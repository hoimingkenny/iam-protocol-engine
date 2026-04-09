import { useState, useEffect } from 'react';
import {
  Alert, Box, Chip, Paper, Table, TableBody, TableCell,
  TableContainer, TableHead, TableRow, TablePagination,
  Typography, MenuItem, Select, FormControl, InputLabel,
} from '@mui/material';

const BASE_URL = 'http://localhost:8080';

interface AuditEvent {
  id: string;
  eventType: string;
  actor: string;
  subject: string;
  clientId: string;
  scope: string;
  jti: string;
  ipAddress: string;
  timestamp: string;
  details: Record<string, unknown>;
}

export function AuditPage() {
  const [events, setEvents] = useState<AuditEvent[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [page, setPage] = useState(0);
  const [total, setTotal] = useState(0);
  const [eventTypeFilter, setEventTypeFilter] = useState('');

  const token = sessionStorage.getItem('access_token');

  const fetchEvents = async (p = 0, filter = eventTypeFilter) => {
    setLoading(true);
    try {
      const params = new URLSearchParams({ page: String(p), size: '50' });
      if (filter) params.set('eventType', filter);
      const resp = await fetch(`${BASE_URL}/admin/audit?${params}`, {
        headers: { Authorization: `Bearer ${token}` },
      });
      if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
      const data = await resp.json();
      setEvents(data.items);
      setTotal(data.total);
      setPage(p);
    } catch (e) {
      setError(String(e));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { fetchEvents(); }, []);

  const formatTimestamp = (ts: string) => {
    try {
      return new Date(ts).toLocaleString();
    } catch { return ts; }
  };

  return (
    <Box>
      <Typography variant="h5" sx={{ mb: 3, fontWeight: 700 }}>Audit Log</Typography>

      {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}

      <Box sx={{ mb: 2 }}>
        <FormControl size="small" sx={{ minWidth: 200 }}>
          <InputLabel>Event Type</InputLabel>
          <Select
            value={eventTypeFilter}
            label="Event Type"
            onChange={(e) => {
              setEventTypeFilter(e.target.value);
              fetchEvents(0, e.target.value);
            }}
          >
            <MenuItem value="">All events</MenuItem>
            <MenuItem value="auth_code_issued">auth_code_issued</MenuItem>
            <MenuItem value="token_issued">token_issued</MenuItem>
            <MenuItem value="token_refreshed">token_refreshed</MenuItem>
            <MenuItem value="token_revoked">token_revoked</MenuItem>
          </Select>
        </FormControl>
      </Box>

      <TableContainer component={Paper}>
        <Table size="small">
          <TableHead sx={{ bgcolor: 'background.paper' }}>
            <TableRow>
              <TableCell sx={{ fontWeight: 700 }}>Timestamp</TableCell>
              <TableCell sx={{ fontWeight: 700 }}>Event</TableCell>
              <TableCell sx={{ fontWeight: 700 }}>Actor</TableCell>
              <TableCell sx={{ fontWeight: 700 }}>Subject</TableCell>
              <TableCell sx={{ fontWeight: 700 }}>Client</TableCell>
              <TableCell sx={{ fontWeight: 700 }}>Scope</TableCell>
              <TableCell sx={{ fontWeight: 700 }}>IP Address</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {events.map((e) => (
              <TableRow key={e.id} hover>
                <TableCell sx={{ fontSize: 12, whiteSpace: 'nowrap' }}>
                  {formatTimestamp(e.timestamp)}
                </TableCell>
                <TableCell>
                  <Chip label={e.eventType} size="small" color="primary" variant="outlined" />
                </TableCell>
                <TableCell sx={{ fontSize: 12 }}>{e.actor ?? '—'}</TableCell>
                <TableCell sx={{ fontSize: 12 }}>{e.subject ?? '—'}</TableCell>
                <TableCell sx={{ fontSize: 12 }}><code>{e.clientId ?? '—'}</code></TableCell>
                <TableCell sx={{ fontSize: 12 }}>{e.scope ?? '—'}</TableCell>
                <TableCell sx={{ fontSize: 12 }}>{e.ipAddress ?? '—'}</TableCell>
              </TableRow>
            ))}
            {!loading && events.length === 0 && (
              <TableRow>
                <TableCell colSpan={7} align="center">
                  <Typography variant="body2" color="text.secondary" sx={{ py: 2 }}>
                    No audit events found
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
        onPageChange={(_, p) => fetchEvents(p)}
        rowsPerPage={50}
        rowsPerPageOptions={[50]}
      />
    </Box>
  );
}
