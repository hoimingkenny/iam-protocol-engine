import type { ReactNode } from 'react';
import {
  AppBar, Box, Drawer, List, ListItemButton, ListItemIcon, ListItemText,
  Toolbar, Typography, Divider, Avatar, Menu, MenuItem, IconButton,
} from '@mui/material';
import {
  Menu as MenuIcon, Person, Group, Security, History, Logout,
} from '@mui/icons-material';
import { useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { auth } from '../lib/auth';

const DRAWER_WIDTH = 240;

const navItems = [
  { label: 'Users', icon: <Person />, path: '/users' },
  { label: 'Clients', icon: <Security />, path: '/clients' },
  { label: 'Groups', icon: <Group />, path: '/groups' },
  { label: 'Audit Log', icon: <History />, path: '/audit' },
];

interface LayoutProps {
  children: ReactNode;
}

export function Layout({ children }: LayoutProps) {
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [menuAnchor, setMenuAnchor] = useState<null | HTMLElement>(null);
  const location = useLocation();

  const handleLogout = () => {
    auth.logout();
    window.location.href = '/login';
  };

  return (
    <Box sx={{ display: 'flex', minHeight: '100vh' }}>
      <AppBar position="fixed" sx={{ zIndex: (t) => t.zIndex.drawer + 1 }}>
        <Toolbar>
          <IconButton
            color="inherit"
            edge="start"
            onClick={() => setDrawerOpen(!drawerOpen)}
            sx={{ mr: 2 }}
          >
            <MenuIcon />
          </IconButton>
          <Typography variant="h6" sx={{ flexGrow: 1, fontWeight: 700 }}>
            IAM Admin
          </Typography>
          <IconButton color="inherit" onClick={(e) => setMenuAnchor(e.currentTarget)}>
            <Avatar sx={{ width: 32, height: 32, bgcolor: 'primary.light', fontSize: 14 }}>
              {auth.getToken() ? 'A1' : '?'}
            </Avatar>
          </IconButton>
        </Toolbar>
      </AppBar>

      <Drawer
        variant="persistent"
        open={drawerOpen}
        sx={{
          width: drawerOpen ? DRAWER_WIDTH : 0,
          flexShrink: 0,
          '& .MuiDrawer-paper': { width: DRAWER_WIDTH, boxSizing: 'border-box' },
        }}
      >
        <Toolbar />
        <Box sx={{ overflow: 'auto', pt: 1 }}>
          <List>
            {navItems.map((item) => (
              <ListItemButton
                key={item.path}
                component={Link}
                to={item.path}
                selected={location.pathname === item.path}
                onClick={() => setDrawerOpen(false)}
                sx={{
                  mx: 1,
                  borderRadius: 1,
                  '&.Mui-selected': { bgcolor: 'primary.dark' },
                }}
              >
                <ListItemIcon sx={{ minWidth: 40 }}>{item.icon}</ListItemIcon>
                <ListItemText primary={item.label} />
              </ListItemButton>
            ))}
          </List>
          <Divider sx={{ my: 1 }} />
          <List>
            <ListItemButton onClick={handleLogout} sx={{ mx: 1, borderRadius: 1 }}>
              <ListItemIcon sx={{ minWidth: 40 }}><Logout /></ListItemIcon>
              <ListItemText primary="Logout" />
            </ListItemButton>
          </List>
        </Box>
      </Drawer>

      <Menu
        anchorEl={menuAnchor}
        open={Boolean(menuAnchor)}
        onClose={() => setMenuAnchor(null)}
      >
        <MenuItem onClick={handleLogout}><Logout sx={{ mr: 1 }} fontSize="small" /> Logout</MenuItem>
      </Menu>

      <Box
        component="main"
        sx={{
          flexGrow: 1,
          p: 3,
          mt: '64px',
          transition: 'margin 0.3s',
          ml: drawerOpen ? `${DRAWER_WIDTH}px` : 0,
        }}
      >
        {children}
      </Box>
    </Box>
  );
}
