import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

import { Upcoming } from "./Upcoming";
import { useSpira } from "@/lib/spira/store";
import { useUpcoming } from "@/lib/spira/upcoming-store";
import type { UpcomingEventRecord } from "@/lib/spira/upcoming";
import type { Goal } from "@/lib/spira/types";

/** Local midnight `n` days from now, as the app stores dates. */
const inDays = (n: number) => {
  const t = new Date();
  return new Date(t.getFullYear(), t.getMonth(), t.getDate() + n).toISOString();
};

function goal(patch: Partial<Goal> = {}): Goal {
  return {
    id: "1",
    title: "Run a marathon",
    description: "",
    confidence: 5,
    deadline: inDays(3),
    createdAt: inDays(-30),
    reality: { actions: [], obstacles: [] },
    options: [],
    resources: [],
    targets: [],
    ...patch,
  };
}

const important = (
  id: string,
  title: string,
  n: number,
): UpcomingEventRecord => ({
  id,
  kind: "important",
  title,
  date: inDays(n),
});

const load = vi.fn();
const remove = vi.fn();

function setUp(events: UpcomingEventRecord[], goals: Goal[] = []) {
  useUpcoming.setState({ events, status: "ready", load, remove });
  useSpira.setState({ goals });
}

beforeEach(() => vi.clearAllMocks());

const column = () => screen.getByRole("region", { name: "Upcoming" });

describe("Upcoming — what it shows", () => {
  it("groups events under their category, in the column's order, with the empty ones left out", () => {
    setUp(
      [
        { id: "g", kind: "goal", goalId: "1" },
        important("i", "Mom's birthday", 0),
      ],
      [goal()],
    );
    render(<Upcoming />);

    const headings = within(column())
      .getAllByRole("button", { expanded: true })
      .map((b) => b.textContent);
    expect(headings).toEqual(["Important dates", "Goals"]);
    expect(within(column()).getByText("Mom's birthday")).toBeInTheDocument();
    expect(within(column()).getByText("Today")).toBeInTheDocument();
    // A link is named after its goal and dated by the goal's deadline.
    expect(within(column()).getByText("Run a marathon")).toBeInTheDocument();
    expect(within(column()).getByText("In 3 days")).toBeInTheDocument();
  });

  it("shows ten at most, across all categories, the nearest first", () => {
    setUp(
      Array.from({ length: 13 }, (_, i) => important(`e${i}`, `Date ${i}`, i)),
    );
    render(<Upcoming />);

    const items = within(column()).getAllByRole("listitem");
    expect(items).toHaveLength(10);
    expect(items[0]).toHaveTextContent("Date 0");
    expect(within(column()).queryByText("Date 10")).not.toBeInTheDocument();
  });

  it("says when there is nothing coming up", () => {
    setUp([important("past", "Last week", -7)]);
    render(<Upcoming />);
    expect(within(column()).getByText(/Nothing coming up/)).toBeInTheDocument();
  });

  it("folds a category behind its caret", async () => {
    const user = userEvent.setup();
    setUp([important("i", "Trip", 2)]);
    render(<Upcoming />);

    await user.click(screen.getByRole("button", { name: "Important dates" }));
    expect(
      screen.getByRole("button", { name: "Important dates" }),
    ).toHaveAttribute("aria-expanded", "false");
    expect(screen.queryByText("Trip")).not.toBeVisible();
  });
});

describe("Upcoming — loading", () => {
  it("asks the server once, when it has not yet", () => {
    useUpcoming.setState({ events: [], status: "idle", load, remove });
    useSpira.setState({ goals: [] });
    render(<Upcoming />);
    expect(load).toHaveBeenCalledTimes(1);
  });

  it("says it could not load, and tries again on request", async () => {
    const user = userEvent.setup();
    useUpcoming.setState({ events: [], status: "error", load, remove });
    useSpira.setState({ goals: [] });
    render(<Upcoming />);

    expect(
      screen.getByText(/Couldn't load your upcoming dates/),
    ).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Try again" }));
    expect(load).toHaveBeenCalledTimes(1);
  });
});

describe("Upcoming — removing", () => {
  it("asks first about an important date, without promising a deadline goes", async () => {
    const user = userEvent.setup();
    setUp([important("i", "Mom's birthday", 2)]);
    render(<Upcoming />);

    await user.click(
      screen.getByRole("button", {
        name: 'Remove "Mom\'s birthday" from Upcoming',
      }),
    );
    const dialog = screen.getByRole("alertdialog");
    expect(dialog).toHaveTextContent(
      'Are you sure you want to remove "Mom\'s birthday"',
    );
    expect(dialog).not.toHaveTextContent("deadline");
    expect(remove).not.toHaveBeenCalled();

    await user.click(
      within(dialog).getByRole("button", { name: "Yes, remove" }),
    );
    expect(remove).toHaveBeenCalledWith(expect.objectContaining({ id: "i" }));
  });

  it("says a linked goal's deadline goes with it", async () => {
    const user = userEvent.setup();
    setUp([{ id: "g", kind: "goal", goalId: "1" }], [goal()]);
    render(<Upcoming />);

    await user.click(
      screen.getByRole("button", {
        name: 'Remove "Run a marathon" from Upcoming',
      }),
    );
    expect(screen.getByRole("alertdialog")).toHaveTextContent(
      "This also removes the goal's deadline",
    );
  });

  it("keeps everything when the user goes back", async () => {
    const user = userEvent.setup();
    setUp([important("i", "Trip", 2)]);
    render(<Upcoming />);

    await user.click(
      screen.getByRole("button", { name: 'Remove "Trip" from Upcoming' }),
    );
    await user.click(screen.getByRole("button", { name: "No, go back" }));
    expect(remove).not.toHaveBeenCalled();
    expect(screen.getByText("Trip")).toBeInTheDocument();
  });
});

describe("Upcoming — adding", () => {
  it("opens the Add event form", async () => {
    const user = userEvent.setup();
    setUp([]);
    render(<Upcoming />);

    await user.click(screen.getByRole("button", { name: "Add event" }));
    expect(
      screen.getByRole("dialog", { name: "Add event" }),
    ).toBeInTheDocument();
  });
});
