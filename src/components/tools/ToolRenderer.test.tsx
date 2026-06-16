import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ToolRenderer } from "./ToolRenderer";
import type { Tool, ToolRecord } from "@/lib/spira/tools-api";

// Mock the network layer; keep the pure helpers (parseSchema) real.
vi.mock("@/lib/spira/tools-api", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/lib/spira/tools-api")>();
  return {
    ...actual,
    listRecords: vi.fn(),
    addRecord: vi.fn(),
    updateRecord: vi.fn(),
    deleteRecord: vi.fn(),
  };
});

import {
  addRecord,
  deleteRecord,
  listRecords,
  updateRecord,
} from "@/lib/spira/tools-api";

const SCHEMA = JSON.stringify({
  layout: "table",
  columns: [
    { key: "company", label: "Company", primitive: "text" },
    { key: "applied", label: "Applied", primitive: "date" },
    {
      key: "status",
      label: "Status",
      primitive: "select",
      options: ["applied", "interview"],
    },
  ],
});

function tool(overrides: Partial<Tool> = {}): Tool {
  return {
    id: 1,
    goalId: 7,
    name: "Vacancies",
    schemaJson: SCHEMA,
    placement: "goal",
    createdBy: "ai",
    createdAt: "2026-06-14T00:00:00Z",
    ...overrides,
  };
}

function record(id: number, data: object): ToolRecord {
  return {
    id,
    toolDefId: 1,
    dataJson: JSON.stringify(data),
    createdAt: "2026-06-14T00:00:00Z",
    updatedAt: "2026-06-14T00:00:00Z",
  };
}

beforeEach(() => {
  vi.clearAllMocks();
  (listRecords as ReturnType<typeof vi.fn>).mockResolvedValue([]);
});

describe("ToolRenderer (table layout)", () => {
  it("renders column headers and existing rows from the API", async () => {
    (listRecords as ReturnType<typeof vi.fn>).mockResolvedValue([
      record(10, {
        company: "Acme",
        applied: "2026-06-14",
        status: "interview",
      }),
    ]);
    render(<ToolRenderer tool={tool()} />);

    expect(await screen.findByText("Company")).toBeInTheDocument();
    expect(await screen.findByText("Acme")).toBeInTheDocument();
    expect(screen.getByText("interview")).toBeInTheDocument();
  });

  it("uses the custom date picker, NOT the native <input type=date>", async () => {
    const { container } = render(<ToolRenderer tool={tool()} />);
    await userEvent.click(
      await screen.findByRole("button", { name: /add entry/i }),
    );
    // The app-wide rule: no native date input anywhere.
    expect(container.querySelector('input[type="date"]')).toBeNull();
    // The custom DeadlinePopover trigger is present instead.
    expect(screen.getByText(/pick a date/i)).toBeInTheDocument();
  });

  it("adds a row: typing + Add calls addRecord with the entered data", async () => {
    (addRecord as ReturnType<typeof vi.fn>).mockImplementation((_id, data) =>
      Promise.resolve(record(11, data as object)),
    );
    render(<ToolRenderer tool={tool()} />);
    await userEvent.click(
      await screen.findByRole("button", { name: /add entry/i }),
    );

    await userEvent.type(screen.getAllByRole("textbox")[0], "Globex");
    await userEvent.click(screen.getByRole("button", { name: /^add$/i }));

    await waitFor(() => expect(addRecord).toHaveBeenCalledTimes(1));
    const [, data] = (addRecord as ReturnType<typeof vi.fn>).mock.calls[0];
    expect((data as Record<string, unknown>).company).toBe("Globex");
  });

  it("edits an existing row: Edit → change → Save calls updateRecord", async () => {
    (listRecords as ReturnType<typeof vi.fn>).mockResolvedValue([
      record(10, { company: "Acme", applied: "", status: "applied" }),
    ]);
    (updateRecord as ReturnType<typeof vi.fn>).mockImplementation(
      (_t, _r, data) => Promise.resolve(record(10, data as object)),
    );
    render(<ToolRenderer tool={tool()} />);
    await screen.findByText("Acme");

    await userEvent.click(screen.getByRole("button", { name: /edit/i }));
    const companyInput = screen.getAllByRole("textbox")[0];
    await userEvent.clear(companyInput);
    await userEvent.type(companyInput, "Initech");
    await userEvent.click(screen.getByRole("button", { name: /save/i }));

    await waitFor(() => expect(updateRecord).toHaveBeenCalledTimes(1));
    const [, recordId, data] = (updateRecord as ReturnType<typeof vi.fn>).mock
      .calls[0];
    expect(recordId).toBe(10);
    expect((data as Record<string, unknown>).company).toBe("Initech");
  });

  it("deletes a row: Delete calls deleteRecord", async () => {
    (listRecords as ReturnType<typeof vi.fn>).mockResolvedValue([
      record(10, { company: "Acme" }),
    ]);
    (deleteRecord as ReturnType<typeof vi.fn>).mockResolvedValue(undefined);
    render(<ToolRenderer tool={tool()} />);
    await screen.findByText("Acme");

    await userEvent.click(screen.getByRole("button", { name: /delete/i }));
    await waitFor(() => expect(deleteRecord).toHaveBeenCalledWith(1, 10));
  });

  it("preview mode is read-only: no network, no Add button", async () => {
    render(<ToolRenderer tool={tool()} preview />);
    expect(screen.getByText("Company")).toBeInTheDocument();
    expect(listRecords).not.toHaveBeenCalled();
    expect(
      screen.queryByRole("button", { name: /add entry/i }),
    ).not.toBeInTheDocument();
  });
});
