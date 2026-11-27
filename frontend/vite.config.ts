import { defineConfig } from 'vitest/config';
import { VitePWA } from 'vite-plugin-pwa';

export default defineConfig({
  test: {
    coverage: {
      provider: 'v8',
      reporter: ['text', 'html'],
      include: ['src/**/*.ts'],
      exclude: ['src/**/*.test.ts', 'src/main.ts'],
    },
  },
  server: {
    host: true,
    allowedHosts: ['.trycloudflare.com'],
    proxy: { '/api': 'http://localhost:8080' },
  },
  preview: { allowedHosts: ['.trycloudflare.com'] },
  plugins: [VitePWA({
    registerType: 'autoUpdate',
    includeAssets: ['favicon.svg'],
    manifest: {
      name: 'Hogar',
      short_name: 'Hogar',
      description: 'Acceso y declaración de turno de Hogar.',
      theme_color: '#174b3a',
      background_color: '#f4f7f4',
      display: 'standalone',
      start_url: '/',
      icons: [
        { src: '/icon-192.svg', sizes: '192x192', type: 'image/svg+xml', purpose: 'any' },
        { src: '/icon-512.svg', sizes: '512x512', type: 'image/svg+xml', purpose: 'any' },
      ],
    },
    workbox: {
      globPatterns: ['**/*.{js,css,html,svg,ico,png,woff2}'],
      navigateFallback: '/index.html',
    },
  })],
});
