import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/oauth2': 'http://localhost:8080',
      '/.well-known': 'http://localhost:8080',
      '/userinfo': 'http://localhost:8080',
      '/api': 'http://localhost:8080',
    },
  },
});
