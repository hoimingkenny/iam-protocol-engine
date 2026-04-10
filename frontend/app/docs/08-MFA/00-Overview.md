---
title: Phase 8 Overview
sidebar_position: 1
description: What Phase 8 built — TOTP MFA (RFC 6238), WebAuthn/FIDO2 (W3C Web Authentication), and Device Authorization Grant (RFC 8628).
---

# Phase 8 — Modern Auth (MFA + Device Flow)

**Status:** Complete
**Branch:** `phase-8-develop` merged to `main`
**Lines changed:** ~1,200 lines across 18 files

Phase 8 added three modern authentication mechanisms: TOTP time-based one-time passwords, WebAuthn/FIDO2 passwordless authentication, and the OAuth 2.0 Device Authorization Grant for browser-less and input-constrained devices.

---

## What Was Built

| Task | What | Files | Key Libraries |
|------|------|-------|---------------|
| Task 24 | TOTP MFA — RFC 6238 | `TotpService.java`, `TotpController.java`, `V3__add_totp_credential.sql` | `dev.samstevens.totp:totp:1.7.1` |
| Task 25 | WebAuthn / FIDO2 | `WebAuthnService.java`, `WebAuthnController.java`, `V4__add_webauthn_credential.sql` | `com.webauthn4j:webauthn4j-core:0.31.2.RELEASE` |
| Task 26 | Device Authorization Grant — RFC 8628 | `DeviceFlowService.java`, `DeviceAuthorizationController.java`, `V5__add_device_code.sql` | Spring Web (built by hand) |

---

## Architecture Decisions

| Decision | Rationale |
|----------|-----------|
| AES-256-GCM for TOTP secrets at rest | TOTP secrets are high-value; encryption with a 96-bit random IV per RFC 5083 |
| Demo encryption key in code, prod would use KMS | In production: AWS KMS or HashiCorp Vault for key management |
| `dev.samstevens.totp` library for TOTP | RFC 6238 compliant; handles Base32 encoding, SHA1/SHA256/SHA512 algorithms, time drift |
| WebAuthn challenge generated in-memory with SecureRandom | Challenges must be fresh per ceremony; stored in memory, never persisted |
| `sign_count` tracked for WebAuthn credential | Anti-cloning protection — increments on each authentication |
| Device code TTL = 10 minutes | RFC 8628 §4.5 recommends 10 minutes |
| Device code + user code as separate fields | `device_code` for device polling; `user_code` for human-facing approval URL |
| `device_code` grant handled in `TokenController` | Keeps token endpoint logic in OAuth module; cleaner than a separate controller |

---

## Flyway Migrations

| Migration | What |
|-----------|------|
| `V3__add_totp_credential.sql` | `totp_credential` table: `user_id`, `secret_encrypted` (BYTEA), `verified` |
| `V4__add_webauthn_credential.sql` | `webauthn_credential` table: `credential_id`, `user_id`, `public_key_cose` (BYTEA), `sign_count`, `aaguid` |
| `V5__add_device_code.sql` | `device_code` table: `device_code`, `user_code`, `client_id`, `scope`, `status`, `expires_at` |

---

## Endpoints Added

### MFA Module (`backend/mfa`)

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/mfa/totp/setup` | Generate TOTP secret + QR code (Bearer token auth) |
| `POST` | `/mfa/totp/verify` | Verify 6-digit TOTP code (Bearer token auth) |
| `GET` | `/mfa/totp/status` | Check if user has verified TOTP enrolled |
| `POST` | `/webauthn/register/begin` | Begin WebAuthn registration ceremony |
| `POST` | `/webauthn/register/complete` | Complete WebAuthn registration |
| `POST` | `/webauthn/authenticate/begin` | Begin WebAuthn authentication |
| `POST` | `/webauthn/authenticate/complete` | Complete WebAuthn authentication |

### Device Flow Module (`backend/device-flow`)

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/device_authorization` | Device requests authorization (RFC 8628 §3.1) |
| `GET` | `/device` | User approval page (HTML) |
| `POST` | `/device/approve` | User approves device |
| `POST` | `/oauth2/token` with `grant_type=urn:ietf:params:oauth:grant-type:device_code` | Device polls for token (in `TokenController`) |

---

## Related Phases

- **Phase 2** — OAuth 2.0 core: the `/token` endpoint that handles the device code grant
- **Phase 9** — Demo Hardening: end-to-end scripts tying all auth mechanisms together
