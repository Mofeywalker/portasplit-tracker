import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';

// The build output is written straight into the Spring Boot classpath so it gets
// packaged into the jar and served from `classpath:/static/`.
export default defineConfig({
  plugins: [react(), tailwindcss()],
  build: {
    outDir: '../target/classes/static',
    emptyOutDir: true,
  },
  server: {
    // `npm run dev` (port 5173) proxies API calls to a locally running backend.
    proxy: {
      '/api': 'http://localhost:8080',
      '/actuator': 'http://localhost:8080',
    },
  },
});
