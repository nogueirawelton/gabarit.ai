import tailwindcss from "@tailwindcss/vite";
import react from "@vitejs/plugin-react";
import { defineConfig } from "vite";

export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    port: 5173,
    // o Keycloak redireciona de volta para esta porta (redirectUris do realm)
    strictPort: true,
  },
});
