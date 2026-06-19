import { defineConfig } from "vitest/config";
import { loadEnv } from "vite";
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

export default defineConfig(({ mode }) => {
  // loadEnv reads .env, .env.local, .env.[mode] etc. with empty prefix = all vars.
  const env = loadEnv(mode, process.cwd(), "");
  const ngrokUrl = env.NGROK_URL ?? "";

  // When NGROK_URL is set, tell Spring Boot (via X-Forwarded-* headers) the
  // real public host so it builds the OAuth redirect_uri for ngrok, not localhost.
  const oauthHeaders = ngrokUrl
    ? { "X-Forwarded-Host": new URL(ngrokUrl).host, "X-Forwarded-Proto": "https" }
    : undefined;

  return {
    plugins: [
      TanStackRouterVite({ target: "react", autoCodeSplitting: true }),
      react(),
      tailwindcss(),
      tsconfigPaths(),
    ],
    server: {
      port: 5173,
      host: true,
      watch: {
        ignored: ["**/backend/**", "**/tests-e2e/**", "**/.wrangler/**"],
      },
      allowedHosts: ngrokUrl ? [new URL(ngrokUrl).host] : [],
      proxy: {
        "/graphql": { target: "http://localhost:8080", changeOrigin: true },
        "/api": { target: "http://localhost:8080", changeOrigin: true },
        // OAuth routes need the public host injected so Spring builds the right
        // redirect_uri (server.forward-headers-strategy=framework is set in backend).
        "/oauth2": {
          target: "http://localhost:8080",
          changeOrigin: true,
          ...(oauthHeaders && { headers: oauthHeaders }),
        },
        "/login": {
          target: "http://localhost:8080",
          changeOrigin: true,
          ...(oauthHeaders && { headers: oauthHeaders }),
        },
      },
    },
    build: {
      rollupOptions: { output: { manualChunks: vendorChunk } },
    },
    test: {
      environment: "jsdom",
      setupFiles: ["./src/test/setup.ts"],
    },
  };
});
