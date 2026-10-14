import { test, expect, type Page } from "@playwright/test";
import { axeScan } from "./a11y-helpers";

/**
 * **Decide now** — a reminder that has gone off takes the whole of Spira until it is answered
 * (specs/2026-10-10-upcoming-notifications/plan.md §5a).
 *
 * The due list is stubbed at the network: whether a reminder is due is the server's decision and
 * `UpcomingReminderIntegrationTest` pins it with a mocked clock. What this pins is the page —
 * that it covers the app, cannot be closed without an answer, sends the answer, and lets go.
 */

const DUE = {
  eventId: "9001",
  kind: "important",
  title: "Call the dentist",
  date: new Date(new Date().setHours(0, 0, 0, 0)).toISOString(),
  time: "14:00",
  goalId: null,
  firedAt: new Date().toISOString(),
};

async function stubDue(page: Page, initiallyDue: object[]) {
  let due = initiallyDue;
  const answers: { eventId: string; action: string }[] = [];
  await page.route("**/graphql", async (route) => {
    const body = route.request().postData() ?? "";
    if (body.includes("query DueReminders")) {
      await route.fulfill({ json: { data: { dueReminders: due } } });
      return;
    }
    if (body.includes("mutation AnswerReminder")) {
      const { variables } = JSON.parse(body) as {
        variables: { eventId: string; action: string };
      };
      answers.push(variables);
      due = due.filter(
        (d) => (d as { eventId: string }).eventId !== variables.eventId,
      );
      await route.fulfill({ json: { data: { answerReminder: true } } });
      return;
    }
    await route.continue();
  });
  return answers;
}

test("a due reminder covers the app, survives Escape, and goes once answered", async ({
  page,
}) => {
  const answers = await stubDue(page, [DUE]);
  await page.goto("/");

  const screen = page.getByRole("alertdialog", { name: "Call the dentist" });
  await expect(screen).toBeVisible();
  await expect(screen).toContainText("Today at 14:00");
  await expect(screen.getByRole("button", { name: "Done" })).toBeFocused();

  // No way out but an answer.
  await page.keyboard.press("Escape");
  await expect(screen).toBeVisible();
  await page.mouse.click(5, 400);
  await expect(screen).toBeVisible();

  await screen.getByRole("button", { name: "Snooze 10 min" }).click();
  await expect(screen).toBeHidden();
  expect(answers).toEqual([{ eventId: "9001", action: "snooze" }]);
});

test("several due reminders are answered one after another", async ({
  page,
}) => {
  const answers = await stubDue(page, [
    DUE,
    { ...DUE, eventId: "9002", title: "Pay the rent", time: null },
  ]);
  await page.goto("/");

  const first = page.getByRole("alertdialog", { name: "Call the dentist" });
  await expect(first).toContainText("1 more waiting");
  await first.getByRole("button", { name: "Done" }).click();

  const second = page.getByRole("alertdialog", { name: "Pay the rent" });
  await expect(second).toBeVisible();
  await second.getByRole("button", { name: "Done" }).click();
  await expect(second).toBeHidden();
  expect(answers.map((a) => a.action)).toEqual(["done", "done"]);
});

test("a tapped notification lands on /remind/<id>; one no longer due just opens the app", async ({
  page,
}) => {
  await stubDue(page, [DUE]);
  await page.goto("/remind/9001");
  const screen = page.getByRole("alertdialog", { name: "Call the dentist" });
  await screen.getByRole("button", { name: "Done" }).click();
  await expect(page).toHaveURL(/\/$/);

  await page.goto("/remind/12345");
  await expect(page).toHaveURL(/\/$/);
  await expect(page.getByRole("alertdialog")).toHaveCount(0);
});

test("the decide-now page has no axe violations", async ({ page }) => {
  await stubDue(page, [DUE]);
  await page.goto("/");
  await expect(page.getByRole("alertdialog")).toBeVisible();
  await axeScan(page, '[role="alertdialog"]', {});
});
