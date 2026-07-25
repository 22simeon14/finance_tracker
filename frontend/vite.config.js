import { defineConfig } from 'vite';

export default defineConfig({
  server: {
    port: 5173,
    proxy: {
      '/health': 'http://localhost:8080',
      '/auth': 'http://localhost:8080',
    },
  },
});
