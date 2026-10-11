import { test, expect } from "@playwright/test";

/**
 * **The "New goal" button never covers the last Upcoming row** (owner, 2026-10-11).
 *
 * The All-goals page reserves room at its foot for the fixed round button, and that room was
 * sized for the goal cards (BUG-072). Upcoming then arrived below the list on a phone, and each of
 * its rows carries a remove X at the right edge — exactly where the circle sits. Scrolled to the
 * end, the last row's X touched the button's top edge. Measured as boxes, not by eye.
 */
test.use({
  viewport: { width: 390, height: 844 },
  hasTouch: true,
  isMobile: true,
});

test("scrolled to the end, the last Upcoming row clears the round button", async ({
  page,
}) => {
  const name = `FAB clearance ${Date.now()}`;
  const created = await page.request.post("/graphql", {
    data: {
      query:
        "mutation($input: CreateUpcomingEventInput!) { createUpcomingEvent(input: $input) { id } }",
      variables: {
        input: {
          kind: "important",
          title: name,
          date: new Date(Date.now() + 86_400_000).toISOString(),
        },
      },
    },
  });
  const id = (await created.json()).data.createUpcomingEvent.id as string;

  try {
    await page.goto("/");
    await page.waitForLoadState("networkidle");
    const remove = page.getByRole("button", {
      name: `Remove "${name}" from Upcoming`,
    });
    await expect(remove).toBeVisible();
    await page.evaluate(() => window.scrollTo(0, document.body.scrollHeight));

    // The last row of the column, wherever this run's date landed in it.
    const lastRemove = page
      .getByRole("region", { name: "Upcoming" })
      .getByRole("button", { name: /^Remove ".*" from Upcoming$/ })
      .last();
    const row = await lastRemove.boundingBox();
    const fab = await page
      .getByRole("button", { name: "New goal", exact: true })
      .last()
      .boundingBox();
    expect(row && fab).toBeTruthy();
    expect(
      row!.y + row!.height,
      "the last row's remove X runs under the New goal button",
    ).toBeLessThanOrEqual(fab!.y - 8);
  } finally {
    await page.request.post("/graphql", {
      data: { query: `mutation { deleteUpcomingEvent(id: "${id}") }` },
    });
  }
});
