import { Box, Typography } from '@mui/material';

export function GroupsPage() {
  return (
    <Box>
      <Typography variant="h5" sx={{ mb: 1, fontWeight: 700 }}>Groups</Typography>
      <Typography variant="body2" color="text.secondary">
        SCIM Group management — full implementation arrives in Phase 6.
      </Typography>
    </Box>
  );
}
