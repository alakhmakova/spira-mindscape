import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";

import { InlineText } from "./Inline";

describe("InlineText URL auto-linking", () => {
  it("renders a URL in the text as a plain clickable link (opens in a new tab)", () => {
    render(
      <InlineText
        value="Docs at https://example.com/x here"
        onChange={vi.fn()}
        ariaLabel="Edit item"
      />,
    );
    const link = screen.getByRole("link", { name: "https://example.com/x" });
    expect(link).toHaveAttribute("href", "https://example.com/x");
    expect(link).toHaveAttribute("target", "_blank");
    expect(link).toHaveAttribute("rel", "noopener noreferrer");
    // The surrounding text is still shown.
    expect(screen.getByText(/Docs at/)).toBeInTheDocument();
    expect(screen.getByText(/here/)).toBeInTheDocument();
  });

  it("does not render a javascript: pseudo-URL as a clickable link (no http/https match)", () => {
    render(
      <InlineText
        value="javascript:alert(1)"
        onChange={vi.fn()}
        ariaLabel="Edit item"
      />,
    );
    expect(screen.queryByRole("link")).not.toBeInTheDocument();
  });

  it("shows the placeholder when empty", () => {
    render(
      <InlineText
        value=""
        onChange={vi.fn()}
        ariaLabel="Edit item"
        placeholder="Add something…"
      />,
    );
    expect(screen.getByText("Add something…")).toBeInTheDocument();
    expect(screen.queryByRole("link")).not.toBeInTheDocument();
  });
});
