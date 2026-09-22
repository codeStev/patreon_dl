import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// Proxies /api to the Spring Boot dev server so `npm run dev` never needs
// CORS enabled on the backend - production serves this build's static
// output from the same origin as the API (see deploy/combined/Dockerfile).
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      "/api": "http://localhost:8080",
    },
  },
});
