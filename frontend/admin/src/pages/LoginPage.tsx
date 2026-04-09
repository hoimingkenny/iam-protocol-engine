import { useState } from 'react';
import {
  Box, Button, Card, CardContent, CircularProgress,
  Typography, Alert,
} from '@mui/material';
import { auth } from '../lib/auth';

const CLIENT_ID = 'test-client';
const REDIRECT_URI = 'http://localhost:5173/callback';

export function LoginPage() {
  const [loading, setLoading] = useState(false);

  const handleLogin = () => {
    setLoading(true);
    auth.login(CLIENT_ID, REDIRECT_URI);
    // The page will redirect, but show loading in case user hits back
    setTimeout(() => setLoading(false), 5000);
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
            IAM Admin
          </Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 4 }}>
            IAM Protocol Engine — Admin Console
          </Typography>

          <Alert severity="info" sx={{ mb: 3, textAlign: 'left' }}>
            You will be redirected to the OAuth 2.0 authorization server to authenticate.
          </Alert>

          <Button
            variant="contained"
            size="large"
            fullWidth
            onClick={handleLogin}
            disabled={loading}
            sx={{ py: 1.5 }}
          >
            {loading ? <CircularProgress size={24} color="inherit" /> : 'Sign In with OAuth 2.0'}
          </Button>
        </CardContent>
      </Card>
    </Box>
  );
}
