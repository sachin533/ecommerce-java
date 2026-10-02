import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';

// In Docker the API lives at http://backend:8000; locally at 127.0.0.1:8001.
// Override with API_PROXY=http://backend:8000 (compose sets this).
const apiProxy = process.env.API_PROXY || 'http://127.0.0.1:8001';

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5175,
    // Demo sharing via ngrok (Vite 5 syntax: explicit host list).
    // ngrok free subdomains change per restart — if you get a new URL,
    // add it here and rebuild. Leading-dot entries cover subdomains.
    allowedHosts: [
      'localhost',
      '127.0.0.1',
      'turbulent-uneasy-salute.ngrok-free.dev',
      '.ngrok-free.dev',
      '.ngrok.io',
    ],
    proxy: {
      '/api': apiProxy
    }
  }
});
