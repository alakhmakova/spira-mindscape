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
  onTheDay: false,
};

/** The same reminder going off on the event's own day. */
const ON_THE_DAY = { ...DUE, onTheDay: true };

/** The Upcoming event behind {@link DUE}, as `upcomingEvents` returns it. */
const EVENT = {
  id: "9001",
  kind: "important",
  title: "Call the dentist",
  date: DUE.date,
  goalId: null,
  targetId: null,
  taskId: null,
  time: "14:00",
  reminders: [],
  status: "pending",
  resolvedAt: null,
};

const resolved: { id: string; status: string }[] = [];
let events: (typeof EVENT)[] = [];

async function stubDue(page: Page, initiallyDue: object[]) {
  let due = initiallyDue;
  events = [{ ...EVENT }];
  resolved.length = 0;
  const answers: {
    eventId: string;
    action: string;
    snoozeAmount: number | null;
    snoozeUnit: string | null;
  }[] = [];
  await page.route("**/graphql", async (route) => {
    const body = route.request().postData() ?? "";
    if (body.includes("query DueReminders")) {
      await route.fulfill({ json: { data: { dueReminders: due } } });
      return;
    }
    if (body.includes("query UpcomingEvents")) {
      await route.fulfill({ json: { data: { upcomingEvents: events } } });
      return;
    }
    if (body.includes("mutation ResolveUpcomingEvent")) {
      const { variables } = JSON.parse(body) as {
        variables: { id: string; status: string };
      };
      resolved.push(variables);
      events = events.map((e) =>
        e.id === variables.id
          ? {
              ...e,
              status: variables.status,
              resolvedAt: new Date().toISOString(),
            }
          : e,
      );
      await route.fulfill({
        json: {
          data: {
            resolveUpcomingEvent: events.find((e) => e.id === variables.id),
          },
        },
      });
      return;
    }
    if (body.includes("mutation AnswerReminder")) {
      const { variables } = JSON.parse(body) as {
        variables: {
          eventId: string;
          action: string;
          snoozeAmount: number | null;
          snoozeUnit: string | null;
        };
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
  // Ahead of the event's day a reminder only informs: I know or Snooze, nothing to mark done.
  await expect(screen.getByRole("button", { name: "I know" })).toBeFocused();
  await expect(screen.getByRole("button", { name: "Open" })).toHaveCount(0);
  await expect(screen.getByRole("button", { name: "Done" })).toHaveCount(0);

  // No way out but an answer.
  await page.keyboard.press("Escape");
  await expect(screen).toBeVisible();
  await page.mouse.click(5, 400);
  await expect(screen).toBeVisible();

  // Snooze asks how long first, and Back returns to the three answers without sending anything.
  await screen.getByRole("button", { name: "Snooze…" }).click();
  await screen.getByRole("button", { name: "Back" }).click();
  expect(answers).toEqual([]);
  await screen.getByRole("button", { name: "Snooze…" }).click();
  const picker = screen.getByRole("group", { name: "Snooze for" });
  // Any number, any unit: 3 hours here.
  await picker.getByRole("textbox", { name: "Number" }).fill("3");
  await picker.getByRole("radio", { name: "Hours" }).click();
  await picker.getByRole("button", { name: "Snooze for 3 hours" }).click();
  await expect(screen).toBeHidden();
  expect(answers).toEqual([
    { eventId: "9001", action: "snooze", snoozeAmount: 3, snoozeUnit: "hour" },
  ]);
});

test("the notification's Snooze lands on the picker, not on a blind ten minutes", async ({
  page,
}) => {
  const answers = await stubDue(page, [DUE]);
  await page.goto("/remind/9001?snooze=1");
  const picker = page
    .getByRole("alertdialog", { name: "Call the dentist" })
    .getByRole("group", { name: "Snooze for" });
  await expect(picker.getByRole("radio")).toHaveText([
    "Minutes",
    "Hours",
    "Days",
    "Weeks",
    "Months",
  ]);
  const number = picker.getByRole("textbox", { name: "Number" });
  await expect(number).toHaveValue("10");

  // Nothing outside 1..999 can be sent.
  await number.fill("0");
  await expect(
    picker.getByRole("button", { name: "Snooze", exact: true }),
  ).toBeDisabled();

  await number.fill("2");
  await picker.getByRole("radio", { name: "Months" }).click();
  await picker.getByRole("button", { name: "Snooze for 2 months" }).click();
  await expect(page).toHaveURL(/\/$/);
  expect(answers).toEqual([
    { eventId: "9001", action: "snooze", snoozeAmount: 2, snoozeUnit: "month" },
  ]);
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
  await first.getByRole("button", { name: "I know" }).click();

  const second = page.getByRole("alertdialog", { name: "Pay the rent" });
  await expect(second).toBeVisible();
  await second.getByRole("button", { name: "I know" }).click();
  await expect(second).toBeHidden();
  expect(answers.map((a) => a.action)).toEqual(["dismiss", "dismiss"]);
});

test("a tapped notification lands on /remind/<id>; one no longer due just opens the app", async ({
  page,
}) => {
  await stubDue(page, [DUE]);
  await page.goto("/remind/9001");
  const screen = page.getByRole("alertdialog", { name: "Call the dentist" });
  await screen.getByRole("button", { name: "I know" }).click();
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

test("on the event's day a reminder asks for a decision, and Open brings up the event's card", async ({
  page,
}) => {
  const answers = await stubDue(page, [ON_THE_DAY]);
  await page.goto("/");

  const screen = page.getByRole("alertdialog", { name: "Call the dentist" });
  await expect(screen.getByRole("button", { name: "Open" })).toBeFocused();
  await expect(screen.getByRole("button", { name: "I know" })).toHaveCount(0);
  await screen.getByRole("button", { name: "Open" }).click();
  expect(answers.map((a) => a.action)).toEqual(["open"]);

  // The card: the picture, the name, and three answers.
  const card = page.getByRole("dialog", { name: "Call the dentist" });
  await expect(card).toBeVisible();
  await expect(card.getByRole("button", { name: "Close" })).toBeVisible();
  await expect(card.getByRole("button").filter({ hasText: /\w/ })).toHaveText([
    "Done",
    "Won't do",
    "Postpone",
  ]);
  await card.getByRole("button", { name: "Won't do" }).click();
  await expect(card).toBeHidden();
  expect(resolved).toEqual([{ id: "9001", status: "wont_do" }]);
});

test("the notification's Open lands on the event's card", async ({ page }) => {
  const answers = await stubDue(page, [ON_THE_DAY]);
  await page.goto("/remind/9001?open=1");
  const card = page.getByRole("dialog", { name: "Call the dentist" });
  await expect(card).toBeVisible();
  await expect(page).toHaveURL(/\/$/);
  expect(answers.map((a) => a.action)).toEqual(["open"]);
  await card.getByRole("button", { name: "Done" }).click();
  await expect(card).toBeHidden();
  expect(resolved).toEqual([{ id: "9001", status: "done" }]);
});

test("the Important events timeline marks what is settled and opens what is not", async ({
  page,
}) => {
  await stubDue(page, []);
  events = [
    {
      ...EVENT,
      id: "1",
      title: "Book the venue",
      status: "done",
      resolvedAt: DUE.date,
    },
    {
      ...EVENT,
      id: "2",
      title: "Renew the passport",
      status: "wont_do",
      resolvedAt: DUE.date,
    },
    { ...EVENT, id: "3", title: "Call the dentist" },
  ];
  await page.goto("/");
  await page.getByRole("tab", { name: "Timeline" }).click();
  await expect(
    page.getByRole("heading", { name: "Important events timeline" }),
  ).toBeVisible();

  const timeline = page.getByRole("list", { name: "Important events" });
  await expect(timeline.getByRole("listitem")).toHaveCount(3);
  await expect(
    timeline.getByRole("listitem").filter({ hasText: "Book the venue" }),
  ).toContainText("Done on");
  await expect(
    timeline.getByRole("listitem").filter({ hasText: "Renew the passport" }),
  ).toContainText("Won't do · decided on");
  // Only the event still to do carries the button.
  await expect(timeline.getByRole("button")).toHaveCount(1);
  await timeline
    .getByRole("button", { name: "Let's do it: Call the dentist" })
    .click();
  await expect(
    page.getByRole("dialog", { name: "Call the dentist" }),
  ).toBeVisible();
});
