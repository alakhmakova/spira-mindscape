import { test, expect } from "@playwright/test";
import { fileURLToPath } from "node:url";
import { createGoal } from "./helpers";

const SAMPLE_PDF = fileURLToPath(
  new URL("./fixtures/sample.pdf", import.meta.url),
);

test.describe("Resources — PDF preview", () => {
  test("a PDF file resource renders inline on a canvas (PDF.js)", async ({
    page,
  }) => {
    await createGoal(page, `E2E pdf ${Date.now()}`);

    // Open the "Add resource" sheet and choose the File type.
    await page.getByRole("button", { name: "Add resource" }).first().click();
    const dialog = page.getByRole("dialog");
    await dialog.getByRole("button", { name: "File" }).click();

    // Attach the sample PDF (the input is visually hidden).
    await dialog.locator('input[type="file"]').setInputFiles(SAMPLE_PDF);

    // Submit the form (the sheet's own "Add resource" button).
    await dialog.getByRole("button", { name: "Add resource" }).click();

    // The resource chip appears — open its preview.
    await expect(page.getByText("sample.pdf")).toBeVisible();
    await page.getByText("sample.pdf").click();

    // The preview panel opens (teal header with a Close button).
    await expect(
      page.getByRole("button", { name: "Close preview" }),
    ).toBeVisible();

    // PDF.js paints each page to a <canvas>; the old iframe/blob approach showed
    // nothing on mobile. A visible canvas proves the inline render worked.
    const canvas = page.locator("canvas").first();
    await expect(canvas).toBeVisible({ timeout: 15_000 });
    const box = await canvas.boundingBox();
    expect(box?.width ?? 0).toBeGreaterThan(50);
    expect(box?.height ?? 0).toBeGreaterThan(50);
  });
});
