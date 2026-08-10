/**
 * Icons from the owner's collection — `specs/icons.md`, the **primary** icon source (CLAUDE.md →
 * UI conventions → "Icons & emoji"). Lucide is only a fallback, and only with the owner's
 * go-ahead, so anything available here should be taken from here.
 *
 * These are drop-in replacements for `lucide-react` components: same `className` prop, same
 * `currentColor` fill, so the caller's text colour and sizing classes keep working. The
 * collection's own markup is 16×16 with `fill="currentColor"`; Linear's rendering chrome
 * (`class="gwb_…"`, `nv_id`, `width="1em"`, …) is deliberately not copied.
 *
 * The Android twins live in `ui/icons/SpiraIcons.kt` — keep the two surfaces on the same glyphs.
 */

type IconProps = { className?: string };

/** `specs/icons.md` → *Filled smile*. The Options card's "good idea" rating. */
export function SmileFilled({ className }: IconProps) {
  return (
    <svg
      viewBox="0 0 16 16"
      fill="currentColor"
      aria-hidden="true"
      focusable="false"
      className={className}
    >
      <path
        fillRule="evenodd"
        clipRule="evenodd"
        d="M8 .5a7.5 7.5 0 100 15 7.5 7.5 0 000-15zm-2.5 4a1 1 0 100 2 1 1 0 000-2zm4 1a1 1 0 112 0 1 1 0 01-2 0zM6.155 9.635a.75.75 0 10-1.31.73C5.465 11.48 6.639 12.25 8 12.25c1.361 0 2.534-.77 3.155-1.885a.75.75 0 00-1.31-.73c-.377.678-1.07 1.115-1.845 1.115-.775 0-1.468-.437-1.845-1.115z"
      />
    </svg>
  );
}

/** `specs/icons.md` → *Filled sad*. The Options card's "didn't work" rating. */
export function FrownFilled({ className }: IconProps) {
  return (
    <svg
      viewBox="0 0 16 16"
      fill="currentColor"
      aria-hidden="true"
      focusable="false"
      className={className}
    >
      <path
        fillRule="evenodd"
        clipRule="evenodd"
        d="M8 .5a7.5 7.5 0 100 15 7.5 7.5 0 000-15zm-2.5 4a1 1 0 100 2 1 1 0 000-2zm4 1a1 1 0 112 0 1 1 0 01-2 0zm.345 6.365a.75.75 0 101.31-.73C10.535 10.02 9.361 9.25 8 9.25c-1.361 0-2.534.77-3.155 1.885a.75.75 0 001.31.73c.377-.678 1.07-1.115 1.845-1.115.775 0 1.468.437 1.845 1.115z"
      />
    </svg>
  );
}

/** `specs/icons.md` → *Kebab*. Verbatim from the collection: a vertical ⋮. */
const KEBAB_PATH =
  "M9.5 2.5a1.5 1.5 0 11-3 0 1.5 1.5 0 013 0zm0 5.5a1.5 1.5 0 11-3 0 1.5 1.5 0 013 0zM8 15a1.5 1.5 0 100-3 1.5 1.5 0 000 3z";

/**
 * The actions menu, **horizontal** ⋯ — the default.
 *
 * The collection ships only the vertical ⋮; this is that same glyph turned a quarter-turn rather
 * than a second, hand-drawn icon. Its dots sit at y = 2.5 / 8 / 13.5, symmetric about the box's
 * centre, so rotating about (8, 8) lands them at x = 2.5 / 8 / 13.5.
 */
export function Kebab({ className }: IconProps) {
  return (
    <svg
      viewBox="0 0 16 16"
      fill="currentColor"
      aria-hidden="true"
      focusable="false"
      className={className}
    >
      <path d={KEBAB_PATH} transform="rotate(90 8 8)" />
    </svg>
  );
}

/** The actions menu as the collection draws it, ⋮ — for rows with a fixed control column. */
export function KebabVertical({ className }: IconProps) {
  return (
    <svg
      viewBox="0 0 16 16"
      fill="currentColor"
      aria-hidden="true"
      focusable="false"
      className={className}
    >
      <path d={KEBAB_PATH} />
    </svg>
  );
}
