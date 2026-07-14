import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  define: {
    // sockjs-client (used for WebSocket fallback) expects Node's `global` object,
    // which doesn't exist in the browser — map it to globalThis instead.
    global: 'globalThis',
  },
})
