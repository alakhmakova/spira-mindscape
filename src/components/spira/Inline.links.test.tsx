import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";

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

describe("InlineText paste length guard", () => {
  const pasteUrl = (url: string) => {
    // Enter edit mode (the display span → textarea), then paste a pure URL.
    fireEvent.click(screen.getByRole("textbox", { name: "Edit strategy" }));
    const textarea = screen.getByRole("textbox", { name: "Edit strategy" });
    fireEvent.paste(textarea, { clipboardData: { getData: () => url } });
  };

  it("refuses a pasted URL that would exceed maxLength — no commit, clear message", () => {
    const onChange = vi.fn();
    render(
      <InlineText
        value=""
        onChange={onChange}
        ariaLabel="Edit strategy"
        maxLength={30}
        maxLengthLabel="Strategy"
      />,
    );

    pasteUrl("https://example.com/" + "a".repeat(60)); // well over 30 chars

    expect(onChange).not.toHaveBeenCalled();
    expect(screen.getByText(/too long to save here/i)).toBeInTheDocument();
  });

  it("commits a pasted URL that fits within maxLength", () => {
    const onChange = vi.fn();
    render(
      <InlineText
        value=""
        onChange={onChange}
        ariaLabel="Edit strategy"
        maxLength={100}
      />,
    );

    pasteUrl("https://example.com/x");

    expect(onChange).toHaveBeenCalledWith("https://example.com/x");
    expect(
      screen.queryByText(/too long to save here/i),
    ).not.toBeInTheDocument();
  });
});
