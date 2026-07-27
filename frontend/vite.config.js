/**
 * Main Responsibility: Vite frontend config — dev server and API proxy.
 *
 * Dev server listens on 5173. Paths /health, /auth, and /categories are
 * proxied to the Spring backend on localhost:8080 so the browser can call
 * same-origin URLs (no CORS issues during local development).
 *
 * Note: package.json cannot hold comments; this file documents the frontend setup.
 */
import { defineConfig } from 'vite';

export default defineConfig({
  server: {
    port: 5173,
    proxy: {
      '/health': 'http://localhost:8080',
      '/auth': 'http://localhost:8080',
      '/categories': 'http://localhost:8080',
    },
  },
});
