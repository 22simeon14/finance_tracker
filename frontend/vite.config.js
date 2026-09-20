/**
 * Main Responsibility: Vite frontend config — dev server and API proxy.
 *
 * Dev server listens on 5173. Paths /health, /auth, /categories, /documents,
 * /expenses, and /dashboard are proxied to the Spring backend on localhost:8080
 * so the browser can call same-origin URLs (no CORS issues during local development).
 *
 * /documents uses a 120s proxy timeout so sync OCR + Groq can finish (mock was ms-fast).
 *
 * Tailwind CSS is compiled through PostCSS (postcss.config.js + tailwind.config.js).
 *
 * Note: package.json cannot hold comments; this file documents the frontend setup.
 */
import { defineConfig } from 'vite';

/** Backend origin for the Vite dev proxy. */
const API = 'http://localhost:8080';

/** Allow sync document processing (OCR + LLM) without the proxy closing early. */
const DOCUMENTS_PROXY_TIMEOUT_MS = 120_000;

export default defineConfig({
  server: {
    port: 5173,
    proxy: {
      '/health': API,
      '/auth': API,
      '/categories': API,
      // Longer than default: upload + process stays open until REVIEW_REQUIRED / FAILED.
      '/documents': {
        target: API,
        timeout: DOCUMENTS_PROXY_TIMEOUT_MS,
        proxyTimeout: DOCUMENTS_PROXY_TIMEOUT_MS,
      },
      '/expenses': API,
      '/dashboard': API,
    },
  },
});
