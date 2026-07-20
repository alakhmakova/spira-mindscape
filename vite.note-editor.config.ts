import { defineConfig } from "vite";
import { viteSingleFile } from "vite-plugin-singlefile";
import { resolve } from "path";

// Builds the TipTap note editor (embeds/note-editor) into ONE self-contained HTML file
// (inline JS + CSS, no network requests — CSP/offline safe) that the Android app loads in a
// WebView from assets. Run: `npm run build:note-editor`.
export default defineConfig({
  root: resolve(__dirname, "embeds/note-editor"),
  plugins: [viteSingleFile()],
  build: {
    outDir: resolve(__dirname, "android/app/src/main/assets/note-editor"),
    emptyOutDir: true,
    target: "es2019",
    cssCodeSplit: false,
    assetsInlineLimit: 100000000,
  },
});
