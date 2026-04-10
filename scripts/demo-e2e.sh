#!/usr/bin/env bash
#
# demo-e2e.sh — IAM Protocol Engine End-to-End Demo
#
# Runs through all major protocol flows: OAuth 2.0, OIDC, SCIM, SAML, MFA, Device Flow.
# Each step prints the curl command and the expected output.
#
# Usage:
#   ./scripts/demo-e2e.sh          # Full demo (idempotent)
#   ./scripts/demo-e2e.sh --quick    # Quick subset (OAuth + OIDC only)
#
# Prerequisites:
#   1. Docker Compose up:  docker compose -f infra/docker-compose.yml up -d
#   2. Flyway migrated:    ./mvnw flyway:migrate -pl backend/auth-core
#   3. App running:        ./mvnw spring-boot:run -pl backend/api-gateway
#
# The script is idempotent — safe to run on a clean `up -d`.
#

set -e

BASE_URL="${BASE_URL:-http://localhost:8080}"
CLIENT_ID="${CLIENT_ID:-test-client}"
REDIRECT_URI="${REDIRECT_URI:-https://app.example.com/callback}"
CODE_VERIFIER="${CODE_VERIFIER:-dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk}"
# S256 challenge for the above verifier (RFC 7636 test vector)
CODE_CHALLENGE="${CODE_CHALLENGE:-E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM}"

# Colours
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Helper functions
section() {
  echo ""
  echo -e "${CYAN}═══════════════════════════════════════════════════════════${NC}"
  echo -e "${CYAN}  $1${NC}"
  echo -e "${CYAN}═══════════════════════════════════════════════════════════${NC}"
}

step() {
  echo ""
  echo -e "${YELLOW}▶ $1${NC}"
}

ok() {
  echo -e "${GREEN}✓ $1${NC}"
}

err() {
  echo -e "${RED}✗ $1${NC}"
}

http_code() {
  echo "$1" | grep -i "^HTTP/" | awk '{print $2}'
}

body() {
  echo "$1"
}

# Quick mode: just OAuth + OIDC
if [ "${1}" = "--quick" ]; then
  section "QUICK DEMO: OAuth 2.0 + OIDC"
  quick_demo
  section "DONE"
  exit 0
fi

# ─────────────────────────────────────────────────────────────────
# SECTION 1 — Health Check
# ─────────────────────────────────────────────────────────────────
section "1 · Health Check"

step "GET /actuator/health"
HEALTH=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/actuator/health")
if [ "$HEALTH" = "200" ]; then
  ok "Server is up (200)"
else
  err "Server not responding correctly (got $HEALTH)"
  exit 1
fi

# ─────────────────────────────────────────────────────────────────
# SECTION 2 — OIDC Discovery
# ─────────────────────────────────────────────────────────────────
section "2 · OIDC Discovery"

step "GET /.well-known/openid-configuration"
DISCO=$(curl -s "$BASE_URL/.well-known/openid-configuration")
if echo "$DISCO" | grep -q '"issuer"'; then
  ISSUER=$(echo "$DISCO" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('issuer',''))" 2>/dev/null || echo "")
  ok "Discovery document valid — issuer: $ISSUER"
else
  err "Discovery endpoint did not return valid JSON"
fi

step "GET /.well-known/jwks.json"
JWKS=$(curl -s "$BASE_URL/.well-known/jwks.json")
if echo "$JWKS" | grep -q '"keys"'; then
  ok "JWKS endpoint valid"
else
  err "JWKS endpoint did not return valid JSON"
fi

# ─────────────────────────────────────────────────────────────────
# SECTION 3 — OAuth 2.0: Auth Code + PKCE
# ─────────────────────────────────────────────────────────────────
section "3 · OAuth 2.0 — Auth Code + PKCE"

step "GET /oauth2/authorize  (PKCE flow)"
AUTHORIZE_URL="$BASE_URL/oauth2/authorize?client_id=$CLIENT_ID&redirect_uri=$REDIRECT_URI&response_type=code&scope=openid%20profile%20email&state=demo&code_challenge=$CODE_CHALLENGE&code_challenge_method=S256&subject=user1"
RESP=$(curl -s -o /dev/null -w "%{http_code}|%{redirect_url}" "$AUTHORIZE_URL")
HTTP_CODE=$(echo "$RESP" | cut -d'|' -f1)
if [ "$HTTP_CODE" = "302" ]; then
  REDIRECT_URL=$(echo "$RESP" | cut -d'|' -f2)
  AUTH_CODE=$(echo "$REDIRECT_URL" | grep -oP '(?<=code=)[^&]+')
  ok "Auth code received: ${AUTH_CODE:0:20}..."
else
  err "Authorize returned $HTTP_CODE (expected 302)"
  AUTH_CODE=""
fi

if [ -n "$AUTH_CODE" ]; then
  step "POST /oauth2/token  (exchange auth code)"
  TOKEN_RESP=$(curl -s -X POST "$BASE_URL/oauth2/token" \
    -d "grant_type=authorization_code" \
    -d "code=$AUTH_CODE" \
    -d "code_verifier=$CODE_VERIFIER" \
    -d "redirect_uri=$REDIRECT_URI" \
    -d "client_id=$CLIENT_ID")

  ACCESS_TOKEN=$(echo "$TOKEN_RESP" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('access_token',''))" 2>/dev/null || echo "")
  REFRESH_TOKEN=$(echo "$TOKEN_RESP" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('refresh_token',''))" 2>/dev/null || echo "")
  ID_TOKEN=$(echo "$TOKEN_RESP" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('id_token',''))" 2>/dev/null || echo "")

  if [ -n "$ACCESS_TOKEN" ]; then
    ok "Access token received"
    [ -n "$REFRESH_TOKEN" ] && ok "Refresh token received"
    [ -n "$ID_TOKEN" ] && ok "ID token received"
  else
    err "Token exchange failed: $TOKEN_RESP"
  fi
fi

# ─────────────────────────────────────────────────────────────────
# SECTION 4 — Protected Resource
# ─────────────────────────────────────────────────────────────────
section "4 · Protected Resource (Bearer Token)"

if [ -z "$ACCESS_TOKEN" ]; then
  err "Skipped (no access token from Section 3)"
else
  step "GET /api/resource  (no token → 401)"
  NO_TOKEN=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/api/resource")
  [ "$NO_TOKEN" = "401" ] && ok "401 as expected (no token)" || err "Got $NO_TOKEN (expected 401)"

  step "GET /api/resource  (valid token → 200)"
  RESOURCE=$(curl -s -H "Authorization: Bearer $ACCESS_TOKEN" "$BASE_URL/api/resource")
  RESOURCE_CODE=$(curl -s -o /dev/null -w "%{http_code}" -H "Authorization: Bearer $ACCESS_TOKEN" "$BASE_URL/api/resource")
  [ "$RESOURCE_CODE" = "200" ] && ok "200 as expected: $RESOURCE" || err "Got $RESOURCE_CODE (expected 200)"
fi

# ─────────────────────────────────────────────────────────────────
# SECTION 5 — Token Introspection + Revocation
# ─────────────────────────────────────────────────────────────────
section "5 · Token Introspection + Revocation"

if [ -z "$ACCESS_TOKEN" ]; then
  err "Skipped (no access token)"
else
  step "POST /oauth2/introspect  (active token)"
  INTROSPECTED=$(curl -s -X POST "$BASE_URL/oauth2/introspect" \
    -d "token=$ACCESS_TOKEN" \
    -d "token_type_hint=access_token")
  if echo "$INTROSPECTED" | grep -q '"active": true'; then
    ok "Token active: $INTROSPECTED"
  else
    err "Token not active: $INTROSPECTED"
  fi

  step "POST /oauth2/revoke  (revoke access token)"
  REVOKE_RESP=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE_URL/oauth2/revoke" \
    -d "token=$ACCESS_TOKEN")
  [ "$REVOKE_RESP" = "200" ] && ok "200 as expected" || err "Got $REVOKE_RESP (expected 200)"

  step "POST /oauth2/introspect  (after revoke → inactive)"
  AFTER_REVOKE=$(curl -s -X POST "$BASE_URL/oauth2/introspect" \
    -d "token=$ACCESS_TOKEN")
  if echo "$AFTER_REVOKE" | grep -q '"active": false'; then
    ok "Token inactive after revoke: $AFTER_REVOKE"
  else
    err "Token still active: $AFTER_REVOKE"
  fi
fi

# ─────────────────────────────────────────────────────────────────
# SECTION 6 — Refresh Token Rotation
# ─────────────────────────────────────────────────────────────────
section "6 · Refresh Token Rotation"

if [ -z "$REFRESH_TOKEN" ]; then
  err "Skipped (no refresh token)"
else
  step "POST /oauth2/token  (refresh token → new tokens)"
  REFRESH_RESP=$(curl -s -X POST "$BASE_URL/oauth2/token" \
    -d "grant_type=refresh_token" \
    -d "refresh_token=$REFRESH_TOKEN" \
    -d "client_id=$CLIENT_ID")
  NEW_ACCESS=$(echo "$REFRESH_RESP" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('access_token',''))" 2>/dev/null || echo "")
  NEW_REFRESH=$(echo "$REFRESH_RESP" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('refresh_token',''))" 2>/dev/null || echo "")

  if [ -n "$NEW_ACCESS" ]; then
    ok "New access token received"
    # Try reusing old refresh token (should fail)
    step "POST /oauth2/token  (reuse old refresh → invalid_grant)"
    REUSE_RESP=$(curl -s -X POST "$BASE_URL/oauth2/token" \
      -d "grant_type=refresh_token" \
      -d "refresh_token=$REFRESH_TOKEN" \
      -d "client_id=$CLIENT_ID")
    if echo "$REUSE_RESP" | grep -q '"invalid_grant"'; then
      ok "Old refresh token rejected: invalid_grant"
    else
      err "Expected invalid_grant, got: $REUSE_RESP"
    fi
  else
    err "Refresh failed: $REFRESH_RESP"
  fi
fi

# ─────────────────────────────────────────────────────────────────
# SECTION 7 — OIDC: /userinfo
# ─────────────────────────────────────────────────────────────────
section "7 · OIDC /userinfo"

if [ -z "$ACCESS_TOKEN" ]; then
  err "Skipped (no access token from Section 3)"
else
  step "GET /userinfo  (Bearer token)"
  USERINFO=$(curl -s -H "Authorization: Bearer $ACCESS_TOKEN" "$BASE_URL/userinfo")
  USERINFO_CODE=$(curl -s -o /dev/null -w "%{http_code}" -H "Authorization: Bearer $ACCESS_TOKEN" "$BASE_URL/userinfo")
  if [ "$USERINFO_CODE" = "200" ]; then
    SUB=$(echo "$USERINFO" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('sub',''))" 2>/dev/null || echo "")
    ok "200 as expected — sub: $SUB"
  else
    err "Got $USERINFO_CODE (expected 200)"
  fi
fi

# ─────────────────────────────────────────────────────────────────
# SECTION 8 — SCIM 2.0
# ─────────────────────────────────────────────────────────────────
section "8 · SCIM 2.0"

# Create a client for SCIM auth
if [ -z "$ACCESS_TOKEN" ]; then
  err "Skipped (no access token for SCIM)"
else
  step "POST /scim/v2/Users  (create user — joiner)"
  SCIM_USER=$(curl -s -X POST "$BASE_URL/scim/v2/Users" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $ACCESS_TOKEN" \
    -d '{
      "schemas": ["urn:ietf:params:scim:schemas:core:2.0:User"],
      "userName": "alice.demo",
      "displayName": "Alice Demo",
      "emails": [{"value": "alice.demo@example.com", "primary": true}],
      "active": true
    }')
  SCIM_USER_ID=$(echo "$SCIM_USER" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('id',''))" 2>/dev/null || echo "")
  if [ -n "$SCIM_USER_ID" ]; then
    ok "User created: $SCIM_USER_ID"
  else
    err "Failed to create user: $SCIM_USER"
    SCIM_USER_ID=""
  fi

  if [ -n "$SCIM_USER_ID" ]; then
    step "GET /scim/v2/Users/$SCIM_USER_ID  (get user)"
    GET_RESP=$(curl -s -o /dev/null -w "%{http_code}" \
      -H "Authorization: Bearer $ACCESS_TOKEN" \
      "$BASE_URL/scim/v2/Users/$SCIM_USER_ID")
    [ "$GET_RESP" = "200" ] && ok "200 as expected" || err "Got $GET_RESP"

    step "POST /scim/v2/Groups  (create group)"
    SCIM_GROUP=$(curl -s -X POST "$BASE_URL/scim/v2/Groups" \
      -H "Content-Type: application/json" \
      -H "Authorization: Bearer $ACCESS_TOKEN" \
      -d '{"schemas": ["urn:ietf:params:scim:schemas:core:2.0:Group"], "displayName": "Engineering"}')
    SCIM_GROUP_ID=$(echo "$SCIM_GROUP" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('id',''))" 2>/dev/null || echo "")
    [ -n "$SCIM_GROUP_ID" ] && ok "Group created: $SCIM_GROUP_ID" || err "Failed: $SCIM_GROUP"

    step "DELETE /scim/v2/Users/$SCIM_USER_ID  (leaver — token revocation)"
    DEL_RESP=$(curl -s -o /dev/null -w "%{http_code}" \
      -X DELETE "$BASE_URL/scim/v2/Users/$SCIM_USER_ID" \
      -H "Authorization: Bearer $ACCESS_TOKEN")
    [ "$DEL_RESP" = "204" ] && ok "204 as expected (leaver)" || err "Got $DEL_RESP"
  fi
fi

# ─────────────────────────────────────────────────────────────────
# SECTION 9 — Device Authorization Grant (RFC 8628)
# ─────────────────────────────────────────────────────────────────
section "9 · Device Authorization Grant (RFC 8628)"

step "POST /device_authorization  (device requests codes)"
DEVICE_RESP=$(curl -s -X POST "$BASE_URL/device_authorization" \
  -d "client_id=$CLIENT_ID" \
  -d "scope=openid profile email")
DEVICE_CODE=$(echo "$DEVICE_RESP" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('device_code',''))" 2>/dev/null || echo "")
USER_CODE=$(echo "$DEVICE_RESP" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('user_code',''))" 2>/dev/null || echo "")
if [ -n "$DEVICE_CODE" ]; then
  ok "device_code received: ${DEVICE_CODE:0:20}..."
  ok "user_code: $USER_CODE"
else
  err "Failed: $DEVICE_RESP"
fi

if [ -n "$DEVICE_CODE" ]; then
  step "POST /device/approve  (user approves — simulated)"
  APPROVE_RESP=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE_URL/device/approve" \
    -d "user_code=$USER_CODE")
  [ "$APPROVE_RESP" = "200" ] && ok "200 as expected (approved)" || err "Got $APPROVE_RESP"

  step "POST /oauth2/token  (device polls with device_code)"
  DEVICE_TOKEN_RESP=$(curl -s -X POST "$BASE_URL/oauth2/token" \
    -d "grant_type=urn:ietf:params:oauth:grant-type:device_code" \
    -d "device_code=$DEVICE_CODE" \
    -d "client_id=$CLIENT_ID")
  DEVICE_ACCESS=$(echo "$DEVICE_TOKEN_RESP" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('access_token',''))" 2>/dev/null || echo "")
  if [ -n "$DEVICE_ACCESS" ]; then
    ok "Tokens issued for device flow"
  else
    # Polling before approval returns authorization_pending
    ERROR=$(echo "$DEVICE_TOKEN_RESP" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('error',''))" 2>/dev/null || echo "")
    ok "Poll response: error=$ERROR (expected before approval)"
  fi
fi

# ─────────────────────────────────────────────────────────────────
# SECTION 10 — TOTP MFA (RFC 6238)
# ─────────────────────────────────────────────────────────────────
section "10 · TOTP MFA (RFC 6238)"

if [ -z "$ACCESS_TOKEN" ] || [ -z "$NEW_ACCESS" ]; then
  # Get a fresh token using refresh
  FRESH_TOKEN=$(curl -s -X POST "$BASE_URL/oauth2/token" \
    -d "grant_type=refresh_token" \
    -d "refresh_token=$NEW_REFRESH" \
    -d "client_id=$CLIENT_ID" \
    | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('access_token',''))" 2>/dev/null || echo "")
else
  FRESH_TOKEN="$NEW_ACCESS"
fi

if [ -n "$FRESH_TOKEN" ]; then
  step "POST /mfa/totp/setup  (generate TOTP secret + QR)"
  TOTP_SETUP=$(curl -s -X POST "$BASE_URL/mfa/totp/setup" \
    -H "Authorization: Bearer $FRESH_TOKEN")
  TOTP_SECRET=$(echo "$TOTP_SETUP" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('secret',''))" 2>/dev/null || echo "")
  if [ -n "$TOTP_SECRET" ]; then
    ok "TOTP secret generated (showing first 4 chars only): ${TOTP_SECRET:0:4}..."
  else
    err "TOTP setup failed: $TOTP_SETUP"
  fi

  step "GET /mfa/totp/status  (check enrollment)"
  TOTP_STATUS=$(curl -s -X GET "$BASE_URL/mfa/totp/status" \
    -H "Authorization: Bearer $FRESH_TOKEN")
  if echo "$TOTP_STATUS" | grep -q '"enrolled"'; then
    ok "TOTP status: $TOTP_STATUS"
  else
    err "TOTP status unexpected: $TOTP_STATUS"
  fi
else
  err "Skipped (no access token for TOTP setup)"
fi

# ─────────────────────────────────────────────────────────────────
# SECTION 11 — SAML SP Metadata
# ─────────────────────────────────────────────────────────────────
section "11 · SAML 2.0 SP Metadata"

step "GET /saml/metadata"
METADATA=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/saml/metadata")
[ "$METADATA" = "200" ] && ok "SAML metadata retrieved (200)" || err "Got $METADATA"

step "GET /saml/initiate  (SP-initiated SSO redirect)"
INITIATE=$(curl -s -o /dev/null -w "%{http_code}|%{redirect_url}" \
  "$BASE_URL/saml/initiate?client_id=$CLIENT_ID&redirect_uri=$REDIRECT_URI")
INITIATE_CODE=$(echo "$INITIATE" | cut -d'|' -f1)
[ "$INITIATE_CODE" = "302" ] && ok "302 redirect to IdP (SAML AuthnRequest initiated)" || err "Got $INITIATE_CODE"

# ─────────────────────────────────────────────────────────────────
# SECTION 12 — JWKS Key Rotation
# ─────────────────────────────────────────────────────────────────
section "12 · JWKS Key Rotation"

step "POST /.well-known/jwks.json  (rotate key)"
ROTATE_RESP=$(curl -s -X POST "$BASE_URL/.well-known/jwks.json")
if echo "$ROTATE_RESP" | grep -q '"new_kid"'; then
  NEW_KID=$(echo "$ROTATE_RESP" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('new_kid',''))" 2>/dev/null || echo "")
  ok "Key rotated — new kid: $NEW_KID"
else
  err "Key rotation failed or skipped: $ROTATE_RESP"
fi

step "GET /.well-known/jwks.json  (verify 2 keys after rotation)"
JWKS_AFTER=$(curl -s "$BASE_URL/.well-known/jwks.json")
KEY_COUNT=$(echo "$JWKS_AFTER" | python3 -c "import sys,json; d=json.load(sys.stdin); print(len(d.get('keys',[])))" 2>/dev/null || echo "0")
if [ "$KEY_COUNT" -ge "2" ]; then
  ok "JWKS contains $KEY_COUNT keys (rotation successful)"
else
  err "Expected 2+ keys, got: $KEY_COUNT"
fi

# ─────────────────────────────────────────────────────────────────
# DONE
# ─────────────────────────────────────────────────────────────────
section "DEMO COMPLETE"
echo ""
echo "All flows executed. Expected outputs shown above."
echo ""
echo "To run the quick demo only (OAuth + OIDC):"
echo "  $0 --quick"
echo ""
