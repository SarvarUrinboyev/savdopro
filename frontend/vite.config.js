import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import obfuscator from 'vite-plugin-javascript-obfuscator';

// The production build is emitted straight into the backend's static
// resources, so the Spring Boot JAR serves the UI and the API together.
export default defineConfig(({ command }) => {
  const isBuild = command === 'build';
  // Obfuscation is OPT-IN (set VITE_OBFUSCATE=1). It is OFF by default because
  // the obfuscator corrupts Vite/Rollup code-splitting: it intermittently drops
  // or merges lazy page chunks, so dynamic imports 404 at runtime — this took
  // prod down on 2026-06-04. Reliability first. The shipped bundle is still
  // protected: esbuild mangles names + drops console, no source maps, private
  // repo. Re-enable obfuscation only with a code-split-safe tool/config.
  const obfuscate = isBuild && process.env.VITE_OBFUSCATE === '1';

  return {
    plugins: [
      react(),
      // Make our own bundled source hard to read/copy. Vendor OSS (react,
      // exceljs, html2canvas, jspdf) is excluded: it's public anyway and
      // obfuscating it only adds runtime cost and breakage risk.
      obfuscate &&
        obfuscator({
          apply: 'build',
          exclude: [/node_modules/],
          options: {
            compact: true,
            simplify: true,
            identifierNamesGenerator: 'mangled',
            // Hide string literals (API paths, labels, internal keys) inside an
            // encoded array so they can't be read by eye in the shipped bundle.
            stringArray: true,
            stringArrayEncoding: ['base64'],
            stringArrayThreshold: 0.75,
            // Heavy transforms that wreck runtime performance or break complex
            // libraries are deliberately OFF — protection without fragility.
            controlFlowFlattening: false,
            deadCodeInjection: false,
            transformObjectKeys: false,
            renameGlobals: false,
            selfDefending: false,
            debugProtection: false,
            numbersToExpressions: false,
            splitStrings: false,
          },
        }),
    ].filter(Boolean),
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
    esbuild: isBuild ? { drop: ['console', 'debugger'] } : {},
    build: {
      outDir: '../backend/src/main/resources/static',
      emptyOutDir: true,
      // Never ship source maps — the original (un-minified) source must not be
      // reconstructable from the served bundle. esbuild also mangles names.
      sourcemap: false,
      minify: 'esbuild',
    },
  };
});
