import { type Page } from "@playwright/test";

/**
 * The measuring tools the three non-axe accessibility checks share — keyboard reach, reflow and
 * target size (BUG-023).
 *
 * **Why these are hand-written rather than another axe rule.** axe judges one element at a time
 * against the markup it can see. None of these three is a property of an element: "every control
 * can be reached" is a property of the whole tab sequence, "nothing scrolls sideways" is a
 * property of the layout at a width, and a target's size is a property of its rendered box. They
 * need a real browser at a real viewport, which is exactly what Playwright is.
 *
 * **Everything measures in one `page.evaluate` where it can.** A round trip per element turns a
 * page with 120 controls into 120 round trips; the walk below is the only part that cannot be done
 * in one, because pressing Tab is the browser's job.
 */

/**
 * What a keyboard or a finger is meant to be able to use.
 *
 * Roles as well as tags, because this app has plenty of both: `role="textbox"` spans for inline
 * editing (`Inline.tsx`), `role="tab"` for the view switcher, `role="menuitem"` inside every
 * dropdown.
 */
export const INTERACTIVE = [
  "a[href]",
  "button",
  'input:not([type="hidden"])',
  "select",
  "textarea",
  "summary",
  '[tabindex]:not([tabindex="-1"])',
  '[contenteditable=""]',
  '[contenteditable="true"]',
  '[role="button"]',
  '[role="link"]',
  '[role="menuitem"]',
  '[role="menuitemcheckbox"]',
  '[role="menuitemradio"]',
  '[role="tab"]',
  '[role="checkbox"]',
  '[role="radio"]',
  '[role="switch"]',
  '[role="textbox"]',
  '[role="combobox"]',
  '[role="slider"]',
].join(", ");

export type Probe = {
  /** The value written into `data-a11y-probe`, so a tab stop can be matched back to an element. */
  id: string;
  tag: string;
  role: string;
  /** Roughly what a screen reader would call it — enough to name it in a failure message. */
  name: string;
  width: number;
  height: number;
  /** An inline control inside running text; WCAG exempts those from target size. */
  inline: boolean;
  /** `left top` of its box, for reading a tab order against the visual one. */
  at: string;
};

/**
 * Tag every usable control with `data-a11y-probe` and describe it.
 *
 * Skips what is not actually on offer: `display:none` / `visibility:hidden`, a zero box, anything
 * disabled, and anything inside an `aria-hidden` or `inert` subtree — a closed sheet is all four.
 * **Opacity is deliberately not a reason to skip.** Several controls here sit at `opacity-0` until
 * their row is hovered or focused, and whether those can still be reached by Tab is precisely the
 * question being asked.
 */
export async function probeInteractive(page: Page): Promise<Probe[]> {
  return page.evaluate((selector: string) => {
    const nameOf = (el: Element): string => {
      const labelled = el.getAttribute("aria-labelledby");
      if (labelled) {
        const text = labelled
          .split(/\s+/)
          .map((id) => document.getElementById(id)?.textContent ?? "")
          .join(" ")
          .trim();
        if (text) return text;
      }
      const aria = el.getAttribute("aria-label");
      if (aria) return aria;
      const text = (el.textContent ?? "").replace(/\s+/g, " ").trim();
      if (text) return text;
      return (
        el.getAttribute("title") ??
        el.getAttribute("placeholder") ??
        el.getAttribute("alt") ??
        ""
      );
    };

    const out: Probe[] = [];
    let n = 0;
    for (const el of Array.from(document.querySelectorAll(selector))) {
      if (el.closest('[aria-hidden="true"], [inert]')) continue;
      if (
        el.matches(":disabled") ||
        el.getAttribute("aria-disabled") === "true"
      )
        continue;
      const style = getComputedStyle(el);
      if (style.display === "none" || style.visibility === "hidden") continue;
      const box = el.getBoundingClientRect();
      if (box.width === 0 && box.height === 0) continue;

      const id = `p${n++}`;
      el.setAttribute("data-a11y-probe", id);
      out.push({
        id,
        tag: el.tagName.toLowerCase(),
        role: el.getAttribute("role") ?? "",
        name: nameOf(el).slice(0, 60),
        width: Math.round(box.width),
        height: Math.round(box.height),
        inline: style.display.startsWith("inline"),
        at: `${Math.round(box.left)} ${Math.round(box.top)}`,
      });
    }
    return out;
  }, INTERACTIVE);
}

export type Stop = {
  /** `null` when focus landed on something that was not tagged — a late-rendered control. */
  id: string | null;
  name: string;
  tag: string;
  /** The computed properties a focus ring could be drawn with, while this element has focus. */
  style: string;
};

/**
 * Press Tab `presses` times and write down where focus landed each time.
 *
 * It does not try to detect "we have come round": focus leaving the last control goes to the
 * browser's own chrome, which the page cannot see, and the next Tab brings it back to the top — so
 * a fixed number of presses and a de-duplicated set is both simpler and more honest than guessing
 * where the cycle ends.
 */
export async function tabThrough(page: Page, presses = 120): Promise<Stop[]> {
  const stops: Stop[] = [];
  for (let i = 0; i < presses; i++) {
    await page.keyboard.press("Tab");
    const stop = await page.evaluate(() => {
      const el = document.activeElement as HTMLElement | null;
      if (!el || el === document.body) return null;
      const style = getComputedStyle(el);
      return {
        id: el.getAttribute("data-a11y-probe"),
        name: (
          el.getAttribute("aria-label") ||
          (el.textContent ?? "").replace(/\s+/g, " ").trim() ||
          el.getAttribute("placeholder") ||
          el.tagName
        ).slice(0, 60),
        tag: el.tagName.toLowerCase(),
        style: [
          style.outlineStyle,
          style.outlineWidth,
          style.outlineColor,
          style.boxShadow,
          style.borderColor,
          style.backgroundColor,
        ].join(" | "),
      };
    });
    if (stop) stops.push(stop);
  }
  return stops;
}

/**
 * Read the same properties back with nothing focused, so a focus indicator can be told from a
 * control that simply has a border and a shadow of its own.
 *
 * **This is the whole reason a ring cannot be judged from one measurement.** Every card in this
 * app has a `box-shadow`, so "box-shadow is not none while focused" is true of controls that show
 * nothing at all when you tab to them. The signal is the *difference*.
 */
export async function restingStyles(
  page: Page,
  ids: string[],
): Promise<Record<string, string>> {
  await page.evaluate(() => (document.activeElement as HTMLElement)?.blur());
  return page.evaluate((wanted: string[]) => {
    const out: Record<string, string> = {};
    for (const id of wanted) {
      const el = document.querySelector(`[data-a11y-probe="${id}"]`);
      if (!el) continue;
      const style = getComputedStyle(el);
      out[id] = [
        style.outlineStyle,
        style.outlineWidth,
        style.outlineColor,
        style.boxShadow,
        style.borderColor,
        style.backgroundColor,
      ].join(" | ");
    }
    return out;
  }, ids);
}

export type Overflow = {
  /** How many pixels the document scrolls sideways. Anything above 1 is a reflow failure. */
  sideways: number;
  /** The widest few elements sticking out past the right edge, for the failure message. */
  offenders: string[];
};

/**
 * **WCAG 1.4.10 Reflow**: at 320 CSS pixels wide, content must not need scrolling in two
 * directions at once.
 *
 * The check is the document's own horizontal scroll, not a hunt for wide elements: a `position:
 * fixed` drawer parked off-screen is wider than the viewport on purpose and scrolls nothing. The
 * offender list is only there to say *which* element to go and look at.
 */
export async function horizontalOverflow(page: Page): Promise<Overflow> {
  return page.evaluate(() => {
    const doc = document.documentElement;
    const sideways = doc.scrollWidth - doc.clientWidth;
    const offenders: string[] = [];
    if (sideways > 1) {
      const wide = Array.from(document.querySelectorAll("*"))
        .map((el) => ({ el, box: el.getBoundingClientRect() }))
        .filter(({ el, box }) => {
          if (box.width === 0) return false;
          if (box.right <= doc.clientWidth + 1) return false;
          const style = getComputedStyle(el);
          return style.position !== "fixed" && style.display !== "none";
        })
        .sort((a, b) => b.box.right - a.box.right)
        .slice(0, 5);
      for (const { el, box } of wide) {
        const classes = (el.getAttribute("class") ?? "").slice(0, 70);
        offenders.push(
          `${el.tagName.toLowerCase()}.${classes} → right ${Math.round(box.right)}px`,
        );
      }
    }
    return { sideways, offenders };
  });
}

/**
 * **WCAG 1.4.4 Resize text**: double the root font size and the page must keep working.
 *
 * Everything sized in `rem` grows with it and everything sized in `px` does not, which is the
 * point: a box pinned at `h-9` around type that has doubled is where text gets cut off.
 */
export async function setTextScale(page: Page, percent: number) {
  await page.addStyleTag({
    content: `html { font-size: ${percent}% !important; }`,
  });
  // One frame for layout to settle before anything is measured.
  await page.evaluate(
    () => new Promise((done) => requestAnimationFrame(() => done(null))),
  );
}

/**
 * Text cut off by a box that cannot grow.
 *
 * Deliberate truncation is excluded, because it is a design decision rather than a reflow failure:
 * a `line-clamp` (the three-line option card, with its own Show more) and a one-line ellipsis (the
 * resource head's scrolling name) both clip on purpose at any font size.
 */
export async function clippedText(page: Page): Promise<string[]> {
  return page.evaluate(() => {
    const out: string[] = [];
    for (const el of Array.from(document.querySelectorAll("*"))) {
      const style = getComputedStyle(el);
      if (style.overflowY !== "hidden" && style.overflow !== "hidden") continue;
      if (style.webkitLineClamp !== "none") continue;
      if (style.textOverflow === "ellipsis") continue;
      const hasOwnText = Array.from(el.childNodes).some(
        (node) => node.nodeType === 3 && (node.textContent ?? "").trim() !== "",
      );
      if (!hasOwnText) continue;
      if (el.scrollHeight > el.clientHeight + 2) {
        out.push(
          `${el.tagName.toLowerCase()}.${(el.getAttribute("class") ?? "").slice(0, 60)}` +
            ` → ${el.scrollHeight}px of text in ${el.clientHeight}px`,
        );
      }
    }
    return out.slice(0, 10);
  });
}
