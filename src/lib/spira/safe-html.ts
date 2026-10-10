/**
 * Note HTML that did not come from our own editor, made safe to put on a page.
 *
 * A note body is stored as HTML (TipTap's), and the server stores what it is sent: anyone can
 * write `<img src=x onerror=…>` into one through `/graphql` directly, and — the case that is not
 * self-inflicted — **the AI coach writes note bodies too**, so a prompt injected through an
 * attached document can put markup into a proposal that the panel then renders. The CSP
 * (`script-src 'self'`) blocks inline handlers in production; this is the layer that does not
 * depend on the header being there (the dev server sends none).
 *
 * Two rules, both needed:
 *
 *  - **Parse in an inert document.** `innerHTML` on an element of the live page — even a
 *    detached `createElement("div")` — fetches `<img>` sources and fires their `onerror`. An
 *    element of a document made by `createHTMLDocument` does neither; nothing in it can run.
 *  - **Keep only what the editor can produce** — an allowlist, never a blocklist of bad tags.
 *    Anything unknown is unwrapped (its text stays), and the few tags whose *contents* are code or
 *    a different parser's grammar (`script`, `style`, `svg`, `math`, …) go with their contents.
 */

/** A `<div>` in a document no script, image or style will ever load from. */
export function inertContainer(html: string): HTMLDivElement {
  const doc = document.implementation.createHTMLDocument("");
  const root = doc.createElement("div");
  root.innerHTML = html || "";
  return root;
}

/** The text of some HTML, read without rendering it. */
export function htmlText(html: string): string {
  return inertContainer(html).textContent ?? "";
}

/** What TipTap's StarterKit, task lists, links, highlight and text colour produce. */
const ALLOWED_TAGS = new Set([
  "p",
  "br",
  "hr",
  "div",
  "span",
  "strong",
  "b",
  "em",
  "i",
  "u",
  "s",
  "strike",
  "del",
  "mark",
  "code",
  "pre",
  "sub",
  "sup",
  "blockquote",
  "h1",
  "h2",
  "h3",
  "h4",
  "h5",
  "h6",
  "ul",
  "ol",
  "li",
  "label",
  "input",
  "a",
  "table",
  "thead",
  "tbody",
  "tr",
  "th",
  "td",
]);

/** Removed together with everything inside them. */
const DROPPED_WITH_CONTENT = new Set([
  "script",
  "style",
  "iframe",
  "frame",
  "frameset",
  "object",
  "embed",
  "applet",
  "svg",
  "math",
  "template",
  "noscript",
  "link",
  "meta",
  "base",
  "title",
  "form",
  "textarea",
  "select",
  "button",
]);

const GLOBAL_ATTRIBUTES = new Set([
  "class",
  "data-type",
  "data-checked",
  "data-color",
]);

/** Only colour declarations survive in `style` — enough for the editor's text colour and
 *  highlight, and nothing that can move or cover the page (`position`, `inset`, `z-index`). */
const STYLE_PROPERTY = /^\s*(color|background-color)\s*:\s*([^;]+?)\s*$/i;
const SAFE_COLOUR = /^(#[0-9a-f]{3,8}|rgba?\([\d\s.,%]+\)|[a-z]+)$/i;

function safeStyle(value: string): string {
  return value
    .split(";")
    .map((declaration) => STYLE_PROPERTY.exec(declaration))
    .filter((m): m is RegExpExecArray => !!m && SAFE_COLOUR.test(m[2].trim()))
    .map((m) => `${m[1].toLowerCase()}: ${m[2].trim()}`)
    .join("; ");
}

function safeHref(value: string): boolean {
  try {
    // Relative to a fixed base, so a relative href is judged by its own scheme, not the page's.
    const protocol = new URL(value, "https://x.invalid").protocol;
    return (
      protocol === "http:" || protocol === "https:" || protocol === "mailto:"
    );
  } catch {
    return false;
  }
}

function cleanAttributes(el: Element) {
  const tag = el.tagName.toLowerCase();
  for (const { name, value } of Array.from(el.attributes)) {
    const keep =
      GLOBAL_ATTRIBUTES.has(name) ||
      (name === "style" && safeStyle(value) !== "") ||
      (tag === "a" && name === "href" && safeHref(value)) ||
      (tag === "a" && name === "target" && value === "_blank") ||
      (tag === "ol" && name === "start" && /^\d+$/.test(value)) ||
      (tag === "input" && (name === "checked" || name === "disabled")) ||
      ((tag === "td" || tag === "th") &&
        (name === "colspan" || name === "rowspan") &&
        /^\d+$/.test(value));
    if (!keep) el.removeAttribute(name);
    else if (name === "style") el.setAttribute("style", safeStyle(value));
  }
  if (tag === "a") el.setAttribute("rel", "noopener noreferrer nofollow");
  // A task item's box, and nothing else an <input> could be.
  if (tag === "input") {
    el.setAttribute("type", "checkbox");
    el.setAttribute("disabled", "");
  }
}

function clean(node: Node) {
  for (const child of Array.from(node.childNodes)) {
    if (child.nodeType === Node.COMMENT_NODE) {
      child.remove();
      continue;
    }
    if (child.nodeType !== Node.ELEMENT_NODE) continue;
    const el = child as Element;
    const tag = el.tagName.toLowerCase();
    if (DROPPED_WITH_CONTENT.has(tag)) {
      el.remove();
      continue;
    }
    clean(el);
    if (ALLOWED_TAGS.has(tag)) {
      cleanAttributes(el);
    } else {
      // Unknown but harmless: keep what it says, lose the element.
      el.replaceWith(...Array.from(el.childNodes));
    }
  }
}

/** Note HTML reduced to what the editor itself could have written. */
export function sanitizeNoteHtml(html: string): string {
  const root = inertContainer(html);
  clean(root);
  return root.innerHTML;
}
