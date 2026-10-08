import { expect, type Page } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";
import { appendFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

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

/* ── axe ─────────────────────────────────────────────────────────────────────────── */

/** The rule tags that make up WCAG 2.1 level A + AA. */
export const WCAG_21_AA = ["wcag2a", "wcag2aa", "wcag21a", "wcag21aa"];

/**
 * `A11Y_RECORD=1 npx playwright test <spec> --retries=0` writes what every surface currently has
 * to `e2e/.a11y-baseline.ndjson` instead of failing, so surfaces can be added without reading two
 * dozen numbers out of failure messages one run at a time. It is a recording mode and nothing
 * else: **the run it produces proves nothing** — the numbers go into the spec's own `ACCEPTED` and
 * the suite is re-run normally before anything is believed.
 */
export const RECORD = process.env.A11Y_RECORD === "1";
const BASELINE_LOG = path.join(
  path.dirname(fileURLToPath(import.meta.url)),
  ".a11y-baseline.ndjson",
);

/**
 * Run axe over what is on screen and hold this surface to what it is allowed to have.
 *
 * `within` scopes the scan to an overlay. **An overlay is measured on its own, not with the page
 * behind it**, and that is a correctness fix rather than a convenience: a modal marks the page
 * behind it `aria-hidden` and axe then skips it, so whether the chrome behind is counted depends
 * on whether the hiding had happened when the scan ran — and on what else was open a moment
 * earlier. Measured on this suite: one sheet read 0 in one run and 6 in the next, with nothing in
 * the app changed.
 */
/**
 * What a surface is allowed to have: rule id → **element signature** → how many nodes.
 *
 * **It used to be rule id → a single number, and that number hid real defects.** The owner asked
 * why no test had ever flagged the "Add target" button, which puts white on `#F45D48` at 3.23:1;
 * axe had been reporting it on every run. The goal page's line read `{ "color-contrast": 8 }`,
 * axe found 5, `5 <= 8` and the suite went green — and under a ceiling of 8 one defect can be
 * swapped for another without anything failing. A count records *how many*; only a signature
 * records *which*.
 */
export type AcceptedViolations = Record<
  string,
  Record<string, Record<string, number>>
>;

/**
 * An element's identity: **its own tag and its own classes**, read off the markup axe reports.
 *
 * **Not the CSS path axe computes**, which was the first attempt and did not survive a week. That
 * path is the *shortest selector that is unique in the page as it stands*, so it changes when
 * other elements change: the Timeline's muted span was recorded as
 * `.gap-1\.5.items-center.flex > span` and later reported as `.gap-1\.5.items-center > span` —
 * same element, same defect, a line of baseline retired for nothing. Stripping `:nth-child` and
 * Radix's generated ids fixed two symptoms of that and left the cause.
 *
 * The opening tag depends on the element alone. `<span class="font-medium text-[12px]">` is
 * `span.font-medium.text-[12px]` wherever it sits and whatever is beside it. Classes are sorted so
 * that re-ordering a `className` string is not a change, and framework-generated ones are dropped.
 *
 * **A restyle does expire the line, and that is right**: changing an element's classes is exactly
 * when its contrast wants looking at again. Several nodes can share a signature — three identical
 * muted spans down the Timeline do — which is why the baseline stores a count against it.
 */
function signature(node: { target: unknown[]; html?: string }): string {
  const html = node.html ?? "";
  const tag = /^<([a-z][a-z0-9-]*)/i.exec(html)?.[1]?.toLowerCase();
  if (tag) {
    const classes = (/\sclass="([^"]*)"/i.exec(html)?.[1] ?? "")
      .split(/\s+/)
      .filter((c) => c && !/^(radix-|_r_)/.test(c))
      .sort();
    return classes.length ? `${tag}.${classes.join(".")}` : tag;
  }
  // No markup to go on — fall back to the last step of the path, cleaned the old way.
  const raw = node.target
    .map((t) => (typeof t === "string" ? t : JSON.stringify(t)))
    .join(" ");
  const steps = raw
    .split(">")
    .map((s) =>
      s
        .replace(/:nth-child\(\d+\)/g, "")
        .replace(/\[[^\]]*(?:radix|_r_)[^\]]*\]/g, "")
        .trim(),
    )
    .filter(Boolean);
  return steps[steps.length - 1] ?? raw;
}

export async function axeScan(
  page: Page,
  surface: string,
  accepted: AcceptedViolations,
  within?: string,
) {
  // **Let the surface stop moving first.** axe reports a node whose background it cannot resolve
  // as *incomplete* rather than as a violation, and a card that is still fading in is exactly
  // that — so the same dialog counted 0 in one run and 1 in the next, on unchanged code, three
  // times in this suite (the Reality menu's red Delete, then both delete confirms' `.shadow`).
  // Waiting for the animations rather than for a fixed delay keeps it fast and removes the whole
  // class; the race guard is for an animation that never ends, like a spinner.
  await page.evaluate(async (sel) => {
    const root = sel ? document.querySelector(sel) : document.body;
    if (!root) return;
    const settled = Promise.all(
      root
        .getAnimations({ subtree: true })
        .map((a) => a.finished.catch(() => {})),
    );
    await Promise.race([
      settled,
      new Promise((resolve) => setTimeout(resolve, 1000)),
    ]);
  }, within ?? null);

  const builder = new AxeBuilder({ page }).withTags(WCAG_21_AA);
  const { violations } = await (
    within ? builder.include(within) : builder
  ).analyze();

  // rule id → signature → how many nodes carry it right now.
  const found: Record<string, Record<string, number>> = {};
  for (const violation of violations) {
    const bySignature: Record<string, number> = (found[violation.id] ??= {});
    for (const node of violation.nodes) {
      const key = signature(node);
      bySignature[key] = (bySignature[key] ?? 0) + 1;
    }
  }

  if (RECORD) {
    // Printed in the shape ACCEPTED takes, so a re-record is a paste rather than a transcription.
    // The markup goes alongside, because a selector names an element and only the markup says
    // which component it is.
    const markup: Record<string, string> = {};
    for (const violation of violations) {
      for (const node of violation.nodes) {
        markup[`${violation.id} ${signature(node)}`] ??= (
          node.html ?? ""
        ).slice(0, 180);
      }
    }
    appendFileSync(
      BASELINE_LOG,
      JSON.stringify({ surface, found, markup }) + "\n",
    );
    return;
  }

  const allowed = accepted[surface] ?? {};
  // A regression is an element that is NOT on this surface's list, or one that is on it but has
  // multiplied. Fewer than recorded never fails — fixing something must not break the suite.
  const regressions: string[] = [];
  for (const [id, bySignature] of Object.entries(found)) {
    for (const [key, count] of Object.entries(bySignature)) {
      const ceiling = allowed[id]?.[key] ?? 0;
      if (count > ceiling) {
        regressions.push(
          `${id} x${count}${ceiling ? ` (recorded ${ceiling})` : " (not recorded)"} — ${key}`,
        );
      }
    }
  }

  // The report names the rule, the element and its help text, so a red run says what to go and
  // fix rather than only that something regressed.
  const detail = violations
    .map(
      (v) =>
        `  ${v.id} (${v.impact}) x${v.nodes.length} — ${v.help}\n` +
        v.nodes.map((n) => `      ${signature(n)}`).join("\n"),
    )
    .join("\n");

  expect(
    regressions,
    `${surface}: accessibility violations beyond what is recorded in ACCEPTED.\n` +
      regressions.map((r) => `  ${r}`).join("\n") +
      `\n  — everything axe reported here —\n${detail}\n` +
      `  Re-record with A11Y_RECORD=1 if these are genuinely accepted.`,
  ).toEqual([]);
}
