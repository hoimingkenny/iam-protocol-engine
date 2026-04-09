---
title: Login Flow
sidebar_position: 2
description: How Phase 5 integrates a login page with the OAuth 2.0 PKCE authorization flow.
---

# Login Flow

## Overview

The Phase 5 login flow bridges the gap between a username/password form and the OAuth 2.0 authorization flow. It introduces a `login_token` (Redis-backed, 10-minute TTL) as a short-lived credential that proves a user has authenticated, without bypassing the OAuth 2.0 PKCE flow.

```
1. User → GET /login              (sees login form)
2. User → POST /login              (username + password)
           ← { login_token, username, expires_in }

3. Admin UI → GET /authorize
              ?login_token=<token>
              &client_id=test-client
              &redirect_uri=http://localhost:5173/callback
              &response_type=code
              &scope=openid%20profile%20email
              &code_challenge=...
              &code_challenge_method=S256
           ← 302 → /callback?code=AUTHCODE

4. Admin UI → POST /token         (exchange auth code)
           ← { access_token, refresh_token, id_token }

5. Admin UI stores tokens in sessionStorage
```

## Why Not Just Issue an Access Token Directly?

Issuing an access token directly from `/login` would:
- Bypass the OAuth 2.0 PKCE flow — no `code_challenge`/`code_verifier`
- Skip the authorization step — no scope consent
- Produce an access token not bound to an auth code (losing the audit trail)

Instead, the login flow uses the `login_token` as a temporary identity proof, then runs the full OAuth 2.0 flow to get properly issued tokens.

## `POST /login`

```
POST /login
Content-Type: application/x-www-form-urlencoded

username=admin&password=admin123
```

**Success response (200):**
```json
{
  "login_token": "550e8400-e29b-41d4-a716-446655440000",
  "username": "admin",
  "expires_in": 600
}
```

**Failure response (401):**
```json
{
  "error": "invalid_credentials",
  "error_description": "invalid username or password"
}
```

## Login Token Storage

The `login_token` is stored in Redis with key `login:<token>` and value = username. TTL = 10 minutes.

This allows the `/authorize` endpoint to look up the authenticated user without a session cookie.

## `/authorize` with login_token

The `login_token` is passed to `/authorize` as the `login_token` query parameter (instead of the Phase 2 `subject` param):

```
GET /oauth2/authorize?
  client_id=test-client&
  redirect_uri=http://localhost:5173/callback&
  response_type=code&
  scope=openid%20profile%20email&
  code_challenge=E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM&
  code_challenge_method=S256&
  login_token=550e8400-e29b-41d4-a716-446655440000
```

The `AuthorizeController` resolves the subject from Redis using the `login_token`. The auth code is then issued with that subject — the rest of the flow is identical to Phase 2.

## Fallback: subject param (Phase 2 compatibility)

For backward compatibility with Phase 2 testing, `/authorize` still accepts `subject=<user>` directly (no login required). In Phase 5+ this is deprecated.

## Future: Consent Page

Phase 5 auto-approves all requested scopes. A future consent page would:
1. Show the requested scopes to the user
2. Allow the user to approve or deny individual scopes
3. Store consent in Redis (short TTL)
4. Return to `/authorize` with consent token
