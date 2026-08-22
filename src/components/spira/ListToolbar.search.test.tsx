import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

import { SectionSearchField, SectionSearchInput } from "./ListToolbar";

/**
 * **A search field empties itself with the word "Clear", never with a cross** (owner, 2026-08-22).
 *
 * The reason is the phone: the open search already carries an X that *closes* it, so a second
 * cross inside the field put two identical marks a few pixels apart and neither said which one
 * did what. A word can't be confused with the button beside it — and it renders identically to
 * an X as far as `getByRole("button")` is concerned, which is why this is asserted on the text.
 */
describe("clearing a search", () => {
  it("offers the word Clear once something is typed, and empties the field", async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    const { rerender } = render(
      <SectionSearchInput
        value=""
        onChange={onChange}
        placeholder="Search targets"
      />,
    );

    // Nothing typed: no control at all — an empty field has nothing to clear.
    expect(screen.queryByText("Clear")).not.toBeInTheDocument();

    rerender(
      <SectionSearchInput
        value="run"
        onChange={onChange}
        placeholder="Search targets"
      />,
    );
    await user.click(screen.getByText("Clear"));

    expect(onChange).toHaveBeenCalledWith("");
  });

  it("keeps the open field's only cross for closing the search", async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    const onClose = vi.fn();
    render(
      <SectionSearchField
        value="run"
        onChange={onChange}
        onClose={onClose}
        placeholder="Search targets"
      />,
    );

    // Clear empties without closing; the X closes. Two jobs, two shapes.
    await user.click(screen.getByText("Clear"));
    expect(onChange).toHaveBeenCalledWith("");
    expect(onClose).not.toHaveBeenCalled();

    await user.click(screen.getByRole("button", { name: "Close search" }));
    expect(onClose).toHaveBeenCalled();
  });
});
