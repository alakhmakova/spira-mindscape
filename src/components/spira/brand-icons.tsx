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
 * Android no longer has twins for these: it moved to **Gravity UI** for every glyph on
 * 2026-08-14 (`ui/icons/SpiraIcons.kt`), and the web is the half of that move still to do.
 * Until it happens the two surfaces are deliberately apart — see CLAUDE.md.
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

/**
 * The **sort / filter** trigger's chevron — a small solid wedge, not a stroked V.
 *
 * The owner's glyph (2026-08-13). It is the only chevron a toolbar trigger uses; navigation
 * chevrons stay on Lucide's stroked `ChevronDown`, because those are marks in their own right
 * rather than a full-stop after a word. Android draws this one as Gravity's `caret-down`
 * (`SpiraIcons.CaretDown`), which is the same shape from a different set.
 */
export function ChevronDownSolid({ className }: IconProps) {
  return (
    <svg
      viewBox="0 0 16 16"
      fill="currentColor"
      aria-hidden="true"
      focusable="false"
      className={className}
    >
      <path d="M8.53 10.77a.75.75 0 01-1.06 0L3.977 7.277a.75.75 0 01.53-1.28h6.986a.75.75 0 01.53 1.28L8.53 10.77z" />
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

/* ── Iconoir (MIT) — stroked, 24×24, the set Android now uses ───────────────── */

/** A stroked 24×24 Iconoir glyph. Weight 2 matches Android's `iconoir()` builder. */
function Iconoir({ className, d }: IconProps & { d: string[] }) {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={2}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      focusable="false"
      className={className}
    >
      {d.map((path, i) => (
        <path key={i} d={path} />
      ))}
    </svg>
  );
}

/**
 * Iconoir `internet` — an **attached resource of kind link**. Deliberately not the chain: a chain
 * says "a link to somewhere", and this marks a saved thing of that type. Every other link use
 * takes {@link LinkFilled}.
 */
export function LinkGlobe({ className }: IconProps) {
  return (
    <Iconoir
      className={className}
      d={[
        "M12 22C17.5228 22 22 17.5228 22 12C22 6.47715 17.5228 2 12 2C6.47715 2 2 6.47715 2 12C2 17.5228 6.47715 22 12 22Z",
        "M2.5 12.5L8 14.5L7 18L8 21",
        "M17 20.5L16.5 18L14 17V13.5L17 12.5L21.5 13",
        "M19 5.5L18.5 7L15 7.5V10.5L17.5 9.5H19.5L21.5 10.5",
        "M2.5 10.5L5 8.5L7.5 8L9.5 5L8.5 3",
      ]}
    />
  );
}

/** Iconoir `open-new-window` — a raw URL that leaves the app. */
export function OpenNewWindow({ className }: IconProps) {
  return (
    <Iconoir
      className={className}
      d={[
        "M21 3L15 3M21 3L12 12M21 3V9",
        "M21 13V19C21 20.1046 20.1046 21 19 21H5C3.89543 21 3 20.1046 3 19V5C3 3.89543 3.89543 3 5 3H11",
      ]}
    />
  );
}

/** Iconoir `sparks` (the two-star mark) — Start GROW session, the same glyph Android uses. */
export function GrowSparkles({ className }: IconProps) {
  return (
    <Iconoir
      className={className}
      d={[
        "M8 15C12.8747 15 15 12.949 15 8C15 12.949 17.1104 15 22 15C17.1104 15 15 17.1104 15 22C15 17.1104 12.8747 15 8 15Z",
        "M2 6.5C5.13376 6.5 6.5 5.18153 6.5 2C6.5 5.18153 7.85669 6.5 11 6.5C7.85669 6.5 6.5 7.85669 6.5 11C6.5 7.85669 5.13376 6.5 2 6.5Z",
      ]}
    />
  );
}

/* ── Phosphor (MIT) — filled, 256×256 ───────────────────────────────────────── */

/** A filled 256×256 Phosphor glyph. */
function Phosphor({ className, d }: IconProps & { d: string }) {
  return (
    <svg
      viewBox="0 0 256 256"
      fill="currentColor"
      aria-hidden="true"
      focusable="false"
      className={className}
    >
      <path d={d} />
    </svg>
  );
}

/** Phosphor `link` (fill) — every link use except an attached resource. */
export function LinkFilled({ className }: IconProps) {
  return (
    <Phosphor
      className={className}
      d="M165.66,90.34a8,8,0,0,1,0,11.32l-64,64a8,8,0,0,1-11.32-11.32l64-64A8,8,0,0,1,165.66,90.34ZM215.6,40.4a56,56,0,0,0-79.2,0L106.34,70.45a8,8,0,0,0,11.32,11.32l30.06-30a40,40,0,0,1,56.57,56.56l-30.07,30.06a8,8,0,0,0,11.31,11.32L215.6,119.6a56,56,0,0,0,0-79.2ZM138.34,174.22l-30.06,30.06a40,40,0,1,1-56.56-56.57l30.05-30.05a8,8,0,0,0-11.32-11.32L40.4,136.4a56,56,0,0,0,79.2,79.2l30.06-30.07a8,8,0,0,0-11.32-11.31Z"
    />
  );
}

/** Phosphor `lock` (fill) — progress is pinned. */
export function LockFilled({ className }: IconProps) {
  return (
    <Phosphor
      className={className}
      d="M128,112a28,28,0,0,0-8,54.83V184a8,8,0,0,0,16,0V166.83A28,28,0,0,0,128,112Zm0,40a12,12,0,1,1,12-12A12,12,0,0,1,128,152Zm80-72H176V56a48,48,0,0,0-96,0V80H48A16,16,0,0,0,32,96V208a16,16,0,0,0,16,16H208a16,16,0,0,0,16-16V96A16,16,0,0,0,208,80ZM96,56a32,32,0,0,1,64,0V80H96ZM208,208H48V96H208V208Z"
    />
  );
}

/** Phosphor `lock-open` (fill) — progress can be nudged. */
export function LockOpenFilled({ className }: IconProps) {
  return (
    <Phosphor
      className={className}
      d="M208,80H96V56a32,32,0,0,1,32-32c15.37,0,29.2,11,32.16,25.59a8,8,0,0,0,15.68-3.18C171.32,24.15,151.2,8,128,8A48.05,48.05,0,0,0,80,56V80H48A16,16,0,0,0,32,96V208a16,16,0,0,0,16,16H208a16,16,0,0,0,16-16V96A16,16,0,0,0,208,80Zm0,128H48V96H208V208Zm-80-96a28,28,0,0,0-8,54.83V184a8,8,0,0,0,16,0V166.83A28,28,0,0,0,128,112Zm0,40a12,12,0,1,1,12-12A12,12,0,0,1,128,152Z"
    />
  );
}
