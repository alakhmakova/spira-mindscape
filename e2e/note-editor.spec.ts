import { test, expect, type Page } from "@playwright/test";
import { createGoal } from "./helpers";

/**
 * The note editor, driven for real: typing, a toolbar mark, and a **paste**.
 *
 * ## Why it exists
 *
 * `prosemirror-view` below 1.42.3 had an XSS in its paste handling (GHSA-c8x8-7fp4-3x9w, CVSS
 * 8.5): pasted HTML could run script in the page holding the editor. The fix arrived as a
 * transitive bump — `@tiptap/*` 3.31.4 → `@tiptap/pm` → `prosemirror-view` 1.42.6 — and moving
 * nine minor versions of TipTap at once is exactly the change that can leave the editor building
 * cleanly and behaving differently. Nothing covered this surface before: `tsc` and `vitest` never
 * open it, and the Android app runs the same bundle inside a WebView, so a break here breaks both
 * surfaces.
 *
 * **This spec does not reproduce the advisory**, and no spec here could honestly claim to: the
 * payload is undisclosed, and the vector it describes is not live in this editor anyway — which is
 * a measured fact, not an assumption, and is the third assertion below. The guard against the fix
 * silently leaving is a version floor over the lockfile,
 * `src/components/spira/editor-security.test.ts`; that file also explains why CI cannot be the
 * guard (the advisories are HIGH, and the supply-chain gate blocks on CRITICAL only).
 *
 * What this spec is for is the *behaviour* either side of that bump — that a pasted selection
 * still arrives as rich text rather than flattened, and that the schema still refuses what it has
 * always refused.
 */

/** The editor's editable element — TipTap puts `aria-label="Note"` on the contenteditable. */
function noteEditor(page: Page) {
  return page.getByRole("textbox", { name: "Note" });
}

/**
 * Paste `html` into the editor by dispatching the event ProseMirror actually listens for.
 *
 * Playwright's `Control+V` would need the OS clipboard, which a headless run has no reliable way
 * to seed with a `text/html` flavour; a constructed `ClipboardEvent` carrying a `DataTransfer`
 * goes through the same `handlePaste` → `parseFromClipboard` path. `text/plain` is set too,
 * because that is what a real clipboard carries alongside the HTML — without it the assertion
 * could pass on a fallback that was never exercised.
 */
async function pasteHtml(page: Page, html: string, plain: string) {
  await page.evaluate(
    ({ html, plain }) => {
      const el = document.querySelector<HTMLElement>(
        '[aria-label="Note"][contenteditable="true"]',
      );
      if (!el)
        throw new Error("the note editor's contenteditable was not found");
      const data = new DataTransfer();
      data.setData("text/html", html);
      data.setData("text/plain", plain);
      el.dispatchEvent(
        new ClipboardEvent("paste", {
          clipboardData: data,
          bubbles: true,
          cancelable: true,
        }),
      );
    },
    { html, plain },
  );
}

test("the note editor types, formats, and keeps a pasted selection rich", async ({
  page,
}) => {
  test.setTimeout(120_000);

  // A render throw inside TipTap would otherwise be swallowed by the ErrorBoundary, and the next
  // assertion would fail for a reason that points nowhere near it. Collected here and checked
  // **as soon as the editor is on screen** as well as at the end — a check that only runs last
  // cannot deliver that, because the first failing assertion ends the test before it.
  const pageErrors: string[] = [];
  page.on("pageerror", (e) => pageErrors.push(e.message));

  await createGoal(page, `Note editor ${Date.now()}`);

  // Scoped to the create sheet, as `image.spec.ts` does: "Add resource" is the name of the
  // section's opener (`Resources.tsx:401`, `:407`) *and* of the sheet's submit (`:2226`), so an
  // unscoped `.last()` would be resolved only by Radix happening to portal the sheet last in DOM
  // order — and would silently re-open a blank sheet if that ever changed.
  await page.getByRole("button", { name: "Add resource" }).first().click();
  const sheet = page.getByRole("dialog");
  await sheet.getByRole("button", { name: "Note", exact: true }).click();
  await sheet
    .locator('div:has(> label:has-text("Title")) input')
    .first()
    .fill("Editor smoke");

  const editor = noteEditor(page);
  await expect(editor).toBeVisible();
  expect(pageErrors, "the editor threw while mounting").toEqual([]);

  // 1. Typing reaches the document.
  await editor.click();
  await editor.pressSequentially("Plain line");
  await expect(editor).toContainText("Plain line");

  // 2. A toolbar mark applies to the selection.
  await editor.press("Shift+Home");
  await page.getByRole("button", { name: "Bold", exact: true }).click();
  await expect(editor.locator("strong")).toHaveText("Plain line");

  // 3. A paste keeps its marks, and the schema drops what it has no node for.
  //
  //    Measured on this editor (2026-10-08): StarterKit here registers no image node, so a
  //    pasted `<img>` — the usual carrier for an `onerror` handler — does not survive the parse
  //    at all, and an `<a href="javascript:…">` comes through as its bare text. Asserting that
  //    is worth more than asserting the handler "did not run", which in an editor with no image
  //    node can only ever pass. If an image node is ever added, this assertion fails and the
  //    paste surface gets looked at again, which is the point.
  await editor.press("End");
  await editor.press("Enter");
  await pasteHtml(
    page,
    "<p><strong>pasted bold</strong> and <em>pasted italic</em>" +
      '<img src="x" onerror="window.__spiraPasteRan = true">' +
      '<a href="javascript:void 0">clickme</a></p>',
    "pasted bold and pasted italic clickme",
  );

  await expect(
    editor.locator("strong", { hasText: "pasted bold" }),
  ).toBeVisible();
  await expect(
    editor.locator("em", { hasText: "pasted italic" }),
  ).toBeVisible();
  await expect(editor.locator("img")).toHaveCount(0);
  await expect(editor.locator('a[href^="javascript:"]')).toHaveCount(0);

  // 4. The note saves and shows up in the list.
  await sheet.getByRole("button", { name: "Add resource" }).click();
  await page.waitForLoadState("networkidle");
  await expect(page.getByText("Editor smoke").first()).toBeVisible();

  expect(pageErrors, "the editor threw").toEqual([]);
});
