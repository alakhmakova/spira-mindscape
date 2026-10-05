import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { GrowEndCard } from "./AiPanel";

/**
 * The closing card is the one place a session's record can be kept, so what it says about an
 * EMPTY record matters: the coach writes none when the provider fails mid-close, and none at all
 * when End was pressed and the session ended locally with no AI involved.
 */
describe("GrowEndCard", () => {
  const props = {
    proposals: 0,
    revising: false,
    onRevise: vi.fn(),
    onSave: vi.fn(),
    onDiscard: vi.fn(),
  };

  it("says plainly that no record was written, and will not save one", () => {
    render(<GrowEndCard {...props} memory={null} />);
    expect(screen.getByText(/no record was written/i)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /save memory/i })).toBeDisabled();
    // The revise field is the one useful thing left, and it used to be hidden in exactly this case.
    expect(
      screen.getByPlaceholderText(/tell the ai what to fix/i),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: /^close$/i }),
    ).toBeInTheDocument();
  });

  it("shows a real record and lets it be saved", () => {
    render(<GrowEndCard {...props} memory="Chose the freelance route." />);
    expect(screen.getByText(/freelance route/i)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /save memory/i })).toBeEnabled();
    expect(
      screen.getByRole("button", { name: /don't save/i }),
    ).toBeInTheDocument();
  });
});
