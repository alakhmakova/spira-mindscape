import { afterEach, describe, expect, it } from "vitest";
import { htmlText, inertContainer, sanitizeNoteHtml } from "./safe-html";

/**
 * Note HTML from outside our own editor — the API, or the AI coach — reduced to what the editor
 * could have written. The attacks below are the standard ones for an HTML sink; each must come
 * out harmless while the formatting a real note carries survives.
 */

afterEach(() => {
  delete (window as { pwned?: boolean }).pwned;
});

describe("sanitizeNoteHtml — what goes", () => {
  it.each([
    ["an inline handler", '<img src=x onerror="window.pwned=true">', ""],
    [
      "a script tag and its contents",
      "<p>a<script>window.pwned=true</script>b</p>",
      "<p>ab</p>",
    ],
    [
      "a javascript: link",
      '<a href="javascript:alert(1)">x</a>',
      '<a rel="noopener noreferrer nofollow">x</a>',
    ],
    [
      "a data: link",
      '<a href="data:text/html,<script>x</script>">x</a>',
      '<a rel="noopener noreferrer nofollow">x</a>',
    ],
    [
      "an obfuscated scheme",
      '<a href=" jav&#x09;ascript:alert(1)">x</a>',
      '<a rel="noopener noreferrer nofollow">x</a>',
    ],
    ["an iframe", '<iframe src="https://evil.example"></iframe>ok', "ok"],
    [
      "svg with a handler",
      '<svg><a xlink:href="javascript:x"><text>t</text></a></svg>ok',
      "ok",
    ],
    ["a style block", "<style>body{display:none}</style>ok", "ok"],
    [
      "a form posting elsewhere",
      '<form action="https://evil.example"><input name=p></form>ok',
      "ok",
    ],
    ["a comment", "<!-- hidden -->ok", "ok"],
    [
      "an unknown wrapper, keeping its text",
      "<details><summary>Hi</summary></details>",
      "Hi",
    ],
  ])("removes %s", (_, input, expected) => {
    expect(sanitizeNoteHtml(input)).toBe(expected);
  });

  it("keeps only colours from a style — nothing that can cover the page", () => {
    expect(
      sanitizeNoteHtml(
        '<span style="position:fixed;inset:0;z-index:9;color:#ff0000">x</span>',
      ),
    ).toBe('<span style="color: #ff0000">x</span>');
    expect(
      sanitizeNoteHtml(
        '<span style="background:url(https://t.example/p)">x</span>',
      ),
    ).toBe("<span>x</span>");
  });

  it("turns any input into a disabled checkbox", () => {
    expect(sanitizeNoteHtml('<input type="text" value="x" onfocus="y">')).toBe(
      '<input type="checkbox" disabled="">',
    );
  });
});

describe("sanitizeNoteHtml — what a real note keeps", () => {
  it("keeps the editor's formatting, lists and safe links", () => {
    const note =
      "<h2>Plan</h2><p><strong>Bold</strong>, <em>italic</em>, <u>u</u>, <s>s</s>, " +
      '<mark data-color="#fff3a0" style="background-color: #fff3a0">hi</mark></p>' +
      '<ul data-type="taskList"><li data-checked="true" data-type="taskItem">' +
      '<label><input type="checkbox" checked=""></label><div><p>Done</p></div></li></ul>' +
      '<ol start="3"><li><p>three</p></li></ol>' +
      "<blockquote><p>q</p></blockquote><pre><code>x = 1</code></pre>" +
      '<p><a href="https://example.com" target="_blank">site</a> ' +
      '<a href="mailto:a@b.co">mail</a></p>';
    const out = sanitizeNoteHtml(note);

    expect(out).toContain("<h2>Plan</h2>");
    expect(out).toContain("<strong>Bold</strong>");
    expect(out).toContain('data-type="taskList"');
    expect(out).toContain('data-checked="true"');
    expect(out).toContain('<input checked="" type="checkbox" disabled="">');
    expect(out).toContain('<ol start="3">');
    expect(out).toContain("background-color: #fff3a0");
    expect(out).toContain(
      '<a href="https://example.com" target="_blank" rel="noopener noreferrer nofollow">site</a>',
    );
    expect(out).toContain('href="mailto:a@b.co"');
    expect(out).toContain("<pre><code>x = 1</code></pre>");
  });
});

describe("parsing never runs anything", () => {
  // jsdom loads no images, so this pins the contract rather than proving it; the browser half is
  // `e2e/note-html-is-inert.spec.ts`.
  it("does not fire a handler while reading text", async () => {
    expect(htmlText('<p>hi</p><img src="x" onerror="window.pwned=true">')).toBe(
      "hi",
    );
    inertContainer('<img src="x" onerror="window.pwned=true">');
    await new Promise((r) => setTimeout(r, 20));
    expect((window as { pwned?: boolean }).pwned).toBeUndefined();
  });
});
