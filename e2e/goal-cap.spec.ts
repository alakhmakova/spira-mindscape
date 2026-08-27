import { test, expect } from "@playwright/test";

/**
 * **The cap on goals in motion, as the user meets it** (owner, 2026-08-25).
 *
 * The backend refuses the fifty-first (`GoalCapIntegrationTest`, `test_goal_cap_e2e.py`). What
 * those cannot show is what happens *on screen*: the goal appears optimistically the moment you
 * press Create, so a refusal that is not surfaced leaves a card sitting there that does not exist
 * on the server and vanishes on the next reload. This checks the two things the user experiences —
 * the sentence arrives, and the phantom card is taken back.
 *
 * The allowance is filled through **one GraphQL request** rather than fifty trips through the UI:
 * the backend allows 120 GraphQL calls a minute, and this spec is about the fifty-first, not about
 * the fifty.
 */

const MAX_ACTIVE_GOALS = 50;

/** Run a GraphQL document through the page's own session. */
async function gql(page: import("@playwright/test").Page, query: string) {
  return page.evaluate(async (q) => {
    const res = await fetch("/graphql", {
      method: "POST",
      credentials: "include",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ query: q }),
    });
    return res.json();
  }, query);
}

test("the fifty-first goal is refused, and the optimistic card is taken back", async ({
  page,
}) => {
  test.setTimeout(120_000);
  await page.goto("/");
  await page.waitForLoadState("networkidle");

  // Start from a known state: this user's goals accumulate across the suite.
  const existing = (await gql(page, "{ goals { id } }")) as {
    data: { goals: { id: string }[] };
  };
  const ids = existing.data.goals.map((g) => g.id);
  if (ids.length) {
    await gql(
      page,
      `mutation { ${ids.map((id, i) => `d${i}: deleteGoal(id: "${id}")`).join("\n")} }`,
    );
  }

  const created: string[] = [];
  try {
    const fill = (await gql(
      page,
      `mutation { ${Array.from(
        { length: MAX_ACTIVE_GOALS },
        (_, i) =>
          `g${i}: createGoal(input: { title: "Cap filler ${i}", confidence: 5 }) { id }`,
      ).join("\n")} }`,
    )) as { data: Record<string, { id: string }> };
    for (let i = 0; i < MAX_ACTIVE_GOALS; i++)
      created.push(fill.data[`g${i}`].id);

    await page.reload();
    await page.waitForLoadState("networkidle");

    // Now try the fifty-first the way a person would.
    await page.getByRole("button", { name: "New goal" }).first().click();
    await page
      .getByPlaceholder("e.g. Launch Spira to first 50 users")
      .fill("One too many");
    await page
      .getByRole("button", { name: "Create goal", exact: true })
      .click();

    // The sentence has to say what to do about it — a bare "invalid" would be useless.
    //
    // Matched on the second half deliberately: the dashboard's own subtitle reads "50 goals in
    // motion. Pick one to dive into…", so the opening words appear twice on the page and a
    // looser locator would pass whether or not the banner ever appeared.
    await expect(
      page.getByText(/which is the most Spira keeps at once/i),
    ).toBeVisible();
    await expect(
      page.getByText(/Achieve or delete one to make room/i),
    ).toBeVisible();

    // …and the card that appeared optimistically must be gone, not left as a phantom that
    // disappears on the next reload with no explanation.
    await expect(page.getByRole("link", { name: "One too many" })).toHaveCount(
      0,
    );
  } finally {
    if (created.length) {
      await gql(
        page,
        `mutation { ${created.map((id, i) => `d${i}: deleteGoal(id: "${id}")`).join("\n")} }`,
      );
    }
  }
});
