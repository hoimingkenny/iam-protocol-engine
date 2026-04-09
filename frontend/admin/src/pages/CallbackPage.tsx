import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { Box, CircularProgress, Typography, Alert } from '@mui/material';
import { auth } from '../lib/auth';

const CLIENT_ID = 'test-client';
const REDIRECT_URI = 'http://localhost:5173/callback';

export function CallbackPage() {
  const [searchParams] = useSearchParams();
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const code = searchParams.get('code');
    const errorParam = searchParams.get('error');

    if (errorParam) {
      setError(`${errorParam}: ${searchParams.get('error_description') ?? ''}`);
      return;
    }

    if (!code) {
      setError('No authorization code received');
      return;
    }

    auth.exchangeCode(code, REDIRECT_URI, CLIENT_ID)
      .then((data) => {
        if (data.error) {
          setError(`${data.error}: ${data.errorDescription ?? ''}`);
        } else {
          window.location.href = '/users';
        }
      })
      .catch((e) => setError(e.message));
  }, [searchParams]);

  if (error) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', mt: 8 }}>
        <Alert severity="error" sx={{ maxWidth: 500 }}>{error}</Alert>
      </Box>
    );
  }

  return (
    <Box sx={{ display: 'flex', justifyContent: 'center', mt: 8 }}>
      <CircularProgress />
      <Typography sx={{ ml: 2, mt: 1 }}>Completing sign-in…</Typography>
    </Box>
  );
}
