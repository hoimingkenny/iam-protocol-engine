package com.iam.deviceflow.service;

import com.iam.authcore.entity.DeviceCode;
import com.iam.authcore.entity.OAuthClient;
import com.iam.authcore.repository.DeviceCodeRepository;
import com.iam.authcore.repository.OAuthClientRepository;
import com.iam.oauth.service.TokenService;
import com.iam.oauth.dto.TokenResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

/**
 * RFC 8628 Device Authorization Grant implementation.
 *
 * Flow:
 * 1. Device POSTs to /device_authorization → gets device_code + user_code
 * 2. User visits /device?user_code=X → sees approval page
 * 3. User approves → device code marked approved
 * 4. Device polls POST /oauth2/token with device_code → authorization_pending until approved
 * 5. After approval: tokens issued
 */
@Service
public class DeviceFlowService {

    private static final Logger log = LoggerFactory.getLogger(DeviceFlowService.class);

    // RFC 8628 §6.1 — 10 minute default lifetime
    private static final Duration DEVICE_CODE_LIFETIME = Duration.ofMinutes(10);
    private static final Duration POLLING_INTERVAL = Duration.ofSeconds(5);
    private static final int SLOW_CONSUMER_INTERVAL = 30; // seconds between polls

    private final DeviceCodeRepository deviceCodeRepo;
    private final OAuthClientRepository clientRepo;
    private final TokenService tokenService;
    private final SecureRandom secureRandom;

    public DeviceFlowService(DeviceCodeRepository deviceCodeRepo,
                              OAuthClientRepository clientRepo,
                              TokenService tokenService) {
        this.deviceCodeRepo = deviceCodeRepo;
        this.clientRepo = clientRepo;
        this.tokenService = tokenService;
        this.secureRandom = new SecureRandom();
    }

    /**
     * Step 1: Device requests authorization.
     * POST /device_authorization
     *
     * @param clientId  OAuth client ID
     * @param scope     requested scopes (space-separated)
     * @return DeviceAuthorizationResult or error
     */
    @Transactional
    public DeviceAuthorizationResult authorize(String clientId, String scope) {
        // Validate client
        OAuthClient client = clientRepo.findByClientId(clientId).orElse(null);
        if (client == null) {
            return new DeviceAuthorizationResult(false, "invalid_client", null, null, null, null, 0);
        }

        // Generate device_code (high-entropy random string)
        String deviceCode = generateDeviceCode();
        // Generate user_code (8 characters, URL-safe)
        String userCode = generateUserCode();

        Instant expiresAt = Instant.now().plus(DEVICE_CODE_LIFETIME);

        DeviceCode dc = new DeviceCode();
        dc.setDeviceCode(deviceCode);
        dc.setUserCode(userCode);
        dc.setClientId(clientId);
        dc.setScope(scope);
        dc.setStatus(DeviceCode.Status.pending);
        dc.setExpiresAt(expiresAt);
        dc.setPollingCount(0);

        deviceCodeRepo.save(dc);

        log.info("Device authorization started: clientId={}, userCode={}", clientId, userCode);
        return new DeviceAuthorizationResult(
            true, null,
            deviceCode,
            userCode,
            "http://localhost:8080/device?user_code=" + userCode,
            "Bearer",
            (int) DEVICE_CODE_LIFETIME.getSeconds()
        );
    }

    /**
     * Step 2: Show approval page.
     * GET /device?user_code=X
     */
    public Optional<DeviceApprovalInfo> getApprovalInfo(String userCode) {
        return deviceCodeRepo.findByUserCode(userCode)
            .map(dc -> new DeviceApprovalInfo(
                dc.getUserCode(),
                dc.getClientId(),
                dc.getScope(),
                dc.getStatus().name(),
                dc.getExpiresAt().isAfter(Instant.now())
            ));
    }

    /**
     * Step 3: User approves the device.
     * POST /device/approve
     */
    @Transactional
    public boolean approve(String userCode) {
        Optional<DeviceCode> opt = deviceCodeRepo.findByUserCode(userCode);
        if (opt.isEmpty()) {
            return false;
        }

        DeviceCode dc = opt.get();
        if (dc.getStatus() != DeviceCode.Status.pending) {
            return false;
        }
        if (dc.getExpiresAt().isBefore(Instant.now())) {
            dc.setStatus(DeviceCode.Status.expired);
            deviceCodeRepo.save(dc);
            return false;
        }

        dc.setStatus(DeviceCode.Status.approved);
        deviceCodeRepo.save(dc);
        log.info("Device approved: userCode={}", userCode);
        return true;
    }

    /**
     * Step 4: Device polls token endpoint.
     * Returns: tokens (if approved), authorization_pending (if pending), or error
     */
    @Transactional
    public PollingResult poll(String deviceCode, String clientId) {
        Optional<DeviceCode> opt = deviceCodeRepo.findByDeviceCodeAndStatus(
            deviceCode, DeviceCode.Status.pending);

        if (opt.isEmpty()) {
            // Try to find the device code with any status
            Optional<DeviceCode> any = deviceCodeRepo.findById(deviceCode);
            if (any.isEmpty()) {
                return new PollingResult(PollingStatus.SLOW_DOWN, null);
            }
            DeviceCode dc = any.get();

            // Not this client's device code
            if (!dc.getClientId().equals(clientId)) {
                return new PollingResult(PollingStatus.SLOW_DOWN, null);
            }

            if (dc.getStatus() == DeviceCode.Status.approved) {
                return issueTokens(dc);
            } else if (dc.getStatus() == DeviceCode.Status.expired ||
                       dc.getExpiresAt().isBefore(Instant.now())) {
                return new PollingResult(PollingStatus.EXPIRED, null);
            } else if (dc.getStatus() == DeviceCode.Status.denied) {
                return new PollingResult(PollingStatus.ACCESS_DENIED, null);
            }
            return new PollingResult(PollingStatus.SLOW_DOWN, null);
        }

        DeviceCode dc = opt.get();

        // Increment polling count
        int count = dc.getPollingCount() + 1;
        dc.setPollingCount(count);
        deviceCodeRepo.save(dc);

        // RFC 8628 §3.5: slow_consumers if polling too frequently
        // For demo: always return authorization_pending until approved
        return new PollingResult(PollingStatus.AUTHORIZATION_PENDING, null);
    }

    private PollingResult issueTokens(DeviceCode dc) {
        try {
            TokenResponse response = tokenService.issueTokensForSamlUser(
                dc.getUserCode(),  // subject = user_code as identifier
                dc.getClientId(),
                null,  // no nonce in device flow
                dc.getScope()
            );

            // Consume device code after successful token issuance
            deviceCodeRepo.deleteById(dc.getDeviceCode());

            log.info("Device flow tokens issued: clientId={}, userCode={}",
                     dc.getClientId(), dc.getUserCode());
            return new PollingResult(PollingStatus.COMPLETED, response);
        } catch (Exception e) {
            log.error("Device flow token issuance failed: {}", e.getMessage());
            return new PollingResult(PollingStatus.SERVER_ERROR, null);
        }
    }

    // --- Code generators ---

    private String generateDeviceCode() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String generateUserCode() {
        // 8-character Base64URL code (no padding)
        byte[] bytes = new byte[6];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    // --- Result types ---

    public record DeviceAuthorizationResult(
        boolean success,
        String error,
        String deviceCode,
        String userCode,
        String verificationUri,
        String tokenType,
        int expiresIn
    ) {}

    public record DeviceApprovalInfo(
        String userCode,
        String clientId,
        String scope,
        String status,
        boolean valid
    ) {}

    public enum PollingStatus {
        COMPLETED,
        AUTHORIZATION_PENDING,
        SLOW_DOWN,
        EXPIRED,
        ACCESS_DENIED,
        SERVER_ERROR
    }

    public record PollingResult(PollingStatus status, TokenResponse tokenResponse) {}
}
