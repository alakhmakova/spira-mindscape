import { describe, it, expect, vi, beforeEach } from "vitest";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

import { InlineText, InlineList } from "./Inline";
import {
  InlineResourcesContextProvider,
  type InlineResourcesValue,
} from "./inline-resources";
import type { Resource } from "@/lib/spira/types";

const resources: Resource[] = [
  { id: "42", type: "link", title: "Job ad", url: "https://example.com/job" },
  { id: "9", type: "note", title: "Interview prep", body: "<p>x</p>" },
];

const ctx = {
  goalId: "goal-1",
  resources,
  createLinkResource: vi.fn(async () => "42"),
  openResource: vi.fn(),
} satisfies InlineResourcesValue;

function withResources(ui: React.ReactNode) {
  return (
    <InlineResourcesContextProvider value={ctx}>
      {ui}
    </InlineResourcesContextProvider>
  );
}

beforeEach(() => {
  ctx.createLinkResource.mockClear();
  ctx.openResource.mockClear();
});

describe("InlineText — attached resource links", () => {
  it("renders the resource's name as a link instead of the raw token", () => {
    render(
      withResources(
        <InlineText
          value="Read {{res:42}} before the call"
          onChange={vi.fn()}
          ariaLabel="Edit item"
        />,
      ),
    );

    expect(screen.getByText("Job ad")).toBeInTheDocument();
    expect(screen.queryByText(/\{\{res:42\}\}/)).not.toBeInTheDocument();
    expect(screen.getByText(/before the call/)).toBeInTheDocument();
  });

  it("opens the resource on click without entering edit mode", async () => {
    const user = userEvent.setup();
    render(
      withResources(
        <InlineText
          value="Read {{res:42}}"
          onChange={vi.fn()}
          ariaLabel="Edit item"
        />,
      ),
    );

    await user.click(screen.getByText("Job ad"));

    expect(ctx.openResource).toHaveBeenCalledWith("42");
    // Still the read view (a span), not the edit textarea.
    expect(screen.getByRole("textbox", { name: "Edit item" }).tagName).toBe(
      "SPAN",
    );
  });

  it("shows a neutral placeholder for a reference whose resource is gone", () => {
    render(
      withResources(
        <InlineText
          value="Read {{res:999}}"
          onChange={vi.fn()}
          ariaLabel="Edit item"
        />,
      ),
    );

    expect(screen.getByText("unavailable")).toBeInTheDocument();
  });

  it("shows the tag by resource NAME while editing, with an explanation", async () => {
    const user = userEvent.setup();
    render(
      withResources(
        <InlineText
          value="Read {{res:42}}"
          onChange={vi.fn()}
          ariaLabel="Edit item"
        />,
      ),
    );

    await user.click(screen.getByRole("textbox", { name: "Edit item" }));

    expect(screen.getByRole("textbox", { name: "Edit item" })).toHaveValue(
      "Read {{res:Job ad}}",
    );
    expect(screen.getByText(/delete the whole tag to detach/i)).toBeVisible();
  });

  it("stores the id form again when the name tag is committed", async () => {
    const onChange = vi.fn();
    render(
      withResources(
        <InlineText
          value="Read {{res:42}}"
          onChange={onChange}
          ariaLabel="Edit item"
        />,
      ),
    );

    fireEvent.click(screen.getByRole("textbox", { name: "Edit item" }));
    const textarea = screen.getByRole("textbox", { name: "Edit item" });
    fireEvent.change(textarea, {
      target: { value: "Read {{res:Job ad}} tonight" },
    });
    fireEvent.blur(textarea);

    expect(onChange).toHaveBeenCalledWith("Read {{res:42}} tonight");
  });

  it("degrades a tag naming an unknown resource to plain text", async () => {
    const onChange = vi.fn();
    render(
      withResources(
        <InlineText value="Read" onChange={onChange} ariaLabel="Edit item" />,
      ),
    );

    fireEvent.click(screen.getByRole("textbox", { name: "Edit item" }));
    const textarea = screen.getByRole("textbox", { name: "Edit item" });
    fireEvent.change(textarea, { target: { value: "Read {{res:Gone}}" } });
    fireEvent.blur(textarea);

    expect(onChange).toHaveBeenCalledWith("Read Gone");
  });
});

describe("InlineText — over-limit URL becomes a link resource", () => {
  const longUrl = `https://example.com/${"a".repeat(80)}`;

  it("offers the swap, then commits the value with the created resource's id", async () => {
    const onChange = vi.fn();
    render(
      withResources(
        <InlineText
          value="Apply"
          onChange={onChange}
          ariaLabel="Edit item"
          maxLength={60}
          maxLengthLabel="Strategy"
        />,
      ),
    );

    fireEvent.click(screen.getByRole("textbox", { name: "Edit item" }));
    const textarea = screen.getByRole("textbox", { name: "Edit item" });
    fireEvent.change(textarea, { target: { value: `Apply ${longUrl}` } });
    fireEvent.blur(textarea);

    expect(
      await screen.findByText(/too long to save here/i),
    ).toBeInTheDocument();
    // Nothing is saved until the user answers.
    expect(onChange).not.toHaveBeenCalled();

    fireEvent.click(
      screen.getByRole("button", { name: "Yes, save as a resource" }),
    );

    await waitFor(() =>
      expect(ctx.createLinkResource).toHaveBeenCalledWith(longUrl),
    );
    await waitFor(() =>
      expect(onChange).toHaveBeenCalledWith("Apply {{res:42}}"),
    );
  });

  it("keeps the value unsaved when the swap is declined", async () => {
    const onChange = vi.fn();
    render(
      withResources(
        <InlineText
          value="Apply"
          onChange={onChange}
          ariaLabel="Edit item"
          maxLength={60}
        />,
      ),
    );

    fireEvent.click(screen.getByRole("textbox", { name: "Edit item" }));
    const textarea = screen.getByRole("textbox", { name: "Edit item" });
    fireEvent.change(textarea, { target: { value: `Apply ${longUrl}` } });
    fireEvent.blur(textarea);

    fireEvent.click(
      await screen.findByRole("button", { name: "No, I'll shorten it" }),
    );

    expect(ctx.createLinkResource).not.toHaveBeenCalled();
    expect(onChange).not.toHaveBeenCalled();
  });
});

describe("InlineList — attach a resource from the ⋯ menu", () => {
  it("appends the picked resource's token to the item's text", async () => {
    const user = userEvent.setup();
    const onUpdate = vi.fn();
    render(
      withResources(
        <InlineList
          items={[{ id: "i1", text: "Call the recruiter" }]}
          emptyHint="none"
          placeholder="Add…"
          onAdd={vi.fn()}
          onUpdate={onUpdate}
          onRemove={vi.fn()}
          maxLength={500}
        />,
      ),
    );

    await user.click(screen.getByRole("button", { name: "Item actions" }));
    await user.click(screen.getByRole("menuitem", { name: "Attach resource" }));
    await user.click(
      await screen.findByRole("button", { name: /Interview prep/ }),
    );

    expect(onUpdate).toHaveBeenCalledWith("i1", "Call the recruiter {{res:9}}");
  });

  it("still deletes the item from the same menu", async () => {
    const user = userEvent.setup();
    const onRemove = vi.fn();
    render(
      withResources(
        <InlineList
          items={[{ id: "i1", text: "Call the recruiter" }]}
          emptyHint="none"
          placeholder="Add…"
          onAdd={vi.fn()}
          onUpdate={vi.fn()}
          onRemove={onRemove}
        />,
      ),
    );

    await user.click(screen.getByRole("button", { name: "Item actions" }));
    await user.click(screen.getByRole("menuitem", { name: "Delete" }));

    expect(onRemove).toHaveBeenCalledWith("i1");
  });
});
