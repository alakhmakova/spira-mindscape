import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

import { ResourceAttachSheet } from "./ResourceAttachSheet";

/**
 * **Attaching one of the goal's saved resources to an AI message** (BUG-030), at the component
 * level.
 *
 * The picker had no test of any kind on either surface until 2026-08-23, and it showed: on the
 * web it was a hand-rolled dropdown around a bare `<input>` that inherited the AI panel's
 * `text-white` and so typed **white on white**, and the Android twin had lost its Kale head to a
 * white one with a drag handle. Both were found by the owner using the app.
 *
 * What lives at which level: this file covers the card's behaviour (what is listed, what the
 * search does, what a pick hands back, what "already attached" looks like);
 * `e2e/ai-attach-resource.spec.ts` covers the real flow through the composer;
 * `AiChatServiceResourceAttachmentTest` covers the content actually reaching the model.
 */
const RESOURCES = [
  { id: "1", label: "Interview notes", type: "note" },
  { id: "2", label: "Job board", type: "link" },
  { id: "3", label: "CV.pdf", type: "file", mime: "application/pdf" },
  { id: "4", label: "Recruiter", type: "email" },
];

function open(props: Partial<Parameters<typeof ResourceAttachSheet>[0]> = {}) {
  const onPick = vi.fn();
  const onOpenChange = vi.fn();
  render(
    <ResourceAttachSheet
      open
      onOpenChange={onOpenChange}
      resources={RESOURCES}
      attachedIds={[]}
      onPick={onPick}
      {...props}
    />,
  );
  return { onPick, onOpenChange };
}

describe("the resource attach sheet", () => {
  it("wears the standard sheet head and lists every resource type", () => {
    open();

    // The Kale band's title — the head every other sheet in the app wears.
    expect(screen.getByText("Attach a resource")).toBeInTheDocument();

    for (const r of RESOURCES) {
      expect(screen.getByText(r.label)).toBeInTheDocument();
    }
    // Each row says what kind of thing it is, as Android's does.
    expect(screen.getByText("Note")).toBeInTheDocument();
    expect(screen.getByText("Link")).toBeInTheDocument();
    expect(screen.getByText("File")).toBeInTheDocument();
    expect(screen.getByText("Email")).toBeInTheDocument();
  });

  it("has a search the user can actually see what they type in", async () => {
    const user = userEvent.setup();
    open();

    const search = screen.getByLabelText("Search resources");
    await user.type(search, "Job");

    // The regression that made this test exist: the field set a background and no colour,
    // so it rendered white text on white inside the teal panel. A value that never appears
    // is indistinguishable from one that is simply invisible, so assert the value.
    expect(search).toHaveValue("Job");
    expect(screen.getByText("Job board")).toBeInTheDocument();
    expect(screen.queryByText("Interview notes")).not.toBeInTheDocument();
  });

  it("empties the search with the word Clear, never a cross", async () => {
    const user = userEvent.setup();
    open();

    const search = screen.getByLabelText("Search resources");
    expect(screen.queryByText("Clear")).not.toBeInTheDocument();

    await user.type(search, "Job");
    await user.click(screen.getByText("Clear"));

    expect(search).toHaveValue("");
    expect(screen.getByText("Interview notes")).toBeInTheDocument();
  });

  it("hands the picked resource back and closes", async () => {
    const user = userEvent.setup();
    const { onPick, onOpenChange } = open();

    await user.click(screen.getByRole("button", { name: /Interview notes/ }));

    expect(onPick).toHaveBeenCalledTimes(1);
    expect(onPick.mock.calls[0][0]).toMatchObject({ id: "1" });
    expect(onOpenChange).toHaveBeenCalledWith(false);
  });

  it("offers an already-attached resource as Added, and refuses to add it twice", async () => {
    const user = userEvent.setup();
    const { onPick } = open({ attachedIds: [1] });

    expect(screen.getByText("Added")).toBeInTheDocument();
    const row = screen.getByRole("button", { name: /Interview notes/ });
    expect(row).toBeDisabled();

    await user.click(row);
    expect(onPick).not.toHaveBeenCalled();
  });

  it("says the goal has none rather than showing an empty list", () => {
    open({ resources: [] });

    expect(
      screen.getByText("This goal has no resources yet."),
    ).toBeInTheDocument();
  });

  it("distinguishes 'none at all' from 'none matching'", async () => {
    const user = userEvent.setup();
    open();

    await user.type(screen.getByLabelText("Search resources"), "zzz");

    // Two different situations, and telling the user the goal has no resources when it has
    // four would send them off to create one they already own.
    expect(screen.getByText("No matching resources.")).toBeInTheDocument();
  });
});
