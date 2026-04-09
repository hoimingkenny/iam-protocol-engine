const BASE_URL = 'http://localhost:8080';

export interface TokenResponse {
  accessToken: string;
  refreshToken?: string;
  idToken?: string;
  tokenType: string;
  expiresIn: number;
  scope: string;
  error?: string;
  errorDescription?: string;
}

export interface IntrospectResponse {
  active: boolean;
  sub?: string;
  scope?: string;
  client_id?: string;
  exp?: number;
  iat?: number;
}

const CODE_VERIFIER = 'dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk';
const CODE_CHALLENGE = 'E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM';

function pkceChallenge(): { verifier: string; challenge: string } {
  return { verifier: CODE_VERIFIER, challenge: CODE_CHALLENGE };
}

function storeTokens(resp: TokenResponse) {
  sessionStorage.setItem('access_token', resp.accessToken);
  if (resp.refreshToken) sessionStorage.setItem('refresh_token', resp.refreshToken);
  if (resp.idToken) sessionStorage.setItem('id_token', resp.idToken);
  sessionStorage.setItem('token_expiry', String(Date.now() + resp.expiresIn * 1000));
}

function clearTokens() {
  sessionStorage.removeItem('access_token');
  sessionStorage.removeItem('refresh_token');
  sessionStorage.removeItem('id_token');
  sessionStorage.removeItem('token_expiry');
}

function getAccessToken(): string | null {
  const expiry = Number(sessionStorage.getItem('token_expiry') ?? 0);
  if (Date.now() > expiry - 30_000) return null; // refresh 30s before actual expiry
  return sessionStorage.getItem('access_token');
}

// ---- Public API ----

export const auth = {
  /**
   * Step 1 of PKCE flow: redirect to /authorize
   */
  login(clientId: string, redirectUri: string, state = 'xyz') {
    const { verifier, challenge } = pkceChallenge();
    sessionStorage.setItem('pkce_verifier', verifier);
    const url = new URL(`${BASE_URL}/oauth2/authorize`);
    url.searchParams.set('client_id', clientId);
    url.searchParams.set('redirect_uri', redirectUri);
    url.searchParams.set('response_type', 'code');
    url.searchParams.set('scope', 'openid profile email');
    url.searchParams.set('state', state);
    url.searchParams.set('code_challenge', challenge);
    url.searchParams.set('code_challenge_method', 'S256');
    window.location.href = url.toString();
  },

  /**
   * Step 2 of PKCE flow: exchange auth code for tokens (called from callback page)
   */
  async exchangeCode(code: string, redirectUri: string, clientId: string): Promise<TokenResponse> {
    const verifier = sessionStorage.getItem('pkce_verifier') ?? CODE_VERIFIER;
    const resp = await fetch(`${BASE_URL}/oauth2/token`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams({
        grant_type: 'authorization_code',
        code,
        code_verifier: verifier,
        redirect_uri: redirectUri,
        client_id: clientId,
      }),
    });
    const data: TokenResponse = await resp.json();
    if (data.accessToken) storeTokens(data);
    return data;
  },

  /**
   * Refresh access token using refresh token
   */
  async refreshToken(clientId: string): Promise<TokenResponse> {
    const refreshToken = sessionStorage.getItem('refresh_token');
    if (!refreshToken) return { error: 'no_refresh_token', accessToken: '', tokenType: 'Bearer', expiresIn: 0, scope: '' };

    const resp = await fetch(`${BASE_URL}/oauth2/token`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams({
        grant_type: 'refresh_token',
        refresh_token: refreshToken,
        client_id: clientId,
      }),
    });
    const data: TokenResponse = await resp.json();
    if (data.accessToken) storeTokens(data);
    return data;
  },

  /**
   * Introspect a token to check if it's still active
   */
  async introspectToken(token: string): Promise<IntrospectResponse> {
    const resp = await fetch(`${BASE_URL}/oauth2/introspect`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams({ token }),
    });
    return resp.json();
  },

  /**
   * Revoke a token
   */
  async revokeToken(token: string): Promise<void> {
    await fetch(`${BASE_URL}/oauth2/revoke`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams({ token }),
    });
  },

  /**
   * Get the current access token (or null if expired)
   */
  getToken(): string | null {
    return getAccessToken();
  },

  /**
   * Check if user is authenticated
   */
  isAuthenticated(): boolean {
    return getAccessToken() !== null;
  },

  /**
   * Logout: clear all tokens from session storage
   */
  logout() {
    clearTokens();
  },
};
