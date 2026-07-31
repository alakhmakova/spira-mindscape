import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";

import { InlineText, InlineList } from "./Inline";

describe("InlineText readOnly", () => {
  it("does not enter edit mode on click (used in Options reorder mode)", () => {
    const onChange = vi.fn();
    render(
      <InlineText
        value="a strategy"
        onChange={onChange}
        ariaLabel="Edit strategy"
        readOnly
      />,
    );

    fireEvent.click(screen.getByRole("textbox", { name: "Edit strategy" }));
    // In edit mode a <textarea> replaces the display span; readOnly must keep the read view.
    expect(document.querySelector("textarea")).toBeNull();
    expect(onChange).not.toHaveBeenCalled();
  });
});

// These guard the "no silent truncation" contract: over-limit input is allowed on screen,
// flagged with a clear message, and NEVER saved/added (so it can't reach the server truncated).

describe("InlineText over-limit editing", () => {
  it("does not save an over-limit edit and keeps a clear message", () => {
    const onChange = vi.fn();
    render(
      <InlineText
        value="ok"
        onChange={onChange}
        ariaLabel="Edit strategy"
        maxLength={10}
        maxLengthLabel="Strategy"
      />,
    );

    fireEvent.click(screen.getByRole("textbox", { name: "Edit strategy" }));
    const textarea = screen.getByRole("textbox", { name: "Edit strategy" });
    fireEvent.change(textarea, { target: { value: "a".repeat(25) } });

    // Live message while editing (no truncation — the full text is still there).
    expect(screen.getByText(/too long/i)).toBeInTheDocument();
    expect((textarea as HTMLTextAreaElement).value).toHaveLength(25);

    // Blur tries to commit; over-limit must NOT save.
    fireEvent.blur(textarea);
    expect(onChange).not.toHaveBeenCalled();
  });

  it("saves once trimmed within the limit", () => {
    const onChange = vi.fn();
    render(
      <InlineText
        value="ok"
        onChange={onChange}
        ariaLabel="Edit strategy"
        maxLength={10}
      />,
    );

    fireEvent.click(screen.getByRole("textbox", { name: "Edit strategy" }));
    const textarea = screen.getByRole("textbox", { name: "Edit strategy" });
    fireEvent.change(textarea, { target: { value: "short" } });
    fireEvent.blur(textarea);

    expect(onChange).toHaveBeenCalledWith("short");
  });
});

describe("InlineList over-limit add", () => {
  it("blocks adding an over-limit item (button + Enter) and shows a message", () => {
    const onAdd = vi.fn();
    render(
      <InlineList
        items={[]}
        emptyHint="empty"
        placeholder="Add an action…"
        onAdd={onAdd}
        onUpdate={vi.fn()}
        onRemove={vi.fn()}
        maxLength={10}
        maxLengthLabel="Action"
      />,
    );

    const input = screen.getByPlaceholderText("Add an action…");
    fireEvent.change(input, { target: { value: "a".repeat(25) } });

    expect(screen.getByText(/too long/i)).toBeInTheDocument();
    const addBtn = screen.getByRole("button", { name: "Add" });
    expect(addBtn).toBeDisabled();

    fireEvent.click(addBtn);
    fireEvent.keyDown(input, { key: "Enter" });
    expect(onAdd).not.toHaveBeenCalled();
  });

  it("adds once within the limit", () => {
    const onAdd = vi.fn();
    render(
      <InlineList
        items={[]}
        emptyHint="empty"
        placeholder="Add an action…"
        onAdd={onAdd}
        onUpdate={vi.fn()}
        onRemove={vi.fn()}
        maxLength={10}
      />,
    );

    const input = screen.getByPlaceholderText("Add an action…");
    fireEvent.change(input, { target: { value: "fine" } });
    fireEvent.keyDown(input, { key: "Enter" });
    expect(onAdd).toHaveBeenCalledWith("fine");
  });
});
