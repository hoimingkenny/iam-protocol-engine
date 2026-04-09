import { useState, useEffect } from 'react';
import {
  Alert, Box, Chip, Paper, Table, TableBody, TableCell,
  TableContainer, TableHead, TableRow, TablePagination,
  Typography,
} from '@mui/material';

const BASE_URL = 'http://localhost:8080';

interface Client {
  clientId: string;
  clientName: string;
  isPublic: boolean;
  redirectUris: string;
  allowedScopes: string;
  grantTypes: string;
  createdAt: string;
}

export function ClientsPage() {
  const [clients, setClients] = useState<Client[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [page, setPage] = useState(0);
  const [total, setTotal] = useState(0);

  const token = sessionStorage.getItem('access_token');

  const fetchClients = async (p = 0) => {
    setLoading(true);
    try {
      const resp = await fetch(`${BASE_URL}/admin/clients?page=${p}&size=20`, {
        headers: { Authorization: `Bearer ${token}` },
      });
      if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
      const data = await resp.json();
      setClients(data.items);
      setTotal(data.total);
      setPage(p);
    } catch (e) {
      setError(String(e));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { fetchClients(); }, []);

  return (
    <Box>
      <Typography variant="h5" sx={{ mb: 3, fontWeight: 700 }}>OAuth Clients</Typography>

      {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}

      <TableContainer component={Paper}>
        <Table size="small">
          <TableHead sx={{ bgcolor: 'background.paper' }}>
            <TableRow>
              <TableCell sx={{ fontWeight: 700 }}>Client ID</TableCell>
              <TableCell sx={{ fontWeight: 700 }}>Name</TableCell>
              <TableCell sx={{ fontWeight: 700 }}>Type</TableCell>
              <TableCell sx={{ fontWeight: 700 }}>Grant Types</TableCell>
              <TableCell sx={{ fontWeight: 700 }}>Scopes</TableCell>
              <TableCell sx={{ fontWeight: 700 }}>Redirect URIs</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {clients.map((c) => (
              <TableRow key={c.clientId} hover>
                <TableCell><code>{c.clientId}</code></TableCell>
                <TableCell>{c.clientName}</TableCell>
                <TableCell>
                  <Chip label={c.isPublic ? 'Public (PKCE)' : 'Confidential'} size="small"
                    color={c.isPublic ? 'warning' : 'success'} />
                </TableCell>
                <TableCell>
                  {c.grantTypes.split(' ').map(g => (
                    <Chip key={g} label={g} size="small" sx={{ mr: 0.5, mb: 0.5 }} />
                  ))}
                </TableCell>
                <TableCell>
                  {c.allowedScopes.split(' ').map(s => (
                    <Chip key={s} label={s} size="small" variant="outlined" sx={{ mr: 0.5, mb: 0.5 }} />
                  ))}
                </TableCell>
                <TableCell sx={{ fontSize: 12, maxWidth: 200 }}>
                  <code style={{ fontSize: 11 }}>{c.redirectUris}</code>
                </TableCell>
              </TableRow>
            ))}
            {!loading && clients.length === 0 && (
              <TableRow>
                <TableCell colSpan={6} align="center">
                  <Typography variant="body2" color="text.secondary" sx={{ py: 2 }}>
                    No clients found
                  </Typography>
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </TableContainer>
      <TablePagination
        component="div"
        count={total}
        page={page}
        onPageChange={(_, p) => fetchClients(p)}
        rowsPerPage={20}
        rowsPerPageOptions={[20]}
      />
    </Box>
  );
}
