import { useState } from 'react';
import {
  Box, Button, Card, CardContent, CircularProgress,
  TextField, Typography, Alert,
} from '@mui/material';

const BASE_URL = 'http://localhost:8080';

export function LoginPage() {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // If called from /authorize redirect, read OAuth params from URL
  const params = new URLSearchParams(window.location.search);
  const redirectUri = params.get('redirect_uri') ?? 'http://localhost:5173/callback';
  const clientId = params.get('client_id') ?? 'test-client';
  const scope = params.get('scope') ?? 'openid profile email';
  const state = params.get('state') ?? 'xyz';
  const codeChallenge = params.get('code_challenge') ?? 'E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM';
  const codeChallengeMethod = params.get('code_challenge_method') ?? 'S256';

  const handleLogin = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setError(null);

    try {
      // Step 1: Authenticate via /login endpoint
      const loginResp = await fetch(`${BASE_URL}/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: new URLSearchParams({ username, password }),
      });

      if (!loginResp.ok) {
        const err = await loginResp.json().catch(() => ({ error_description: 'Login failed' }));
        setError(err.error_description ?? err.error ?? 'Login failed');
        setLoading(false);
        return;
      }

      const loginData = await loginResp.json();
      sessionStorage.setItem('login_token', loginData.login_token);
      sessionStorage.setItem('username', loginData.username);

      // Step 2: Redirect to /authorize with login_token
      const authorizeUrl = new URL(`${BASE_URL}/oauth2/authorize`);
      authorizeUrl.searchParams.set('client_id', clientId);
      authorizeUrl.searchParams.set('redirect_uri', redirectUri);
      authorizeUrl.searchParams.set('response_type', 'code');
      authorizeUrl.searchParams.set('scope', scope);
      authorizeUrl.searchParams.set('state', state);
      authorizeUrl.searchParams.set('code_challenge', codeChallenge);
      authorizeUrl.searchParams.set('code_challenge_method', codeChallengeMethod);
      authorizeUrl.searchParams.set('login_token', loginData.login_token);

      window.location.href = authorizeUrl.toString();
    } catch (err) {
      setError('Network error — is the backend running?');
      setLoading(false);
    }
  };

  return (
    <Box
      sx={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        background: 'radial-gradient(ellipse at top, #1a0a3a 0%, #0f0f0f 60%)',
      }}
    >
      <Card sx={{ width: 400, p: 1 }}>
        <CardContent sx={{ textAlign: 'center' }}>
          <Typography variant="h4" sx={{ mb: 1, color: 'primary.light', fontWeight: 700 }}>
            IAM Admin — Sign In
          </Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 4 }}>
            IAM Protocol Engine — OAuth 2.0 + OIDC
          </Typography>

          {error && (
            <Alert severity="error" sx={{ mb: 3, textAlign: 'left' }}>
              {error}
            </Alert>
          )}

          <Box component="form" onSubmit={handleLogin} sx={{ textAlign: 'left' }}>
            <TextField
              label="Username"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              fullWidth
              required
              sx={{ mb: 2 }}
              autoComplete="username"
              autoFocus
            />
            <TextField
              label="Password"
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              fullWidth
              required
              sx={{ mb: 3 }}
              autoComplete="current-password"
            />

            <Typography variant="caption" color="text.secondary" sx={{ mb: 2, display: 'block' }}>
              Test accounts: <code>admin/admin123</code>, <code>user1/user1pass</code>, <code>alice/alicepass</code>
            </Typography>

            <Button
              type="submit"
              variant="contained"
              size="large"
              fullWidth
              disabled={loading}
              sx={{ py: 1.5 }}
            >
              {loading ? <CircularProgress size={24} color="inherit" /> : 'Sign In'}
            </Button>
          </Box>
        </CardContent>
      </Card>
    </Box>
  );
}
