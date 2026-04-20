import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  // Al hacer "npm run build", deposita los archivos en la carpeta webapp del proyecto Java
  build: {
    outDir: '../src/main/webapp',
    emptyOutDir: false, // No borrar web.xml ni META-INF
  },
  // Base relativa para que funcione bajo el contexto /algoritmogris/ de Tomcat
  base: './',
})
