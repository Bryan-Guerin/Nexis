import {defineConfig} from 'vite'
import {svelte} from '@sveltejs/vite-plugin-svelte'

export default defineConfig({
  plugins: [svelte({
    onwarn(warning, handler) {
      // Les modales utilisent délibérément des <div class="backdrop"> cliquables
      // (pattern backdrop + stopPropagation). Corriger proprement = ajouter
      // role + onkeydown sur chaque composant — prévu en phase accessibilité.
      if (warning.code.startsWith('a11y')) return
      handler(warning)
    }
  })],

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
