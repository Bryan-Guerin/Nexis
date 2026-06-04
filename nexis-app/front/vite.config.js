import {defineConfig} from 'vite'
import {svelte} from '@sveltejs/vite-plugin-svelte'

export default defineConfig({
  plugins: [svelte()],

  build: {
    outDir: '../src/main/resources/public',
    emptyOutDir: true,
  },

  server: {
    // En dev : redirige /api vers le backend Micronaut (port 8080)
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
