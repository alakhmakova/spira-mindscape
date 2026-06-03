/**
 * Export a rich-text note (HTML body) to a downloadable file.
 *
 * Zero external dependencies:
 * - txt  — plain text (HTML stripped)
 * - doc  — a Word-compatible HTML document (opens in Word / Google Docs with
 *          formatting preserved). It is a real, openable Word file; the wire
 *          format is legacy Word-HTML (.doc) rather than OOXML (.docx) so we
 *          avoid bundling a heavyweight docx generator.
 * - pdf  — printed via the browser (Save as PDF) for crisp, selectable text.
 *
 * Google Docs export is different: it calls the backend Drive integration to
 * create a real document (see {@link openInGoogleDocs}).
 */

import { getCsrfToken } from "@/lib/spira/auth";

function escapeHtml(s: string): string {
  const d = document.createElement("div");
  d.textContent = s;
  return d.innerHTML;
}

/**
 * Converts a UL/OL (and any nested lists) into flat paragraphs prefixed with a
 * literal bullet / number. Word's HTML import is unreliable about rendering
 * real <ul>/<li> markers, so for the .doc export we bake the marker into the
 * text — guaranteeing visible bullets in any viewer.
 */
function convertListToParagraphs(
  list: Element,
  level: number,
  out: DocumentFragment,
): void {
  const ordered = list.tagName === "OL";
  let idx = 1;
  for (const li of Array.from(list.children)) {
    if (li.tagName !== "LI") continue;
    const nested = Array.from(li.children).filter(
      (c) => c.tagName === "UL" || c.tagName === "OL",
    );
    nested.forEach((n) => n.remove());

    const p = document.createElement("p");
    p.setAttribute(
      "style",
      `margin:0 0 3pt 0;${level ? `padding-left:${level * 24}px;` : ""}`,
    );
    const isTask = /^\s*[☑☐]/.test(li.textContent ?? "");
    const marker = isTask ? "" : ordered ? `${idx}.  ` : "•  ";
    p.innerHTML = marker + li.innerHTML;
    out.appendChild(p);
    idx++;
    nested.forEach((n) => convertListToParagraphs(n, level + 1, out));
  }
}

/**
 * Normalises note HTML for export:
 * - converts TipTap task lists (checkbox markup Word/PDF can't render) into a
 *   plain list prefixed with ☑/☐,
 * - unwraps <p> inside <li>,
 * - (doc only) flattens lists to paragraphs with literal • / n. markers,
 * - drops trailing empty paragraphs/breaks (these cause a blank last PDF page).
 */
function prepareExportHtml(html: string, opts: { flattenLists?: boolean } = {}): string {
  const root = document.createElement("div");
  root.innerHTML = html || "";

  root.querySelectorAll('ul[data-type="taskList"]').forEach((ul) => {
    ul.removeAttribute("data-type");
    ul.querySelectorAll("li").forEach((li) => {
      const checked = li.getAttribute("data-checked") === "true";
      const text = (li.textContent ?? "").trim();
      li.removeAttribute("data-type");
      li.removeAttribute("data-checked");
      li.textContent = `${checked ? "☑" : "☐"} ${text}`;
    });
  });

  // TipTap wraps list-item content in <p> (`<li><p>text</p></li>`). Word renders
  // that as a gapped, bullet-less paragraph; unwrapping the <p> restores proper
  // bullets/numbers and removes the huge gaps. Nested lists (non-<p>) are kept.
  root.querySelectorAll("li").forEach((li) => {
    const paragraphs = Array.from(li.children).filter(
      (c) => c.tagName === "P",
    ) as HTMLElement[];
    paragraphs.forEach((p, i) => {
      if (i > 0) li.insertBefore(document.createElement("br"), p);
      while (p.firstChild) li.insertBefore(p.firstChild, p);
      li.removeChild(p);
    });
  });

  if (opts.flattenLists) {
    const topLists = Array.from(root.querySelectorAll("ul, ol")).filter(
      (l) => !l.parentElement?.closest("ul, ol"),
    );
    for (const list of topLists) {
      const frag = document.createDocumentFragment();
      convertListToParagraphs(list, 0, frag);
      list.replaceWith(frag);
    }
  }

  let last = root.lastElementChild;
  while (
    last &&
    /^(P|DIV|BR)$/.test(last.tagName) &&
    !(last.textContent ?? "").trim() &&
    !last.querySelector("img")
  ) {
    last.remove();
    last = root.lastElementChild;
  }

  return root.innerHTML;
}

function safeName(title: string, fallback: string): string {
  const base = (title || fallback).trim().replace(/[\\/:*?"<>|]+/g, "_").slice(0, 80);
  return base || fallback;
}

function triggerDownload(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

/** HTML → readable plain text, turning block elements into line breaks. */
function htmlToPlainText(html: string): string {
  const withBreaks = html
    .replace(/<\s*br\s*\/?>/gi, "\n")
    .replace(/<li[^>]*>/gi, "• ")
    .replace(/<\/(p|div|h[1-6]|li|blockquote|tr)>/gi, "\n");
  const el = document.createElement("div");
  el.innerHTML = withBreaks;
  const text = el.textContent ?? "";
  return text.replace(/\n{3,}/g, "\n\n").trim();
}

export function downloadNoteTxt(title: string, html: string): void {
  const blob = new Blob([htmlToPlainText(html)], {
    type: "text/plain;charset=utf-8",
  });
  triggerDownload(blob, `${safeName(title, "note")}.txt`);
}

const DOC_STYLES = `
  body { font-family: Calibri, Arial, sans-serif; font-size: 11pt; }
  h1 { font-size: 20pt; } h2 { font-size: 16pt; } h3 { font-size: 13pt; }
  ul { list-style-type: disc; } ol { list-style-type: decimal; }
  ul, ol { margin: 6pt 0; padding-left: 36pt; }
  li { margin: 0 0 3pt 0; }
  li p { margin: 0; }
  blockquote { border-left: 3px solid #ccc; margin-left: 0; padding-left: 10pt; color: #444; }
`;

export function downloadNoteDoc(title: string, html: string): void {
  const doc =
    `<!DOCTYPE html><html xmlns:o="urn:schemas-microsoft-com:office:office" ` +
    `xmlns:w="urn:schemas-microsoft-com:office:word" xmlns="http://www.w3.org/TR/REC-html40">` +
    `<head><meta charset="utf-8"><title>${escapeHtml(title)}</title>` +
    `<style>${DOC_STYLES}</style></head>` +
    `<body>${prepareExportHtml(html, { flattenLists: true })}</body></html>`;
  // The leading BOM helps Word detect UTF-8.
  const blob = new Blob(["﻿", doc], { type: "application/msword" });
  triggerDownload(blob, `${safeName(title, "note")}.doc`);
}

const PDF_STYLES = `
  /* Page margin must come from @page so it repeats on EVERY page — a body
     margin only indents the first page (that was the "no top margin from
     page 2" bug). Compact, document-like density so a 2-page CV stays ~2 pages. */
  @page { margin: 1.4cm 1.6cm; }
  html, body { margin: 0; padding: 0; }
  body { font-family: Georgia, "Times New Roman", serif; font-size: 10.5pt; line-height: 1.3; color: #111; }
  p { margin: 0 0 0.3em; }
  h1, h2, h3 { font-family: Arial, Helvetica, sans-serif; line-height: 1.2; margin: 0.5em 0 0.2em; }
  h1 { font-size: 15pt; } h2 { font-size: 12.5pt; } h3 { font-size: 11pt; }
  ul, ol { margin: 0.2em 0; padding-left: 1.3em; }
  li { margin: 0 0 0.15em; }
  li p { margin: 0; }
  blockquote { border-left: 3px solid #ccc; margin: 0.3em 0 0.3em 0; padding-left: 0.8em; color: #444; }
  a { color: #0c69a3; }
  /* Avoid awkward breaks right after a heading and trim trailing space. */
  h1, h2, h3 { break-after: avoid; }
  body > *:first-child { margin-top: 0; }
  body > *:last-child { margin-bottom: 0; }
`;

/**
 * Opens the note in a print-ready document and invokes the browser's print
 * dialog, where the user picks "Save as PDF" (vector, selectable text, no
 * external library).
 *
 * Uses a real popup window rather than a hidden iframe: browsers reliably apply
 * the document's `@page` margins when printing a top-level window, whereas an
 * iframe's `@page` is often ignored (which left the right/bottom margins wrong).
 * Falls back to a hidden iframe if the popup is blocked.
 */
export function printNotePdf(title: string, html: string): void {
  const docHtml =
    `<!DOCTYPE html><html><head><meta charset="utf-8">` +
    `<title>${escapeHtml(title || "note")}</title><style>${PDF_STYLES}</style></head>` +
    `<body>${prepareExportHtml(html)}</body></html>`;

  const win = window.open("", "_blank", "width=820,height=1000");
  if (win) {
    win.document.open();
    win.document.write(docHtml);
    win.document.close();
    win.onafterprint = () => win.close();
    const print = () => {
      win.focus();
      win.print();
    };
    if (win.document.readyState === "complete") setTimeout(print, 250);
    else win.onload = () => setTimeout(print, 150);
    return;
  }

  // Popup blocked → hidden-iframe fallback.
  const iframe = document.createElement("iframe");
  iframe.setAttribute("aria-hidden", "true");
  Object.assign(iframe.style, {
    position: "fixed",
    right: "0",
    bottom: "0",
    width: "0",
    height: "0",
    border: "0",
  });
  document.body.appendChild(iframe);
  const idoc = iframe.contentDocument;
  const iwin = iframe.contentWindow;
  if (!idoc || !iwin) {
    iframe.remove();
    return;
  }
  idoc.open();
  idoc.write(docHtml);
  idoc.close();
  iwin.onafterprint = () => setTimeout(() => iframe.remove(), 1000);
  setTimeout(() => {
    iwin.focus();
    iwin.print();
  }, 250);
}

/**
 * Creates a real Google Doc from the note via the backend Drive integration and
 * opens it in a new tab. The backend ({@code POST /api/notes/google-doc}) uses
 * the user's stored Google refresh token to call the Drive API and convert the
 * note's HTML into an editable document; it returns the document's `webViewLink`.
 *
 * Requires the user to be signed in with Google and to have granted Drive access
 * (the `drive.file` scope). On failure the promise rejects so the caller can show
 * an error toast.
 *
 * @returns the created document's shareable link
 */
/**
 * Network call only (no DOM): POST the note to the backend Drive integration and
 * return the created document's link. Sends the session cookie (`credentials`) and
 * the CSRF token, as every mutating `/api` call must. Extracted from
 * {@link openInGoogleDocs} so it can be unit-tested without a browser DOM.
 */
export async function createGoogleDocFromHtml(title: string, preparedHtml: string): Promise<string> {
  const res = await fetch("/api/notes/google-doc", {
    method: "POST",
    credentials: "include",
    headers: {
      "Content-Type": "application/json",
      "X-XSRF-TOKEN": getCsrfToken(),
    },
    body: JSON.stringify({ title, html: preparedHtml }),
  });
  if (!res.ok) {
    const body = await res.text().catch(() => "");
    throw new Error(body || `Failed to create Google Doc (${res.status})`);
  }
  const { webViewLink } = (await res.json()) as { webViewLink: string };
  return webViewLink;
}

export async function openInGoogleDocs(html: string, title?: string): Promise<string> {
  const prepared = prepareExportHtml(html);

  // Reserve a tab synchronously inside the user-gesture so the browser doesn't
  // block the popup after the async request resolves; we set its URL afterwards.
  const reserved = window.open("", "_blank");

  try {
    const webViewLink = await createGoogleDocFromHtml(title ?? "Spira note", prepared);
    if (reserved) reserved.location.href = webViewLink;
    else window.open(webViewLink, "_blank", "noopener,noreferrer");
    return webViewLink;
  } catch (e) {
    reserved?.close();
    throw e;
  }
}
