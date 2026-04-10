package com.iam.deviceflow.controller;

import com.iam.deviceflow.service.DeviceFlowService;
import com.iam.deviceflow.service.DeviceFlowService.DeviceAuthorizationResult;
import com.iam.deviceflow.service.DeviceFlowService.PollingResult;
import com.iam.deviceflow.service.DeviceFlowService.PollingStatus;
import com.iam.oauth.dto.TokenResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * RFC 8628 Device Authorization Grant endpoints.
 *
 * POST /device_authorization  — Device requests authorization
 * GET  /device                — User approval page (HTML)
 * POST /device/approve         — User approves device
 * POST /oauth2/token          — Device polls (handled by token controller, but this
 *                               controller provides polling result for device flow)
 *
 * Note: The actual POST /oauth2/token with device_code is handled in
 * TokenController (oauth-oidc module). This controller provides the
 * device authorization initiation and user-facing approval endpoints.
 */
@RestController
public class DeviceAuthorizationController {

    private final DeviceFlowService deviceFlowService;

    public DeviceAuthorizationController(DeviceFlowService deviceFlowService) {
        this.deviceFlowService = deviceFlowService;
    }

    /**
     * Step 1: Device requests authorization.
     * POST /device_authorization
     *
     * Body (urlencoded):
     *   client_id=...&scope=openid profile email
     */
    @PostMapping(value = "/device_authorization",
                 consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
                 produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> deviceAuthorization(
            @RequestParam("client_id") String clientId,
            @RequestParam(value = "scope", required = false) String scope
    ) {
        DeviceAuthorizationResult result = deviceFlowService.authorize(clientId, scope);

        if (!result.success()) {
            return ResponseEntity.status(400)
                .body(TokenResponse.error(result.error(), "client not found"));
        }

        return ResponseEntity.ok(Map.of(
            "device_code", result.deviceCode(),
            "user_code", result.userCode(),
            "verification_uri", result.verificationUri(),
            "verification_uri_complete", result.verificationUri() + "&user_code=" + result.userCode(),
            "expires_in", result.expiresIn(),
            "interval", 5
        ));
    }

    /**
     * Step 2: User visits this URL to approve the device.
     * GET /device?user_code=ABCD1234
     *
     * Returns HTML page with approve button and user code display.
     * In a real app this would require authentication. For demo it is open.
     */
    @GetMapping(value = "/device",
                produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<?> deviceApprovalPage(
            @RequestParam(value = "user_code", required = false) String userCodeParam,
            @RequestParam(value = "error", required = false) String error
    ) {
        String html;

        if (error != null) {
            html = buildErrorHtml("Authorization error: " + error);
        } else if (userCodeParam == null || userCodeParam.isBlank()) {
            html = buildWelcomeHtml();
        } else {
            String decoded = URLDecoder.decode(userCodeParam, StandardCharsets.UTF_8);
            var info = deviceFlowService.getApprovalInfo(decoded);

            if (info.isEmpty()) {
                html = buildErrorHtml("Invalid or expired user code.");
            } else if (!info.get().valid()) {
                html = buildErrorHtml("This user code has expired. Please start a new flow.");
            } else if ("approved".equals(info.get().status())) {
                html = buildApprovedHtml();
            } else {
                html = buildApprovalHtml(decoded, info.get().clientId(), info.get().scope());
            }
        }

        return ResponseEntity.ok(html);
    }

    /**
     * Step 3: User submits approval.
     * POST /device/approve
     *
     * Body (urlencoded): user_code=ABCD1234
     */
    @PostMapping(value = "/device/approve",
                consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
                produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> approve(
            @RequestParam("user_code") String userCode
    ) {
        boolean success = deviceFlowService.approve(userCode);

        if (success) {
            return ResponseEntity.ok(Map.of(
                "approved", true,
                "message", "Device approved. The device can now fetch tokens."
            ));
        } else {
            return ResponseEntity.badRequest()
                .body(Map.of("approved", false, "error", "invalid or expired user code"));
        }
    }

    // --- HTML page builders ---

    private String buildWelcomeHtml() {
        return """
            <!DOCTYPE html>
            <html>
            <head><title>Device Authorization</title></head>
            <body>
            <h1>Device Authorization</h1>
            <p>Enter your user code below to approve device access.</p>
            </body>
            </html>
            """;
    }

    private String buildApprovalHtml(String userCode, String clientId, String scope) {
        return """
            <!DOCTYPE html>
            <html>
            <head><title>Device Authorization</title>
            <style>
                body { font-family: Arial, sans-serif; max-width: 500px; margin: 50px auto; }
                .code { font-size: 2em; font-weight: bold; letter-spacing: 0.1em;
                         background: #f0f0f0; padding: 10px 20px; display: inline-block; }
                .approve-btn { background: #0066cc; color: white; padding: 12px 24px;
                               border: none; cursor: pointer; font-size: 1em; margin-top: 20px; }
                .approve-btn:hover { background: #0055aa; }
            </style>
            </head>
            <body>
            <h1>Approve Device Access</h1>
            <p>Device is requesting access.</p>
            <p><strong>User Code:</strong></p>
            <div class="code">%s</div>
            <p style="margin-top:10px"><strong>Client:</strong> %s</p>
            <p><strong>Scopes:</strong> %s</p>
            <form method="POST" action="/device/approve">
                <input type="hidden" name="user_code" value="%s">
                <button type="submit" class="approve-btn">Approve Access</button>
            </form>
            </body>
            </html>
            """.formatted(userCode, clientId, scope != null ? scope : "(none)", userCode);
    }

    private String buildApprovedHtml() {
        return """
            <!DOCTYPE html>
            <html>
            <head><title>Device Approved</title></head>
            <body>
            <h1>Device Approved</h1>
            <p>This device has been approved. It can now fetch tokens.</p>
            </body>
            </html>
            """;
    }

    private String buildErrorHtml(String message) {
        return """
            <!DOCTYPE html>
            <html>
            <head><title>Error</title></head>
            <body>
            <h1>Error</h1>
            <p>%s</p>
            </body>
            </html>
            """.formatted(message);
    }
}
