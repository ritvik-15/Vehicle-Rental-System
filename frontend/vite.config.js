import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// Port 5173 matches the backend CORS allowed origin (http://localhost:5173).
export default defineConfig({
  plugins: [react()],
  server: { port: 5173 },
})
