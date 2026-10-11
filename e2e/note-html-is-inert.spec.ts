import { test, expect } from "@playwright/test";

/**
 * `safe-html.ts` in a real browser, where images load and handlers fire — jsdom does neither, so
 * its unit test can only pin the contract.
 *
 * The control matters as much as the check: the same markup in a **live** detached `div` (what
 * `stripHtml` and the note export used to do) must fire, or this spec would pass against nothing.
 */

const ATTACK =
  '<p>hi</p><img src="/__inert_probe.png" onerror="window.__pwned = true">';

test("note HTML is read without loading or running anything in it", async ({
  page,
}) => {
  await page.goto("/");
  await page.waitForLoadState("networkidle");

  const probes: string[] = [];
  page.on("request", (r) => {
    if (r.url().includes("__inert_probe")) probes.push(r.url());
  });

  const result = await page.evaluate(async (html) => {
    const mod = await import("/src/lib/spira/safe-html.ts");
    const text = mod.htmlText(html);
    const clean = mod.sanitizeNoteHtml(html);
    await new Promise((r) => setTimeout(r, 300));
    return { text, clean, pwned: (window as { __pwned?: boolean }).__pwned };
  }, ATTACK);

  expect(result.text).toBe("hi");
  expect(result.clean).toBe("<p>hi</p>");
  expect(result.pwned).toBeUndefined();
  expect(probes).toEqual([]);

  // The control: the old way, a live element, does fetch the image and fire the handler.
  const control = await page.evaluate(async (html) => {
    const div = document.createElement("div");
    div.innerHTML = html;
    await new Promise((r) => setTimeout(r, 300));
    return (window as { __pwned?: boolean }).__pwned;
  }, ATTACK);
  expect(control).toBe(true);
});
