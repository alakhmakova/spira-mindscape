/**
 * **Publishes a viewport height the on-screen keyboard cannot move** — the number every sheet
 * is sized from.
 *
 * ## The problem this exists for
 *
 * `index.html` declares `interactive-widget=resizes-content`, which is the right call: with it
 * the keyboard shrinks the *layout* viewport, so a bottom-anchored sheet sits above the keyboard
 * instead of behind it. The cost is that **every viewport unit shrinks with the keyboard** —
 * `vh`, `dvh` and `svh` alike, because all three are percentages of that same viewport.
 *
 * So `h-[92vh]` does not mean "92 % of the screen". It means "92 % of whatever is left", and on
 * the owner's phone (2026-08-28) a keyboard leaves about 430 px of an 888 px screen: the sheet
 * lands at ~395 px, **less than half the screen**, which is exactly what was reported for the
 * chat drawer, the key sheet and the New-target sheet. The sheets that never misbehaved — filter
 * and sort — are precisely the ones with no field to type in.
 *
 * This also explains why four rounds of automated checks proved nothing: a headless browser has
 * no keyboard, so there every unit measures a correct 92 %.
 *
 * ## What it publishes
 *
 * `--app-vh`: one percent of the **keyboard-free** viewport height, in px, so
 * `calc(var(--app-vh) * 92)` reads as "92 % of the screen" and stays put while typing.
 *
 * A keyboard can only be up while something is focused that you type into, so that — and not a
 * pixel threshold — is what decides whether a shrink is believed. Nothing focused: the current
 * height is the truth, so a genuinely smaller window (a resize, split-screen, a folding phone)
 * is followed down. Focused: the largest height seen holds, because a keyboard can only ever
 * make the viewport smaller. The maximum is also reset when the width changes, which is what a
 * rotation looks like.
 *
 * Sheets pair it with a `min(…, 100dvh)` guard (see `.sheet-h-*` in `styles.css`) so that while
 * the keyboard IS open the sheet fills the space above it rather than running off the top.
 *
 * ## Why a blur must NOT republish (BUG-064, 2026-08-29)
 *
 * There used to be a `focusout` listener here, on the reasoning that a field losing focus is the
 * keyboard going away. It is not — it is the keyboard *starting* to go away, and for the next
 * couple of hundred milliseconds the viewport is still the small one. So `focusout` ran
 * `publish()` at the one instant when `couldBeTyping()` had just turned false while
 * `window.innerHeight` was still keyboard-sized, and republished **the keyboard's height as the
 * screen's**. Every sheet then shrank, mid-tap.
 *
 * That is what made Confidence look broken. Measured (412x430, the room left above the keyboard,
 * with the title focused):
 *
 * | | Events the button saw | Result |
 * |---|---|---|
 * | with the `focusout` publish | `pointerdown touchstart pointerup touchend mousedown` | **no `click` at all** |
 * | without it | `… mousedown click` | the value is set |
 *
 * `mousedown` blurred the field, the blur republished `--app-vh` (7.8 → 4.3), the sheet lost
 * 34 px, and being bottom-anchored it moved everything inside it down — so by `mouseup` the
 * button was no longer under the finger and Chrome suppressed the `click`. From the user's side:
 * "новая оценка не ставится, а просто закрывается клавиатура".
 *
 * The listener was not merely early, it was useless: `stableHeight` already holds the
 * pre-keyboard maximum while a field is focused, so a keyboard-shrunk height can never be the
 * one that sticks — the case it was written for cannot arise. All it could ever do was shrink.
 * The keyboard closing resizes the layout viewport (`interactive-widget=resizes-content`), and
 * `resize` is what publishes.
 */

let stableHeight = 0;
let stableWidth = 0;

/**
 * Whether a keyboard could be up at all — i.e. whether something is focused that one would
 * type into.
 *
 * <p>This is what keeps the published height from going stale. Taking the maximum alone would
 * mean the value could only ever grow, so every genuine *reduction* that is not a keyboard —
 * a desktop window dragged shorter, devtools docked to the bottom, Android split-screen, a
 * foldable folding — would leave `--app-vh` too large and drop every sheet through to the
 * `100dvh` clamp, i.e. full height instead of 92 %.
 *
 * With nothing focused there is no keyboard, so the current height is simply the truth and is
 * taken as it is, shrinking included. While a field IS focused the maximum holds, which is the
 * one case the whole module exists for.
 */
function couldBeTyping(): boolean {
  const el = document.activeElement as HTMLElement | null;
  if (!el) return false;
  const tag = el.tagName;
  return tag === "INPUT" || tag === "TEXTAREA" || el.isContentEditable;
}

function publish(): void {
  const height = window.innerHeight;
  const width = window.innerWidth;
  if (!height) return;

  // A rotation is a different screen, not a keyboard — start again.
  if (width !== stableWidth) {
    stableWidth = width;
    stableHeight = 0;
  }
  if (height < stableHeight && couldBeTyping()) return;
  if (height === stableHeight) return;

  stableHeight = height;
  document.documentElement.style.setProperty("--app-vh", `${height / 100}px`);
}

/**
 * Starts publishing `--app-vh`. Safe to call more than once and outside a browser (SSR, a unit
 * test with no DOM), where it does nothing.
 */
export function trackViewportHeight(): void {
  if (typeof window === "undefined" || typeof document === "undefined") return;
  publish();
  window.addEventListener("resize", publish);
  window.addEventListener("orientationchange", publish);
  window.visualViewport?.addEventListener("resize", publish);
}
