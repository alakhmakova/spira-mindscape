import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";
import { TanStackRouterVite } from "@tanstack/router-plugin/vite";
import tsconfigPaths from "vite-tsconfig-paths";

function vendorChunk(id: string) {
  if (!id.includes("node_modules")) return undefined;

  if (
    id.includes("/react/") ||
    id.includes("/react-dom/") ||
    id.includes("/react-is/") ||
    id.includes("/scheduler/") ||
    id.includes("/use-sync-external-store/")
  ) {
    return "vendor-react";
  }
  if (id.includes("/@tanstack/")) {
    return "vendor-tanstack";
  }
  if (id.includes("/@radix-ui/")) {
    return "vendor-radix";
  }
  if (id.includes("/@tiptap/") || id.includes("/prosemirror-")) {
    return "vendor-editor";
  }
  if (id.includes("/recharts/") || id.includes("/d3-")) {
    return "vendor-charts";
  }
  if (id.includes("/framer-motion/")) {
    return "vendor-motion";
  }
  if (
    id.includes("/react-hook-form/") ||
    id.includes("/@hookform/") ||
    id.includes("/zod/")
  ) {
    return "vendor-forms";
  }
  if (id.includes("/date-fns/") || id.includes("/react-day-picker/")) {
    return "vendor-date";
  }
  if (
    id.includes("/lucide-react/") ||
    id.includes("/cmdk/") ||
    id.includes("/sonner/") ||
    id.includes("/vaul/") ||
    id.includes("/embla-carousel") ||
    id.includes("/input-otp/")
  ) {
    return "vendor-ui";
  }

  return undefined;
}

export default defineConfig({
  plugins: [
    TanStackRouterVite({ target: "react", autoCodeSplitting: true }),
    react(),
    tailwindcss(),
    tsconfigPaths(),
  ],
  server: {
    port: 5173,
    host: true, // expose on all network interfaces → shows Network URL on startup
    proxy: {
      // All backend routes forwarded to Spring Boot so the browser sees a
      // single origin in dev (no cross-origin cookie issues).
      "/graphql": {
        target: "http://localhost:8080",
        changeOrigin: true,
      },
      "/api": {
        target: "http://localhost:8080",
        changeOrigin: true,
      },
      // OAuth2 Authorization Code flow — browser follows redirect to Google
      // and back to /login/oauth2/code/google on the backend.
      "/oauth2": {
        target: "http://localhost:8080",
        changeOrigin: true,
      },
      "/login": {
        target: "http://localhost:8080",
        changeOrigin: true,
      },
    },
  },
  build: {
    rollupOptions: {
      output: {
        manualChunks: vendorChunk,
      },
    },
  },
});
