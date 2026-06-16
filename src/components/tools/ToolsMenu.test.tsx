import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ToolsMenu } from "./ToolsMenu";
import { ToolWindows } from "./ToolWindows";
import { useTools, useToolWindows } from "./tools-store";
import type { Tool } from "@/lib/spira/tools-api";

vi.mock("@/lib/spira/tools-api", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/lib/spira/tools-api")>();
  return { ...actual, listTools: vi.fn(), listRecords: vi.fn() };
});

// TanStack <Link> needs a router context; stub it to a plain anchor.
vi.mock("@tanstack/react-router", () => ({
  Link: ({ children, to }: { children: React.ReactNode; to: string }) => (
    <a href={to}>{children}</a>
  ),
}));

import { listRecords } from "@/lib/spira/tools-api";

function tool(id: number, name: string, goalId: number | null): Tool {
  return {
    id,
    goalId,
    name,
    schemaJson: JSON.stringify({
      layout: "table",
      columns: [{ key: "c", primitive: "text" }],
    }),
    placement: goalId == null ? "tools" : "goal",
    createdBy: "ai",
    createdAt: "2026-06-14T00:00:00Z",
  };
}

/** Seed the shared store directly (avoids the one-time network load). */
function seedTools(tools: Tool[]) {
  useTools.setState({
    tools,
    loaded: true,
    loading: false,
    recordsVersion: {},
  });
}

beforeEach(() => {
  vi.clearAllMocks();
  (listRecords as ReturnType<typeof vi.fn>).mockResolvedValue([]);
  useTools.setState({
    tools: [],
    loaded: false,
    loading: false,
    recordsVersion: {},
  });
  useToolWindows.setState({ windows: [], topZ: 1 });
});

describe("ToolsMenu", () => {
  it("groups this goal's tools and global tools, plus an All tools link", async () => {
    seedTools([tool(1, "Vacancies", 7), tool(2, "Weight log", null)]);
    render(<ToolsMenu goalId="7" />);

    await userEvent.click(screen.getByRole("button", { name: /^tools/i }));

    expect(await screen.findByText("This goal")).toBeInTheDocument();
    expect(screen.getByText("Vacancies")).toBeInTheDocument();
    expect(screen.getByText("Global")).toBeInTheDocument();
    expect(screen.getByText("Weight log")).toBeInTheDocument();
    expect(screen.getByText("All tools")).toBeInTheDocument();
  });

  it("opens a tool in a floating window when its menu item is chosen", async () => {
    seedTools([tool(1, "Vacancies", 7)]);
    render(
      <>
        <ToolsMenu goalId="7" />
        <ToolWindows />
      </>,
    );

    await userEvent.click(screen.getByRole("button", { name: /^tools/i }));
    await userEvent.click(await screen.findByText("Vacancies"));

    // The floating window appears (its own control set: minimize + close).
    await waitFor(() => {
      expect(useToolWindows.getState().windows).toHaveLength(1);
    });
    expect(
      await screen.findByRole("button", { name: "Close" }),
    ).toBeInTheDocument();
  });

  it("shows an empty hint when the user has no tools", async () => {
    seedTools([]);
    render(<ToolsMenu />);
    await userEvent.click(screen.getByRole("button", { name: /^tools/i }));
    expect(await screen.findByText(/no tools yet/i)).toBeInTheDocument();
  });
});
