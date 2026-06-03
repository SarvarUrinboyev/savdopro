import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// The production build is emitted straight into the backend's static
// resources, so the Spring Boot JAR serves the UI and the API together.
export default defineConfig(({ command }) => ({
  plugins: [react()],
  // Absolute asset paths ("/assets/...") so nested routes like
  // /warehouse/3 still resolve the bundle correctly.
  base: '/',
  server: {
    port: 3000,
    strictPort: true,
    proxy: {
      '/api': 'http://localhost:8086',
    },
  },
  // Hardening: in production strip console.* / debugger so internal logic,
  // API shapes and debug data never reach end users' browser dev tools.
  // Kept in dev so local debugging still works.
  esbuild: command === 'build' ? { drop: ['console', 'debugger'] } : {},
  build: {
    outDir: '../backend/src/main/resources/static',
    emptyOutDir: true,
    // Never ship source maps — the original (un-minified) source must not be
    // reconstructable from the served bundle. esbuild also mangles names.
    sourcemap: false,
    minify: 'esbuild',
  },
}));
