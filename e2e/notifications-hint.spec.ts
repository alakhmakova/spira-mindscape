import { test, expect, type Page } from "@playwright/test";
import { axeScan } from "./a11y-helpers";

/**
 * **"Turn on notifications"** — the one-time hint that circles the account figure the first time
 * "Add event" is pressed in a browser without notifications (owner, 2026-10-18,
 * `NotificationsHint.tsx`). A fresh Playwright context has never been asked, so it qualifies.
 */

const upcoming = (page: Page) => page.getByRole("region", { name: "Upcoming" });

async function pressAddEvent(page: Page) {
  await page.goto("/");
  await page.waitForLoadState("networkidle");
  await upcoming(page).getByRole("button", { name: "Add event" }).click();
}

for (const viewport of [
  { width: 1280, height: 900 },
  { width: 390, height: 844 },
]) {
  test.describe(`${viewport.width}px`, () => {
    test.use({ viewport });

    test("the figure opens Settings → Notifications, and the hint never comes back", async ({
      page,
    }) => {
      await pressAddEvent(page);
      const hint = page.getByRole("dialog", { name: "Turn on notifications" });
      await expect(hint).toBeVisible();
      await axeScan(page, "Notifications hint", {}, '[role="dialog"]');

      // The plate sits on the header's own figure.
      const figure = await page
        .locator('[data-hint-target="account"]')
        .boundingBox();
      const plate = await hint
        .getByRole("button", { name: "Open notification settings" })
        .boundingBox();
      expect(figure && plate).toBeTruthy();
      expect(
        Math.abs(figure!.x + figure!.width / 2 - (plate!.x + plate!.width / 2)),
      ).toBeLessThan(1);
      expect(
        Math.abs(
          figure!.y + figure!.height / 2 - (plate!.y + plate!.height / 2),
        ),
      ).toBeLessThan(1);

      await hint
        .getByRole("button", { name: "Open notification settings" })
        .click();
      await expect(page).toHaveURL(/\/settings\?tab=notifications/);
      await expect(hint).toHaveCount(0);
      // Leaving for Settings does not leave the form open behind it.
      await expect(page.getByRole("dialog", { name: "Add event" })).toHaveCount(
        0,
      );

      // Shown once: the next press goes straight to the form.
      await pressAddEvent(page);
      await expect(
        page.getByRole("dialog", { name: "Add event" }),
      ).toBeVisible();
      await expect(
        page.getByRole("dialog", { name: "Turn on notifications" }),
      ).toHaveCount(0);
    });

    test("closing it carries on to the form that was asked for", async ({
      page,
    }) => {
      await pressAddEvent(page);
      const hint = page.getByRole("dialog", { name: "Turn on notifications" });
      await expect(hint).toBeVisible();
      await page.keyboard.press("Escape");
      await expect(hint).toHaveCount(0);
      await expect(
        page.getByRole("dialog", { name: "Add event" }),
      ).toBeVisible();
    });
  });
}
