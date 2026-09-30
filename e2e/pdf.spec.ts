import { test, expect, type Page } from "@playwright/test";
import { fileURLToPath } from "node:url";
import { createGoal } from "./helpers";

const SAMPLE_PDF = fileURLToPath(
  new URL("./fixtures/sample.pdf", import.meta.url),
);

async function openSamplePdf(
  page: Page,
  viewport?: { width: number; height: number },
) {
  await createGoal(page, `E2E pdf ${Date.now()}`);
  if (viewport) await page.setViewportSize(viewport);

  // Open the "Add resource" sheet and choose the File type.
  await page.getByRole("button", { name: "Add resource" }).first().click();
  const dialog = page.getByRole("dialog");
  await dialog.getByRole("button", { name: "File" }).click();

  // Attach the sample PDF (the input is visually hidden).
  await dialog.locator('input[type="file"]').setInputFiles(SAMPLE_PDF);

  // Submit the form (the sheet's own "Add resource" button).
  await dialog.getByRole("button", { name: "Add resource" }).click();

  // The resource chip appears — open its preview.
  // The card, not its words: a resource card draws its name twice (the second copy is the
  // full name it lays over itself on hover), so a text query matches two nodes.
  const card = page.getByRole("button", {
    name: "sample.pdf",
    // `exact`, or the card's own "Actions for sample.pdf" button matches too.
    exact: true,
  });
  await expect(card).toBeVisible();
  await card.click();

  // The preview panel opens. Over a page the way out is the X at the end of the action group;
  // on a phone the panel fills the screen, and there it is the chevron, worded "Back"
  // (CLAUDE.md -> 3i).
  await expect(
    page.getByRole("button", { name: viewport ? "Back" : "Close" }),
  ).toBeVisible();
}

test.describe("Resources — PDF preview", () => {
  test("on a laptop a PDF opens in the browser's own viewer (search, print, thumbnails)", async ({
    page,
  }) => {
    await openSamplePdf(page);

    // The native viewer is an iframe on a blob URL. The PDF.js canvas that replaced it in
    // July left the laptop with nothing but zoom (owner, 2026-09-15).
    const frame = page.locator('iframe[title="sample.pdf"]');
    await expect(frame).toBeVisible({ timeout: 15_000 });
    await expect(frame).toHaveAttribute("src", /^blob:/);
    const box = await frame.boundingBox();
    expect(box?.width ?? 0).toBeGreaterThan(200);
    expect(box?.height ?? 0).toBeGreaterThan(200);
  });

  test("on a phone a PDF renders inline on a canvas (PDF.js)", async ({
    page,
  }) => {
    await openSamplePdf(page, { width: 390, height: 844 });

    // Phone browsers do not embed a PDF in an iframe — it shows nothing — so PDF.js paints
    // each page to a <canvas>. A visible canvas proves the inline render worked.
    const canvas = page.locator("canvas").first();
    await expect(canvas).toBeVisible({ timeout: 15_000 });
    const box = await canvas.boundingBox();
    expect(box?.width ?? 0).toBeGreaterThan(50);
    expect(box?.height ?? 0).toBeGreaterThan(50);
  });
});
