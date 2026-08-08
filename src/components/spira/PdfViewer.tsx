import { useCallback, useEffect, useRef, useState } from "react";
import { Loader2, FileWarning, ZoomIn, ZoomOut } from "lucide-react";
import * as pdfjsLib from "pdfjs-dist";
// Vite serves the worker as a same-origin asset (?url → hashed file under /assets),
// so it satisfies the CSP without any external host.
import pdfWorkerUrl from "pdfjs-dist/build/pdf.worker.min.mjs?url";
import { logger } from "@/lib/logger";
import { cn } from "@/lib/utils";

pdfjsLib.GlobalWorkerOptions.workerSrc = pdfWorkerUrl;

const ZOOM_STEP = 0.25;
const ZOOM_MIN = 0.5;
const ZOOM_MAX = 3;

function dataUrlToUint8Array(dataUrl: string): Uint8Array {
  const base64 = dataUrl.split(",")[1] ?? "";
  const binary = atob(base64);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
  return bytes;
}

/**
 * Renders a PDF inline by painting each page to a <canvas> via PDF.js. Unlike an
 * <iframe src=blob:…>, this works on mobile browsers too (Android Chrome / iOS Safari
 * don't embed PDFs in iframes). Pages fade in as they render, are displayed at a width
 * RELATIVE to the container (so they always fill it — never a thin strip when the mobile
 * drawer hasn't finished laying out) and can be zoomed with the toolbar.
 */
export function PdfViewer({
  dataUrl,
  title,
}: {
  dataUrl: string;
  title: string;
}) {
  const containerRef = useRef<HTMLDivElement>(null);
  // Holds the loaded PDFDocumentProxy so zoom/resize can re-render without re-parsing.
  const pdfRef = useRef<Awaited<
    ReturnType<typeof pdfjsLib.getDocument>["promise"]
  > | null>(null);
  // Bumped on every render pass so a superseded pass (rapid zoom/resize) bails out.
  const renderToken = useRef(0);
  const zoomRef = useRef(1);
  const lastWidth = useRef(0);
  const [status, setStatus] = useState<"loading" | "ready" | "error">(
    "loading",
  );
  const [zoom, setZoom] = useState(1);
  zoomRef.current = zoom;

  const renderAll = useCallback(async () => {
    const pdf = pdfRef.current;
    const container = containerRef.current;
    if (!pdf || !container) return;
    const width = container.clientWidth;
    // Container not laid out yet (e.g. mobile drawer still opening) — the ResizeObserver
    // will call again once it has a real width. Rendering at width 0 = the "thin strip".
    if (!width) return;
    lastWidth.current = width;
    const z = zoomRef.current;
    const token = ++renderToken.current;
    container.replaceChildren();
    const dpr = Math.min(window.devicePixelRatio || 1, 2);
    let firstPageShown = false;

    for (let pageNum = 1; pageNum <= pdf.numPages; pageNum++) {
      if (token !== renderToken.current) return; // superseded
      const page = await pdf.getPage(pageNum);
      const base = page.getViewport({ scale: 1 });
      // Backing-store resolution: fit the container width, times zoom, times DPR.
      const scale = (width / base.width) * z;
      const viewport = page.getViewport({ scale });

      const canvas = document.createElement("canvas");
      canvas.width = Math.max(1, Math.floor(viewport.width * dpr));
      canvas.height = Math.max(1, Math.floor(viewport.height * dpr));
      // Display width is RELATIVE to the container (never a fixed px that could be stale):
      // 100% fits it, >100% overflows for horizontal scroll when zoomed in.
      canvas.style.width = `${z * 100}%`;
      canvas.style.height = "auto";
      canvas.className =
        "mx-auto block rounded-md border hairline bg-white opacity-0 transition-opacity duration-300";
      const ctx = canvas.getContext("2d");
      if (!ctx) continue;
      container.appendChild(canvas);

      await page.render({
        canvasContext: ctx,
        viewport,
        transform: dpr !== 1 ? [dpr, 0, 0, dpr, 0, 0] : undefined,
      }).promise;
      if (token !== renderToken.current) return;
      requestAnimationFrame(() => {
        canvas.style.opacity = "1";
      });
      // Reveal the surface as soon as the first page paints, not after all pages.
      if (!firstPageShown) {
        firstPageShown = true;
        setStatus("ready");
      }
    }
    setStatus("ready");
  }, []);

  // Load the document once per dataUrl.
  useEffect(() => {
    let cancelled = false;
    setStatus("loading");
    setZoom(1);
    lastWidth.current = 0;
    const task = pdfjsLib.getDocument({
      data: dataUrlToUint8Array(dataUrl),
      isEvalSupported: false, // avoids needing 'unsafe-eval' in the CSP
    });
    task.promise
      .then((pdf) => {
        if (cancelled) return;
        pdfRef.current = pdf;
        void renderAll();
      })
      .catch((err) => {
        logger.reportError(err, { kind: "render" });
        if (!cancelled) setStatus("error");
      });
    return () => {
      cancelled = true;
      renderToken.current++;
      pdfRef.current = null;
      void task.destroy();
    };
  }, [dataUrl, renderAll]);

  // Re-render on zoom change (document already parsed).
  useEffect(() => {
    if (pdfRef.current) void renderAll();
  }, [zoom, renderAll]);

  // Render once the container has a real width, and again when it changes materially
  // (mobile drawer finishing its open animation, viewport resize, device rotation).
  useEffect(() => {
    const container = containerRef.current;
    if (!container) return;
    const observer = new ResizeObserver(() => {
      const width = container.clientWidth;
      if (!pdfRef.current || width === 0) return;
      // Ignore sub-pixel/scrollbar jitter; only re-render on a meaningful width change.
      if (Math.abs(width - lastWidth.current) > 4) void renderAll();
    });
    observer.observe(container);
    return () => observer.disconnect();
  }, [renderAll]);

  const zoomOut = () =>
    setZoom((z) => Math.max(ZOOM_MIN, +(z - ZOOM_STEP).toFixed(2)));
  const zoomIn = () =>
    setZoom((z) => Math.min(ZOOM_MAX, +(z + ZOOM_STEP).toFixed(2)));

  return (
    <div className="relative flex-1 min-h-0">
      {/* Zoom toolbar */}
      <div className="absolute right-3 top-3 z-10 flex items-center gap-0.5 rounded-md border hairline bg-surface/95 p-0.5 shadow-sm backdrop-blur">
        <button
          onClick={zoomOut}
          disabled={zoom <= ZOOM_MIN}
          aria-label="Zoom out"
          className="grid h-8 w-8 place-items-center rounded text-foreground/80 transition-colors hover:bg-secondary disabled:opacity-30"
        >
          <ZoomOut className="h-4 w-4" />
        </button>
        <button
          onClick={() => setZoom(1)}
          aria-label="Reset zoom"
          className="h-8 w-12 rounded text-xs font-medium tabular-nums text-foreground/80 transition-colors hover:bg-secondary"
        >
          {Math.round(zoom * 100)}%
        </button>
        <button
          onClick={zoomIn}
          disabled={zoom >= ZOOM_MAX}
          aria-label="Zoom in"
          className="grid h-8 w-8 place-items-center rounded text-foreground/80 transition-colors hover:bg-secondary disabled:opacity-30"
        >
          <ZoomIn className="h-4 w-4" />
        </button>
      </div>

      {status === "loading" && (
        <div className="absolute inset-0 grid place-items-center text-muted-foreground">
          <Loader2 className="h-6 w-6 animate-spin" aria-label="Loading PDF" />
        </div>
      )}
      {status === "error" && (
        <div className="grid place-items-center gap-2 py-10 text-sm text-muted-foreground">
          <FileWarning className="h-6 w-6" />
          Couldn&apos;t display this PDF. Try downloading it instead.
        </div>
      )}
      <div
        ref={containerRef}
        className={cn(
          "absolute inset-0 overflow-auto p-2",
          "[&>canvas]:mb-3 [&>canvas:last-child]:mb-0",
        )}
        role="document"
        aria-label={title}
      />
    </div>
  );
}
