// Inline text is stored verbatim as plain text. Two things inside it get special rendering:
//   - a bare http(s) URL → a plain clickable link (no naming, no chip);
//   - a resource token `{{res:<id>}}` → a titled chip for an attached Resource
//     (see specs/2026-07-28-inline-resource-attachments/requirements.md).
// Editing stays plain-text, so removing either one is just deleting text.

export type InlineSegment =
  | { type: "text"; value: string }
  | { type: "url"; url: string }
  | { type: "resource"; id: string };

// A bare http(s) URL. `[^\s<]` stops at whitespace/tags; trailing sentence punctuation is trimmed
// back out below so "see https://x.com." doesn't swallow the period into the link.
const URL_SOURCE = "https?:\\/\\/[^\\s<]+";
// Resource ids are server ids (numeric) or optimistic local ones (`local-xxxx`).
const RESOURCE_ID_SOURCE = "[A-Za-z0-9_-]+";
const SEGMENT_RE = new RegExp(
  `${URL_SOURCE}|\\{\\{res:(${RESOURCE_ID_SOURCE})\\}\\}`,
  "g",
);
const RESOURCE_TOKEN_RE = new RegExp(
  `\\{\\{res:(${RESOURCE_ID_SOURCE})\\}\\}`,
  "g",
);
const TRAILING_PUNCT_RE = /[.,;:!?)\]}'"]+$/;

/**
 * Length to reserve for a not-yet-created resource token, used when deciding whether swapping a
 * long URL for a chip would bring a field back under its limit. `{{res:}}` is 8 characters, so
 * this leaves room for a generous id.
 */
export const RESOURCE_TOKEN_BUDGET = 24;

/** The inline token that references a resource by id. */
export function resourceToken(id: string): string {
  return `{{res:${id}}}`;
}

/** Split a string into plain text, URL, and resource-token segments for rendering. */
export function splitInline(text: string): InlineSegment[] {
  const segments: InlineSegment[] = [];
  let last = 0;
  SEGMENT_RE.lastIndex = 0;
  let match: RegExpExecArray | null;
  while ((match = SEGMENT_RE.exec(text)) !== null) {
    if (match.index > last) {
      segments.push({ type: "text", value: text.slice(last, match.index) });
    }
    last = match.index + match[0].length;

    if (match[1] !== undefined) {
      segments.push({ type: "resource", id: match[1] });
      continue;
    }
    let url = match[0];
    const punct = url.match(TRAILING_PUNCT_RE);
    if (punct) {
      url = url.slice(0, -punct[0].length);
      segments.push({ type: "url", url });
      segments.push({ type: "text", value: punct[0] });
      continue;
    }
    segments.push({ type: "url", url });
  }
  if (last < text.length) {
    segments.push({ type: "text", value: text.slice(last) });
  }
  return segments;
}

/**
 * A token as it appears while **editing**, where the braces may hold either the stored id or the
 * resource's name (which can contain spaces and punctuation) — see `rewriteResourceTokens`.
 */
const LOOSE_TOKEN_RE = /\{\{res:([^{}]+)\}\}/g;

/** True when the text carries at least one `{{res:id}}` reference — to any resource. */
export function hasResourceToken(text: string): boolean {
  RESOURCE_TOKEN_RE.lastIndex = 0;
  return RESOURCE_TOKEN_RE.test(text);
}

/** True when the text carries a resource tag in either form (stored id or editable name). */
export function hasResourceTag(text: string): boolean {
  LOOSE_TOKEN_RE.lastIndex = 0;
  return LOOSE_TOKEN_RE.test(text);
}

/**
 * Replace every resource tag with plain text — for the places a value is read as prose rather
 * than rendered as a field (confirmation dialogs, toasts, search).
 */
export function stripResourceTokens(
  text: string,
  label: (inner: string) => string,
): string {
  return text
    .replace(LOOSE_TOKEN_RE, (_match, inner: string) => label(inner.trim()))
    .replace(/\s{2,}/g, " ")
    .trim();
}

/**
 * Rewrite every resource tag through `resolve`, which receives what is inside the braces (an id
 * or a name) and returns the replacement — or `null` to drop the braces and keep the bare text.
 * Used to swap ids for names when a field enters edit mode, and back again when it commits, so a
 * tag never survives as a dangling reference to something that can't be resolved.
 */
export function rewriteResourceTokens(
  text: string,
  resolve: (inner: string) => string | null,
): string {
  return text.replace(LOOSE_TOKEN_RE, (_match, inner: string) => {
    const trimmed = inner.trim();
    const next = resolve(trimmed);
    return next === null ? trimmed : resourceToken(next);
  });
}

/** True when the text references this specific resource. */
export function referencesResource(text: string, resourceId: string): boolean {
  return text.includes(resourceToken(resourceId));
}

/** Every resource id referenced by the text, in order, without duplicates. */
export function resourceIdsIn(text: string): string[] {
  const ids: string[] = [];
  RESOURCE_TOKEN_RE.lastIndex = 0;
  let match: RegExpExecArray | null;
  while ((match = RESOURCE_TOKEN_RE.exec(text)) !== null) {
    if (!ids.includes(match[1])) ids.push(match[1]);
  }
  return ids;
}

/**
 * Replace every reference to one resource with plain text — used when the resource is deleted,
 * so the sentence keeps reading (its title stays) and no dangling token remains.
 */
export function replaceResourceToken(
  text: string,
  resourceId: string,
  replacement: string,
): string {
  return text.split(resourceToken(resourceId)).join(replacement);
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
