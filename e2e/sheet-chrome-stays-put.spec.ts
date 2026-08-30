import { test, expect, Page } from "@playwright/test";

/**
 * **A sheet must never scroll inside its own frame, and a popover must never leave the screen**
 * (BUG-063, owner 2026-08-29: "нажми на дедлайн, появится календарь и ты увидишь какой бред
 * происходит").
 *
 * The New goal sheet is the one that shows both, so it is the one driven here. Every sheet in
 * the app is the same `DrawerContent`, so the first test guards all of them.
 *
 * The sizes are the two states of a phone under `interactive-widget=resizes-content`: the
 * keyboard shrinks the LAYOUT viewport, so `PHONE_KB_UP` is what is left above it. See
 * CLAUDE.md → Sheets → the height.
 */

const PHONE = { width: 412, height: 780 };
const PHONE_KB_UP = { width: 412, height: 430 };

const sheet = (page: Page) => page.locator("[data-vaul-drawer]").first();

async function openNewGoal(page: Page) {
  await page.goto("/");
  await page.waitForLoadState("networkidle");
  await page.setViewportSize(PHONE);
  await page.getByRole("button", { name: "New goal" }).first().click();
  await expect(
    page.getByPlaceholder("e.g. Launch Spira to first 50 users"),
  ).toBeVisible();
  // The open animation translates the sheet; measure only once it has landed.
  await page.waitForTimeout(600);
}

test.describe("sheet chrome stays put", () => {
  test("the sheet itself cannot be scrolled, at any viewport height", async ({
    page,
  }) => {
    // `overflow-hidden` clips a box but still makes it a **scroll container** — it only hides
    // the scrollbars. The sheet reported a `scrollHeight` of 1866 against a `clientHeight` of
    // 622, i.e. 1244 px of slack that nothing on screen accounts for, so anything that scrolls
    // programmatically could slide the whole form inside its own frame: `scrollIntoView` from a
    // descendant, the browser pulling a focused field into view, or a touch that pans it.
    // `overflow-clip` is not a scroll container at all. The body's `overflow-y-auto` is the one
    // scroller a sheet has, by design.
    await openNewGoal(page);

    for (const size of [PHONE, PHONE_KB_UP]) {
      await page.setViewportSize(size);
      await page.waitForTimeout(250);
      const moved = await sheet(page).evaluate((el) => {
        const d = el as HTMLElement;
        d.scrollTop = 400;
        const after = d.scrollTop;
        d.scrollTop = 0;
        return after;
      });
      expect(
        moved,
        `the sheet scrolled inside its own frame at ${size.height}px — ` +
          "`overflow-hidden` is back on DrawerContent (see drawer.tsx)",
      ).toBe(0);
    }
  });

  test("opening the deadline sheet leaves the form beneath it untouched", async ({
    page,
  }) => {
    // The symptom the owner photographed, in the shape the app has now. It used to be a popover
    // here, and `DeadlinePopover` calls `scrollIntoView` on the field so a long form makes room
    // for the calendar — `scrollIntoView` walks up and scrolls EVERY scrollable ancestor, and
    // the sheet was one. It landed at `scrollTop: 450` with its teal head at y −292: a stub of a
    // form with a calendar floating over it.
    //
    // The picker is a centred modal on a phone now (`e2e/deadline-picker.spec.ts`), so what is
    // guarded here is the thing that outlives the redesign: opening and dismissing a second
    // layer must leave the sheet underneath exactly as it was.
    await openNewGoal(page);
    const head = sheet(page).getByText("New goal", { exact: true });
    const before = (await head.boundingBox())!;

    await page.getByText("Pick a deadline").click();
    await page.waitForTimeout(700);
    // The date modal is on top; Radix takes the form out of the a11y tree while it is, which is
    // why the head is measured by its box rather than by role. The form's drawer stays the only
    // drawer on the page — the picker is a dialog, not a second sheet.
    expect(await page.locator("[data-vaul-drawer]").count()).toBe(1);
    await expect(page.getByRole("dialog")).toBeVisible();
    await page.getByRole("button", { name: "Cancel" }).click();
    await page.waitForTimeout(600);

    const after = (await head.boundingBox())!;
    expect(after.y).toBeCloseTo(before.y, 0);
    expect(
      await sheet(page).evaluate((el) => (el as HTMLElement).scrollTop),
    ).toBe(0);
  });

  test("a popover never grows past the room it has", async ({ page }) => {
    // Radix flips a popover above its trigger when there is no room below, but it will not
    // SHRINK one that fits neither way — it simply hangs off the top. The 414 px deadline
    // calendar was measured at y −136 on a phone with the keyboard up: its own head with the
    // close X, the month arrows and the weekday row were all above the screen, so the month
    // could not be changed and the picker could not be dismissed.
    //
    // The popover is the LAPTOP surface now, so the same squeeze is reproduced the way a laptop
    // meets it: a short window. `PopoverContent` caps itself to
    // `--radix-popover-content-available-height`, and the grid is what scrolls.
    await page.goto("/");
    await page.waitForLoadState("networkidle");
    await page.setViewportSize({ width: 1280, height: 420 });
    await page.getByRole("button", { name: "New goal" }).first().click();
    await expect(
      page.getByPlaceholder("e.g. Launch Spira to first 50 users"),
    ).toBeVisible();
    await page.waitForTimeout(400);

    await page.getByText("Pick a deadline").click();
    await page.waitForTimeout(700);

    const popper = page.locator("[data-radix-popper-content-wrapper]").first();
    const box = (await popper.boundingBox())!;
    expect(box.y).toBeGreaterThanOrEqual(0);
    expect(box.y + box.height).toBeLessThanOrEqual(421);
    // The two rows that were off the top: the head with the close X, and the weekday row that
    // sits under the month navigation. Named by things that do not change with the calendar
    // month, so this spec still means the same in November.
    await expect(
      popper.getByRole("button", { name: "Close" }),
    ).toBeInViewport();
    await expect(popper.getByText("Mo", { exact: true })).toBeInViewport();
  });
});

test.describe("a tap lands while the keyboard is up", () => {
  // A real finger, because the defect is in what Chrome does with a `click` whose element moved
  // between `mousedown` and `mouseup` — synthetic `.click()` never goes through that.
  test.use({ hasTouch: true, isMobile: true });

  test("tapping Confidence right after typing sets the value on the FIRST tap", async ({
    page,
  }) => {
    // The owner, 2026-08-29: "пишу текст … и после этого не закрывая клавиатуру нажимаю на
    // confidence — новая оценка не ставится, а просто закрывается клавиатура".
    //
    // `sheet-height.ts` used to publish `--app-vh` on `focusout` as well as on resize. A blur is
    // not the keyboard being gone, it is the keyboard *starting* to go — so that listener ran at
    // the one instant when nothing was focused any more while `innerHeight` was still
    // keyboard-sized, and republished the keyboard's height as the screen's. `--app-vh` went
    // 7.8 → 4.3, every sheet lost 34 px, and a bottom-anchored sheet moves its contents when it
    // shrinks. By `mouseup` the button was no longer under the finger, so Chrome dispatched no
    // `click` at all: the field blurred, the keyboard closed, and nothing else happened.
    //
    // Measured on the old code, at every finger travel from 0 to 12 px:
    //   pointerdown touchstart pointerup touchend mousedown        ← no click
    // and on the new code:
    //   pointerdown touchstart pointerup touchend mousedown click
    await openNewGoal(page);
    const title = page.getByPlaceholder("e.g. Launch Spira to first 50 users");
    await title.tap();
    await page.keyboard.type("Goal with a title");
    // The keyboard is up: `interactive-widget=resizes-content` leaves this much layout viewport.
    await page.setViewportSize(PHONE_KB_UP);
    await page.waitForTimeout(350);

    const seven = page.getByRole("button", { name: "Confidence 7" });
    await seven.scrollIntoViewIfNeeded();
    const b = (await seven.boundingBox())!;
    await page.touchscreen.tap(b.x + b.width / 2, b.y + b.height / 2);
    await page.waitForTimeout(400);

    await expect(
      page
        .locator("[data-vaul-drawer]")
        .getByText(/^\d+\/10$/)
        .first(),
      "the first tap on Confidence did nothing — something is moving the sheet on blur " +
        "(see the `focusout` note in src/lib/spira/sheet-height.ts)",
    ).toHaveText("7/10");
  });
});
