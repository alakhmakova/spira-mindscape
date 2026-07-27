// Bare URLs in plain-text fields are rendered as plain clickable links (no naming, no markdown).
// Text is stored verbatim; a URL is just a URL in the string. `splitUrls` finds the URLs for
// rendering; editing is plain-text, so deleting a URL is just deleting text.

export type UrlSegment =
  | { type: "text"; value: string }
  | { type: "url"; url: string };

// A bare http(s) URL. `[^\s<]` stops at whitespace/tags; trailing sentence punctuation is trimmed
// back out below so "see https://x.com." doesn't swallow the period into the link.
const URL_RE = /https?:\/\/[^\s<]+/g;
const TRAILING_PUNCT_RE = /[.,;:!?)\]}'"]+$/;

/** Split a string into plain-text and URL segments (URLs rendered as clickable links). */
export function splitUrls(text: string): UrlSegment[] {
  const segments: UrlSegment[] = [];
  let last = 0;
  URL_RE.lastIndex = 0;
  let match: RegExpExecArray | null;
  while ((match = URL_RE.exec(text)) !== null) {
    let url = match[0];
    let trailing = "";
    const punct = url.match(TRAILING_PUNCT_RE);
    if (punct) {
      trailing = punct[0];
      url = url.slice(0, -trailing.length);
    }
    if (match.index > last) {
      segments.push({ type: "text", value: text.slice(last, match.index) });
    }
    segments.push({ type: "url", url });
    if (trailing) segments.push({ type: "text", value: trailing });
    last = match.index + match[0].length;
  }
  if (last < text.length) {
    segments.push({ type: "text", value: text.slice(last) });
  }
  return segments;
}

/** Only http/https URLs may be rendered as clickable links — blocks javascript:/data: XSS. */
export function isSafeHttpUrl(url: string): boolean {
  try {
    const protocol = new URL(url).protocol;
    return protocol === "http:" || protocol === "https:";
  } catch {
    return false;
  }
}
