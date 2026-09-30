import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

import { MarqueeText, ResourceFullScreen, ResourceHead } from "./ResourceHead";
import { Copy, Download, Trash2 } from "./icons";

// ── The viewport, which is what picks icons or a kebab ───────────────────────

const REAL_WIDTH = window.innerWidth;

function viewport(width: number) {
  Object.defineProperty(window, "innerWidth", {
    configurable: true,
    value: width,
  });
}

afterEach(() => viewport(REAL_WIDTH));

function head(extra: Parameters<typeof ResourceHead>[0]["actions"] = []) {
  const onBack = vi.fn();
  const onCopy = vi.fn();
  render(
    <ResourceHead
      title="Backend Developer"
      onBack={onBack}
      actions={[
        {
          key: "copy",
          label: "Copy as plain text",
          icon: <Copy />,
          onClick: onCopy,
        },
        ...extra,
      ]}
    />,
  );
  return { onBack, onCopy };
}

describe("ResourceHead", () => {
  it("closes from the cross at the end of the action group", async () => {
    viewport(1280);
    const { onBack } = head();

    await userEvent.click(screen.getByRole("button", { name: "Close" }));

    expect(onBack).toHaveBeenCalledTimes(1);
    // Over a page there is no disc on the left at all (owner, 2026-09-24): a cross in a circle
    // there said nothing about where it led, and the way out is on the right with the actions.
    expect(screen.queryByRole("button", { name: "Back" })).toBeNull();
  });

  it("puts the cross LAST, after the actions", () => {
    viewport(1280);
    head([
      { key: "delete", label: "Delete", icon: <Trash2 />, onClick: vi.fn() },
    ]);

    const buttons = screen.getAllByRole("button").map((b) => b.ariaLabel);

    // Everything before it acts ON the resource; this one ends the panel.
    expect(buttons).toEqual(["Copy as plain text", "Delete", "Close"]);
  });

  it("adds the chevron only once the panel IS the screen", () => {
    viewport(1280);
    const { unmount } = render(
      <ResourceHead title="A note" onBack={vi.fn()} />,
    );
    expect(screen.queryByRole("button", { name: "Back" })).toBeNull();
    unmount();

    render(
      <ResourceFullScreen.Provider value={true}>
        <ResourceHead title="A note" onBack={vi.fn()} />
      </ResourceFullScreen.Provider>,
    );

    // Full width there is nothing beside it, so the chevron says where it goes back to —
    // a different promise from the cross, which simply shuts the panel.
    expect(screen.getByRole("button", { name: "Back" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Close" })).toBeInTheDocument();
  });

  it("a phone is always full screen, whatever the panel says", () => {
    viewport(400);
    render(<ResourceHead title="A note" onBack={vi.fn()} />);

    expect(screen.getByRole("button", { name: "Back" })).toBeInTheDocument();
    // And no cross: the chevron is already there, and a phone has no room for both.
    expect(screen.queryByRole("button", { name: "Close" })).toBeNull();
  });

  it("shows every action as its own button on a laptop", async () => {
    viewport(1280);
    const { onCopy } = head([
      { key: "delete", label: "Delete", icon: <Trash2 />, onClick: vi.fn() },
    ]);

    await userEvent.click(
      screen.getByRole("button", { name: "Copy as plain text" }),
    );

    expect(onCopy).toHaveBeenCalledTimes(1);
    expect(screen.getByRole("button", { name: "Delete" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Actions" })).toBeNull();
    // They are one segmented control, not loose glyphs on the band: white buttons inside a
    // hairline that has to show against the teal (owner, 2026-09-24).
    const group = screen.getByRole("button", { name: "Delete" }).parentElement;
    expect(group?.className).toContain("border-[#ABABAB]");
    expect(group?.className).toContain("bg-white");
  });

  it("folds every action into one kebab on a phone", async () => {
    viewport(400);
    const onDelete = vi.fn();
    head([
      { key: "delete", label: "Delete", icon: <Trash2 />, onClick: onDelete },
    ]);

    // Nothing but the kebab: four discs do not fit beside a name on a phone.
    expect(
      screen.queryByRole("button", { name: "Copy as plain text" }),
    ).toBeNull();
    await userEvent.click(screen.getByRole("button", { name: "Actions" }));

    // The same actions, now worded.
    await userEvent.click(
      await screen.findByRole("menuitem", { name: "Delete" }),
    );
    expect(onDelete).toHaveBeenCalledTimes(1);
  });

  it("draws nothing for an action the resource does not have", () => {
    viewport(1280);
    render(
      <ResourceHead
        title="A link"
        onBack={vi.fn()}
        actions={[
          // `PreviewBody` describes every action for every type and leaves the handler off the
          // ones that do not apply — a download button on a link would do nothing at all.
          { key: "download", label: "Download", icon: <Download /> },
        ]}
      />,
    );

    expect(screen.queryByRole("button", { name: "Download" })).toBeNull();
    // The way out is not an action the caller supplies, so it is there regardless.
    expect(screen.getByRole("button", { name: "Close" })).toBeInTheDocument();
  });

  it("renders the caller's own editor in the title's place when it has one", () => {
    render(
      <ResourceHead title="fallback" onBack={vi.fn()}>
        <input aria-label="Vacancy name" defaultValue="Backend Developer" />
      </ResourceHead>,
    );

    expect(screen.getByLabelText("Vacancy name")).toBeInTheDocument();
    expect(screen.queryByText("fallback")).toBeNull();
  });
});

// ── The scrolling name ───────────────────────────────────────────────────────

/**
 * jsdom lays nothing out, so both measurements read 0 and the marquee can never turn itself on
 * by accident. Each test states the two widths it is about.
 */
function measurements({ text, box }: { text: number; box: number }) {
  Object.defineProperty(HTMLElement.prototype, "scrollWidth", {
    configurable: true,
    value: text,
  });
  Object.defineProperty(HTMLElement.prototype, "clientWidth", {
    configurable: true,
    value: box,
  });
}

describe("MarqueeText", () => {
  beforeEach(() => measurements({ text: 0, box: 0 }));

  it("sits still when the name fits", () => {
    measurements({ text: 180, box: 300 });
    const { container } = render(<MarqueeText>Short name</MarqueeText>);

    expect(container.querySelector("span")).not.toHaveAttribute("style");
  });

  it("scrolls exactly its own overflow when the name does not fit", () => {
    measurements({ text: 700, box: 300 });
    const { container } = render(
      <MarqueeText>A name far too long for the head it is in</MarqueeText>,
    );

    const style = container.querySelector("span")?.getAttribute("style") ?? "";
    // It stops with the last letter at the edge, rather than scrolling off it.
    expect(style).toContain("--marquee-shift: -400px");
    expect(style).toContain("resource-title-marquee");
  });

  it("holds the words inside it to one line, whatever they set on themselves", () => {
    measurements({ text: 700, box: 300 });
    // `InlineText` sets `whitespace-pre-wrap` on its own display span; without this the name
    // simply wrapped to three lines and there was nothing left to scroll.
    const { container } = render(<MarqueeText>Long name</MarqueeText>);

    expect(container.querySelector("span")?.className).toContain(
      "[&_*]:whitespace-nowrap",
    );
  });
});
