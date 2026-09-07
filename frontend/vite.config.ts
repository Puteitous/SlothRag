import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import path from 'node:path';

// 后端 API 地址(本地开发由 Spring Boot 提供,默认 8080)
const API_TARGET = process.env.SLOTHRAG_API_TARGET ?? 'http://localhost:8080';

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  server: {
    port: 5173,
    strictPort: false,
    proxy: {
      // 后端 REST/SSE 接口走 /api 前缀
      '/api': {
        target: API_TARGET,
        changeOrigin: true,
      },
    },
  },
  // 相对 base:生产部署在任意子路径下均可工作
  base: './',
  build: {
    outDir: 'dist',
    emptyOutDir: true,
    sourcemap: false,
  },
});
