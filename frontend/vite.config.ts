import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// In dev, the backend sits behind Vite's proxy so the app is same-origin (no CORS).
// In production set VITE_API_URL to the backend's URL (see src/api/http.ts).
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080',
      '/ws': { target: 'ws://localhost:8080', ws: true },
    },
  },
});
