/**
 * Spira's web icon set — **Gravity UI** (MIT, © 2022 YANDEX LLC), every glyph.
 *
 * The Android app moved to Gravity for every icon on 2026-08-14 (`ui/icons/SpiraIcons.kt`); this is
 * the web half of that move. `lucide-react` is gone — each of these is a drop-in for the Lucide
 * component it replaced (same `className`, same `currentColor` fill), so a consumer only swapped its
 * import source. The names are kept from Lucide **only** so that swap stayed mechanical; the glyph
 * each draws is Gravity's, matching the phone.
 *
 * Gravity draws on a 16 box with the line as an even-odd fill, so the viewBox is `0 0 16 16` and
 * every path carries `fillRule="evenodd"`. To add one, copy the `<path d>` of each `<path>` from
 * `@gravity-ui/icons/svgs/<name>.svg` (strip any `<defs>` clip — see CLAUDE.md).
 *
 * Generated; edit the mapping, not this file.
 */
import type { ReactNode, SVGProps } from "react";
import { cn } from "@/lib/utils";

// `size` is kept for Lucide drop-in compatibility: a few call sites pass `size={n}` instead of a
// className. It maps to width/height in px; a className width still wins because it paints later.
type IconProps = SVGProps<SVGSVGElement> & {
  className?: string;
  size?: number | string;
};

function make(paths: ReactNode) {
  return function Icon({ className, size, ...props }: IconProps) {
    return (
      <svg
        viewBox="0 0 16 16"
        width={size ?? "1em"}
        height={size ?? "1em"}
        className={cn("shrink-0", className)}
        aria-hidden="true"
        {...props}
      >
        {paths}
      </svg>
    );
  };
}

export const AlertTriangle = make(
  <>
    <path
      fill="currentColor"
      fillRule="evenodd"
      clipRule="evenodd"
      d="M7.134 2.994L2.217 11.5a1 1 0 0 0 .866 1.5h9.834a1 1 0 0 0 .866-1.5L8.866 2.993a1 1 0 0 0-1.732 0m3.03-.75c-.962-1.665-3.366-1.665-4.329 0L.918 10.749c-.963 1.666.24 3.751 2.165 3.751h9.834c1.925 0 3.128-2.085 2.164-3.751zM8 5a.75.75 0 0 1 .75.75v2a.75.75 0 0 1-1.5 0v-2A.75.75 0 0 1 8 5m1 5.75a1 1 0 1 1-2 0a1 1 0 0 1 2 0"
    />
  </>,
);
/* gravity: triangle-exclamation */
export const ArrowDown = make(
  <>
    <path
      fill="currentColor"
      fillRule="evenodd"
      clipRule="evenodd"
      d="M8 1.25a.75.75 0 0 1 .75.75v10.19l2.72-2.72a.75.75 0 1 1 1.06 1.06l-4 4a.75.75 0 0 1-1.06 0l-4-4a.75.75 0 1 1 1.06-1.06l2.72 2.72V2A.75.75 0 0 1 8 1.25"
    />
  </>,
);
/* gravity: arrow-down */
export const ArrowUp = make(
  <>
    <path
      fill="currentColor"
      fillRule="evenodd"
      clipRule="evenodd"
      d="M8 14.75a.75.75 0 0 1-.75-.75V3.81L4.53 6.53a.75.75 0 0 1-1.06-1.06l4-4a.75.75 0 0 1 1.06 0l4 4a.75.75 0 0 1-1.06 1.06L8.75 3.81V14a.75.75 0 0 1-.75.75"
    />
  </>,
);
/* gravity: arrow-up */
export const ArrowUpRight = make(
  <>
    <path
      fill="currentColor"
      fillRule="evenodd"
      clipRule="evenodd"
      d="M10 1.5A.75.75 0 0 0 10 3h1.94L6.97 7.97a.75.75 0 0 0 1.06 1.06L13 4.06V6a.75.75 0 0 0 1.5 0V2.25a.75.75 0 0 0-.75-.75zM7.5 3.25a.75.75 0 0 0-.75-.75H4.5a3 3 0 0 0-3 3v6a3 3 0 0 0 3 3h6a3 3 0 0 0 3-3V9.25a.75.75 0 0 0-1.5 0v2.25a1.5 1.5 0 0 1-1.5 1.5h-6A1.5 1.5 0 0 1 3 11.5v-6A1.5 1.5 0 0 1 4.5 4h2.25a.75.75 0 0 0 .75-.75"
    />
  </>,
);
/* gravity: arrow-up-right-from-square */
export const Baseline = make(
  <>
    <path
      fill="currentColor"
      fillRule="evenodd"
      clipRule="evenodd"
      d="M4.75 2.25c-.618 0-1.169.39-1.373.974L.042 12.752a.75.75 0 0 0 1.416.496L2.595 10h4.31l1.137 3.248a.75.75 0 0 0 1.416-.496L6.123 3.224A1.455 1.455 0 0 0 4.75 2.25M6.38 8.5L4.75 3.842L3.12 8.5zm5.135 2.996c0-.223.28-.746 1.152-.746H14.4c-.294 1.024-1.178 1.5-1.9 1.5c-.45 0-.677-.134-.792-.249a.7.7 0 0 1-.193-.505m2.985.754V13a.75.75 0 1 0 1.5 0v-3c0-1.117-.28-2.065-.873-2.744c-.606-.692-1.453-1.006-2.377-1.006c-.53 0-.946.07-1.306.195c-.338.117-.6.274-.804.396l-.025.015a.75.75 0 1 0 .77 1.288c.22-.132.365-.217.55-.281c.178-.062.423-.113.815-.113c.576 0 .978.186 1.248.494c.191.218.354.543.44 1.006h-1.771c-1.462 0-2.658.977-2.652 2.254c.003.542.191 1.116.632 1.557c.447.448 1.085.689 1.853.689c1 0 1.75-.75 1.75-1.5z"
    />
  </>,
);
/* gravity: font-case */
export const Bold = make(
  <>
    <path
      fill="currentColor"
      fillRule="evenodd"
      clipRule="evenodd"
      d="M4.25 2.25A.75.75 0 0 0 3.5 3v10c0 .414.336.75.75.75H9.5a3.25 3.25 0 0 0 1.477-6.146A3.25 3.25 0 0 0 8.5 2.25zm3.5 5a1.75 1.75 0 1 0 0-3.5h-2v3.5zm-2 1.5v3.5h3a1.75 1.75 0 1 0 0-3.5z"
    />
  </>,
);
/* gravity: bold */
export const Cable = make(
  <>
    <path
      fill="currentColor"
      fillRule="evenodd"
      clipRule="evenodd"
      d="M3.47 6.53a.75.75 0 0 1 1.06 1.061l-.727.727a2.743 2.743 0 0 0 3.879 3.879l.727-.727a.75.75 0 0 1 1.06 1.06l-.726.727a4.243 4.243 0 0 1-6-6zm8 1.879a.75.75 0 0 0 1.06 1.06l.727-.726a4.243 4.243 0 0 0-6-6l-.727.727a.75.75 0 0 0 1.061 1.06l.727-.727a2.743 2.743 0 0 1 3.879 3.879zm-.94-1.879a.75.75 0 1 0-1.06-1.06l-4 4a.75.75 0 1 0 1.06 1.06z"
    />
  </>,
);
/* gravity: link */
/** Gravity `house` — Home in the side navigation. Ported from the Android twin
 *  (`SpiraIcons.Home`) so the same row carries the same mark on both surfaces. */
export const Home = make(
  <>
    <path
      fill="currentColor"
      fillRule="evenodd"
      clipRule="evenodd"
      d="M12.5 12.618c.307-.275.5-.674.5-1.118V6.977a1.5 1.5 0 0 0-.585-1.189l-3.5-2.692a1.5 1.5 0 0 0-1.83 0l-3.5 2.692A1.5 1.5 0 0 0 3 6.978V11.5A1.496 1.496 0 0 0 4.493 13H5V9.5a2 2 0 0 1 2-2h2a2 2 0 0 1 2 2V13h.507c.381-.002.73-.146.993-.382m2-1.118a3 3 0 0 1-3 3h-7a3 3 0 0 1-3-3V6.977A3 3 0 0 1 2.67 4.6l3.5-2.692a3 3 0 0 1 3.66 0l3.5 2.692a3 3 0 0 1 1.17 2.378zm-5-2A.5.5 0 0 0 9 9H7a.5.5 0 0 0-.5.5V13h3z"
    />
  </>,
);

/** Gravity `circle-question` — About Spira. The Android twin is `SpiraIcons.CircleQuestion`. */
export const CircleQuestion = make(
  <>
    <path
      fill="currentColor"
      fillRule="evenodd"
      clipRule="evenodd"
      d="M8 13.5a5.5 5.5 0 1 0 0-11a5.5 5.5 0 0 0 0 11M8 15A7 7 0 1 0 8 1a7 7 0 0 0 0 14M6.44 4.54c.43-.354.994-.565 1.56-.565c1.217 0 2.34.82 2.34 2.14c0 .377-.078.745-.298 1.1c-.208.339-.513.614-.875.867c-.217.153-.325.257-.379.328c-.038.052-.038.07-.038.089a.75.75 0 0 1-1.5 0c0-.794.544-1.286 1.057-1.645c.28-.196.4-.332.458-.426a.54.54 0 0 0 .075-.312c0-.3-.244-.641-.84-.641a1 1 0 0 0-.608.223c-.167.138-.231.287-.231.418a.75.75 0 0 1-1.5 0c0-.674.345-1.22.78-1.577M8 12a1 1 0 1 0 0-2a1 1 0 0 0 0 2"
    />
  </>,
);

export const Calendar = make(
  <>
    <path
      fill="currentColor"
      fillRule="evenodd"
      clipRule="evenodd"
      d="M5.25 5.497a.75.75 0 0 1-.75-.75V4A1.5 1.5 0 0 0 3 5.5v1h10v-1A1.5 1.5 0 0 0 11.5 4v.75a.75.75 0 0 1-1.5 0V4H6v.747a.75.75 0 0 1-.75.75M10 2.5H6v-.752a.75.75 0 1 0-1.5 0V2.5a3 3 0 0 0-3 3v6a3 3 0 0 0 3 3h7a3 3 0 0 0 3-3v-6a3 3 0 0 0-3-3v-.75a.75.75 0 0 0-1.5 0zM3 8v3.5A1.5 1.5 0 0 0 4.5 13h7a1.5 1.5 0 0 0 1.5-1.5V8z"
    />
  </>,
);
/* gravity: calendar */
export const CalendarPlus = make(
  <>
    <path
      fill="currentColor"
      fillRule="evenodd"
      clipRule="evenodd"
      d="M5.25 5.497a.75.75 0 0 1-.75-.75V4A1.5 1.5 0 0 0 3 5.5v1h10v-1A1.5 1.5 0 0 0 11.5 4v.75a.75.75 0 0 1-1.5 0V4H6v.747a.75.75 0 0 1-.75.75M10 2.5H6v-.752a.75.75 0 1 0-1.5 0V2.5a3 3 0 0 0-3 3v6a3 3 0 0 0 3 3h7a3 3 0 0 0 3-3v-6a3 3 0 0 0-3-3v-.75a.75.75 0 0 0-1.5 0zM3 8v3.5A1.5 1.5 0 0 0 4.5 13h7a1.5 1.5 0 0 0 1.5-1.5V8z"
    />
  </>,
);
/* gravity: calendar */
export const Check = make(
  <>
    <path
      fill="currentColor"
      fillRule="evenodd"
      clipRule="evenodd"
      d="M13.488 3.43a.75.75 0 0 1 .081 1.058l-6 7a.75.75 0 0 1-1.1.042l-3.5-3.5A.75.75 0 0 1 4.03 6.97l2.928 2.927l5.473-6.385a.75.75 0 0 1 1.057-.081"
    />
  </>,
);
/* gravity: check */
export const CheckSquare = make(
  <>
    <path
      fill="currentColor"
      fillRule="evenodd"
      clipRule="evenodd"
      d="M4.5 3h7A1.5 1.5 0 0 1 13 4.5v7a1.5 1.5 0 0 1-1.5 1.5h-7A1.5 1.5 0 0 1 3 11.5v-7A1.5 1.5 0 0 1 4.5 3m-3 1.5a3 3 0 0 1 3-3h7a3 3 0 0 1 3 3v7a3 3 0 0 1-3 3h-7a3 3 0 0 1-3-3zm10.092 1.46a.75.75 0 0 0-1.184-.92L7.43 8.869l-1.4-1.4A.75.75 0 0 0 4.97 8.53l2 2a.75.75 0 0 0 1.122-.07z"
    />
  </>,
);
/* gravity: square-check */
export const ChevronDown = make(
  <>
    <path
      fill="currentColor"
      fillRule="evenodd"
      clipRule="evenodd"
      d="M2.97 5.47a.75.75 0 0 1 1.06 0L8 9.44l3.97-3.97a.75.75 0 1 1 1.06 1.06l-4.5 4.5a.75.75 0 0 1-1.06 0l-4.5-4.5a.75.75 0 0 1 0-1.06"
    />
  </>,
);
/* gravity: chevron-down */
export const ChevronDownIcon = make(
  <>
    <path
      fill="currentColor"
      fillRule="evenodd"
      clipRule="evenodd"
      d="M2.97 5.47a.75.75 0 0 1 1.06 0L8 9.44l3.97-3.97a.75.75 0 1 1 1.06 1.06l-4.5 4.5a.75.75 0 0 1-1.06 0l-4.5-4.5a.75.75 0 0 1 0-1.06"
    />
  </>,
);
/* gravity: chevron-down */
export const ChevronLeft = make(
  <>
    <path
      fill="currentColor"
      fillRule="evenodd"
      clipRule="evenodd"
      d="M10.53 2.97a.75.75 0 0 1 0 1.06L6.56 8l3.97 3.97a.75.75 0 1 1-1.06 1.06l-4.5-4.5a.75.75 0 0 1 0-1.06l4.5-4.5a.75.75 0 0 1 1.06 0"
    />
  </>,
);
/* gravity: chevron-left */
export const ChevronLeftIcon = make(
  <>
    <path
      fill="currentColor"
      fillRule="evenodd"
      clipRule="evenodd"
      d="M10.53 2.97a.75.75 0 0 1 0 1.06L6.56 8l3.97 3.97a.75.75 0 1 1-1.06 1.06l-4.5-4.5a.75.75 0 0 1 0-1.06l4.5-4.5a.75.75 0 0 1 1.06 0"
    />
  </>,
);
/* gravity: chevron-left */
export const ChevronRight = make(
  <>
    <path
      fill="currentColor"
      fillRule="evenodd"
      clipRule="evenodd"
      d="M5.47 13.03a.75.75 0 0 1 0-1.06L9.44 8L5.47 4.03a.75.75 0 0 1 1.06-1.06l4.5 4.5a.75.75 0 0 1 0 1.06l-4.5 4.5a.75.75 0 0 1-1.06 0"
    />
  </>,
);
/* gravity: chevron-right */
export const ChevronRightIcon = make(
  <>
    <path
      fill="currentColor"
      fillRule="evenodd"
      clipRule="evenodd"
      d="M5.47 13.03a.75.75 0 0 1 0-1.06L9.44 8L5.47 4.03a.75.75 0 0 1 1.06-1.06l4.5 4.5a.75.75 0 0 1 0 1.06l-4.5 4.5a.75.75 0 0 1-1.06 0"
    />
  </>,
);
/* gravity: chevron-right */
export const CaretsExpandVertical = make(
  <>
    <path
      fill="currentColor"
      fillRule="evenodd"
      clipRule="evenodd"
      d="M12 6.273a.73.73 0 0 0-.18-.479L8.8 2.342A1 1 0 0 0 8.046 2h-.092a1 1 0 0 0-.753.341L4.18 5.794A.727.727 0 0 0 4.727 7h6.546A.727.727 0 0 0 12 6.273M4 9.727c0 .176.064.346.18.479l3.02 3.453a1 1 0 0 0 .753.341h.092a1 1 0 0 0 .753-.341l3.021-3.453A.727.727 0 0 0 11.273 9H4.727A.727.727 0 0 0 4 9.727"
    />
  </>,
);
/* gravity: carets-expand-vertical */
export const ChevronUp = make(
  <>
    <path
      fill="currentColor"
      fillRule="evenodd"
      clipRule="evenodd"
      d="M13.03 10.53a.75.75 0 0 1-1.06 0L8 6.56l-3.97 3.97a.75.75 0 1 1-1.06-1.06l4.5-4.5a.75.75 0 0 1 1.06 0l4.5 4.5a.75.75 0 0 1 0 1.06"
    />
  </>,
);
/* gravity: chevron-up */
export const Circle = make(
  <>
    <path
      fill="currentColor"
      fillRule="evenodd"
      clipRule="evenodd"
      d="M8 13.5a5.5 5.5 0 1 0 0-11a5.5 5.5 0 0 0 0 11M8 15A7 7 0 1 0 8 1a7 7 0 0 0 0 14"
    />
  </>,
);
/* gravity: circle */
export const CircleCheck = make(
  <>
    <path
      fill="currentColor"
      fillRule="evenodd"
      clipRule="evenodd"
      d="M13.5 8a5.5 5.5 0 1 1-11 0a5.5 5.5 0 0 1 11 0M15 8A7 7 0 1 1 1 8a7 7 0 0 1 14 0m-3.9-1.55a.75.75 0 1 0-1.2-.9L7.419 8.858L6.03 7.47a.75.75 0 0 0-1.06 1.06l2 2a.75.75 0 0 0 1.13-.08z"
    />
  </>,
);
/* gravity: circle-check */
export const CircleCheckFill = make(
  <>
    <path
      fill="currentColor"
      fillRule="evenodd"
      clipRule="evenodd"
      d="M8 15A7 7 0 1 0 8 1a7 7 0 0 0 0 14m3.1-8.55a.75.75 0 1 0-1.2-.9L7.419 8.858L6.03 7.47a.75.75 0 0 0-1.06 1.06l2 2a.75.75 0 0 0 1.13-.08z"
    />
  </>,
);
/* gravity: circle-check-fill */
/**
 * Gravity `triangle-exclamation-fill` — the warning mark on a notice (owner, 2026-08-18).
 *
 * The **filled** twin, not the outline `TriangleAlert` above it: CLAUDE.md's notice spec puts all
 * four kinds on their `-fill` glyph, because they are one family saying one sort of thing and an
 * outline among them read as a different sort of message.
 */
export const TriangleExclamationFill = make(
  <>
    <path
      fill="currentColor"
      fillRule="evenodd"
      clipRule="evenodd"
      d="M5.835 2.244c.963-1.665 3.367-1.665 4.33 0l4.916 8.505c.964 1.666-.24 3.751-2.164 3.751H3.083c-1.925 0-3.128-2.085-2.165-3.751zM8 5a.75.75 0 0 1 .75.75v2a.75.75 0 1 1-1.5 0v-2A.75.75 0 0 1 8 5m1 5.75a1 1 0 1 1-2 0a1 1 0 0 1 2 0"
    />
  </>,
);

/** Gravity `circle-info-fill` — the informational mark on a notice. */
export const CircleInfoFill = make(
  <>
    <path
      fill="currentColor"
      fillRule="evenodd"
      clipRule="evenodd"
      d="M8 15A7 7 0 1 0 8 1a7 7 0 0 0 0 14m1-9.5a1 1 0 1 1-2 0a1 1 0 0 1 2 0M8 7.75a.75.75 0 0 1 .75.75V11a.75.75 0 0 1-1.5 0V8.5A.75.75 0 0 1 8 7.75"
    />
  </>,
);

/**
 * Gravity `dots-9` — the drag grip, the owner's pick (2026-08-18).
 *
 * It is the mark that stands in the radio's place while a list is being reordered, on both
 * surfaces (`SpiraIcons.Dots9` on Android).
 */
export const Dots9 = make(
  <>
    <path
      fill="currentColor"
      fillRule="evenodd"
      clipRule="evenodd"
      d="M9.5 3a1.5 1.5 0 1 1-3 0a1.5 1.5 0 0 1 3 0M3 9.5a1.5 1.5 0 1 0 0-3a1.5 1.5 0 0 0 0 3M9.5 8a1.5 1.5 0 1 1-3 0a1.5 1.5 0 0 1 3 0m5 0a1.5 1.5 0 1 1-3 0a1.5 1.5 0 0 1 3 0M13 4.5a1.5 1.5 0 1 0 0-3a1.5 1.5 0 0 0 0 3M4.5 3a1.5 1.5 0 1 1-3 0a1.5 1.5 0 0 1 3 0M8 14.5a1.5 1.5 0 1 0 0-3a1.5 1.5 0 0 0 0 3m6.5-1.5a1.5 1.5 0 1 1-3 0a1.5 1.5 0 0 1 3 0M3 14.5a1.5 1.5 0 1 0 0-3a1.5 1.5 0 0 0 0 3"
    />
  </>,
);

export const CircleExclamationFilled = make(
  <>
    <path
      fill="currentColor"
      fillRule="evenodd"
      clipRule="evenodd"
      d="M15 8A7 7 0 1 1 1 8a7 7 0 0 1 14 0m-6 2.5a1 1 0 1 1-2 0a1 1 0 0 1 2 0M8.75 5a.75.75 0 0 0-1.5 0v2.5a.75.75 0 0 0 1.5 0z"
    />
  </>,
);
/* gravity: circle-exclamation-fill */
export const ArrowUturnCwDown = make(
  <>
    <path
      fill="currentColor"
      fillRule="evenodd"
      clipRule="evenodd"
      d="M11.28 13.53a.75.75 0 0 1-1.06 0l-3-3a.75.75 0 1 1 1.06-1.06L10 11.19V7a3.25 3.25 0 0 0-6.5 0v1A.75.75 0 0 1 2 8V7a4.75 4.75 0 0 1 9.5 0v4.19l1.72-1.72a.75.75 0 1 1 1.06 1.06z"
    />
  </>,
);
/* gravity: arrow-uturn-cw-down */
export const CirclePlus = make(
  <>
    <path
      fill="currentColor"
      fillRule="evenodd"
      clipRule="evenodd"
      d="M13.5 8a5.5 5.5 0 1 1-11 0a5.5 5.5 0 0 1 11 0M15 8A7 7 0 1 1 1 8a7 7 0 0 1 14 0M8.75 5.5a.75.75 0 0 0-1.5 0v1.75H5.5a.75.75 0 1 0 0 1.5h1.75v1.75a.75.75 0 0 0 1.5 0V8.75h1.75a.75.75 0 0 0 0-1.5H8.75z"
    />
  </>,
);
/* gravity: circle-plus */
export const CircleX = make(
  <>
    <path
      fill="currentColor"
      fillRule="evenodd"
      clipRule="evenodd"
      d="M13.5 8a5.5 5.5 0 1 1-11 0a5.5 5.5 0 0 1 11 0M15 8A7 7 0 1 1 1 8a7 7 0 0 1 14 0M6.53 5.47a.75.75 0 0 0-1.06 1.06L6.94 8L5.47 9.47a.75.75 0 1 0 1.06 1.06L8 9.06l1.47 1.47a.75.75 0 1 0 1.06-1.06L9.06 8l1.47-1.47a.75.75 0 1 0-1.06-1.06L8 6.94z"
    />
  </>,
);
/* gravity: circle-xmark */
export const ClipboardPaste = make(
  <>
    <path
      fill="currentColor"
      fillRule="evenodd"
      clipRule="evenodd"
      d="M12 2.5H8A1.5 1.5 0 0 0 6.5 4v1H8a3 3 0 0 1 3 3v1.5h1A1.5 1.5 0 0 0 13.5 8V4A1.5 1.5 0 0 0 12 2.5M11 11h1a3 3 0 0 0 3-3V4a3 3 0 0 0-3-3H8a3 3 0 0 0-3 3v1H4a3 3 0 0 0-3 3v4a3 3 0 0 0 3 3h4a3 3 0 0 0 3-3zM4 6.5h4A1.5 1.5 0 0 1 9.5 8v4A1.5 1.5 0 0 1 8 13.5H4A1.5 1.5 0 0 1 2.5 12V8A1.5 1.5 0 0 1 4 6.5"
    />
  </>,
);
/* gravity: copy */
export const Code = make(
  <>
    <path
      fill="currentColor"
      fillRule="evenodd"
      clipRule="evenodd"
      d="M10.218 3.216a.75.75 0 0 0-1.436-.431l-3 10a.75.75 0 0 0 1.436.43zM4.53 4.97a.75.75 0 0 1 0 1.06L2.56 8l1.97 1.97a.75.75 0 0 1-1.06 1.06l-2.5-2.5a.75.75 0 0 1 0-1.06l2.5-2.5a.75.75 0 0 1 1.06 0m6.94 6.06a.75.75 0 0 1 0-1.06L13.44 8l-1.97-1.97a.75.75 0 0 1 1.06-1.06l2.5 2.5a.75.75 0 0 1 0 1.06l-2.5 2.5a.75.75 0 0 1-1.06 0"
    />
  </>,
);
/* gravity: code */
export const Copy = make(
  <>
    <path
      fill="currentColor"
      fillRule="evenodd"
      clipRule="evenodd"
      d="M12 2.5H8A1.5 1.5 0 0 0 6.5 4v1H8a3 3 0 0 1 3 3v1.5h1A1.5 1.5 0 0 0 13.5 8V4A1.5 1.5 0 0 0 12 2.5M11 11h1a3 3 0 0 0 3-3V4a3 3 0 0 0-3-3H8a3 3 0 0 0-3 3v1H4a3 3 0 0 0-3 3v4a3 3 0 0 0 3 3h4a3 3 0 0 0 3-3zM4 6.5h4A1.5 1.5 0 0 1 9.5 8v4A1.5 1.5 0 0 1 8 13.5H4A1.5 1.5 0 0 1 2.5 12V8A1.5 1.5 0 0 1 4 6.5"
    />
  </>,
);
/* gravity: copy */
export const Download = make(
  <>
    <path
      fill="currentColor"
      fillRule="evenodd"
      clipRule="evenodd"
      d="M8.53 11.78a.75.75 0 0 1-1.06 0l-2.5-2.5a.75.75 0 0 1 1.06-1.06l1.22 1.22V1.75a.75.75 0 0 1 1.5 0v7.69l1.22-1.22a.75.75 0 1 1 1.06 1.06zM1.75 13.5a.75.75 0 0 0 0 1.5h12.5a.75.75 0 0 0 0-1.5z"
    />
  </>,
);
/* gravity: arrow-down-to-line */
export const Eraser = make(
  <>
    <path
      fill="currentColor"
      fillRule="evenodd"
      clipRule="evenodd"
      d="m9.646 3.268l2.586 2.586a.914.914 0 0 1 0 1.292L8.72 10.66L4.84 6.78l3.513-3.512a.914.914 0 0 1 1.292 0M3.78 7.84L1.768 9.854a.914.914 0 0 0 0 1.292L3.12 12.5h3.76l.78-.78zm9.513.366L9 12.5h6.25a.75.75 0 0 1 0 1.5H2.5L.707 12.207a2.414 2.414 0 0 1 0-3.414l6.586-6.586a2.414 2.414 0 0 1 3.414 0l2.586 2.586a2.414 2.414 0 0 1 0 3.414"
    />
  </>,
);
/* gravity: eraser */
export const FileText = make(
  <>
    <path
      fill="currentColor"
      fillRule="evenodd"
      clipRule="evenodd"
      d="M5 13.5h6a1.5 1.5 0 0 0 1.5-1.5V7.243a1.5 1.5 0 0 0-.44-1.061L8.819 2.939a1.5 1.5 0 0 0-1.06-.439H5A1.5 1.5 0 0 0 3.5 4v8A1.5 1.5 0 0 0 5 13.5m9-6.257a3 3 0 0 0-.879-2.122L9.88 1.88A3 3 0 0 0 7.757 1H5a3 3 0 0 0-3 3v8a3 3 0 0 0 3 3h6a3 3 0 0 0 3-3zM5 8.25a.75.75 0 0 1 .75-.75h4.5a.75.75 0 0 1 0 1.5h-4.5A.75.75 0 0 1 5 8.25m.75 2.25a.75.75 0 0 0 0 1.5h2.5a.75.75 0 0 0 0-1.5z"
    />
  </>,
);
/* gravity: file-text */
export const FileWarning = make(
  <>
    <path
      fill="currentColor"
      fillRule="evenodd"
      clipRule="evenodd"
      d="M12.5 12a1.5 1.5 0 0 1-1.5 1.5H5A1.5 1.5 0 0 1 3.5 12V4A1.5 1.5 0 0 1 5 2.5h2.757a1.5 1.5 0 0 1 1.061.44l3.243 3.242a1.5 1.5 0 0 1 .439 1.06zm.621-6.879A3 3 0 0 1 14 7.243V12a3 3 0 0 1-3 3H5a3 3 0 0 1-3-3V4a3 3 0 0 1 3-3h2.757a3 3 0 0 1 2.122.879zM8 5a.75.75 0 0 1 .75.75V8.5a.75.75 0 0 1-1.5 0V5.75A.75.75 0 0 1 8 5m1 6.25a1 1 0 1 1-2 0a1 1 0 0 1 2 0"
    />
  </>,
);
/* gravity: file-exclamation */
export const Flag = make(
  <>
    <path
      fill="currentColor"
      fillRule="evenodd"
      clipRule="evenodd"
      d="M7.47 3.588a4.45 4.45 0 0 0-4.15-.224a.55.55 0 0 0-.32.499v5.533a6.25 6.25 0 0 1 5.547.439l.344.207a4.02 4.02 0 0 0 3.865.148a.44.44 0 0 0 .244-.395V4.182a6.26 6.26 0 0 1-5.386-.508zm5.957 7.944a5.52 5.52 0 0 1-5.307-.204l-.345-.207a4.75 4.75 0 0 0-4.314-.293L3 11.026v3.255a.75.75 0 0 1-1.5 0V3.863c0-.8.465-1.526 1.19-1.861a5.95 5.95 0 0 1 5.552.3l.144.086a4.76 4.76 0 0 0 4.447.24l.603-.278a.75.75 0 0 1 1.064.681v6.764c0 .735-.416 1.408-1.073 1.737"
    />
  </>,
);
/* gravity: flag */
export const GlobeOff = make(
  <>
    <path
      fill="currentColor"
      fillRule="evenodd"
      clipRule="evenodd"
      d="M9.208 12.346c-.485 1-.953 1.154-1.208 1.154s-.723-.154-1.208-1.154c-.372-.768-.647-1.858-.749-3.187a21 21 0 0 0 3.914 0c-.102 1.329-.377 2.419-.75 3.187m.788-4.699C9.358 7.714 8.69 7.75 8 7.75s-1.358-.036-1.996-.103c.037-1.696.343-3.075.788-3.993C7.277 2.654 7.745 2.5 8 2.5s.723.154 1.208 1.154c.445.918.75 2.297.788 3.993m1.478 1.306c-.085 1.516-.375 2.848-.836 3.874a5.5 5.5 0 0 0 2.843-4.364c-.621.199-1.295.364-2.007.49m1.918-2.043c-.572.204-1.21.379-1.901.514c-.056-1.671-.354-3.14-.853-4.251a5.5 5.5 0 0 1 2.754 3.737m-8.883.514c.056-1.671.354-3.14.853-4.251A5.5 5.5 0 0 0 2.608 6.91c.572.204 1.21.379 1.901.514M2.52 8.463a5.5 5.5 0 0 0 2.843 4.364c-.46-1.026-.75-2.358-.836-3.874a15.5 15.5 0 0 1-2.007-.49M15 8A7 7 0 1 0 1 8a7 7 0 0 0 14 0"
    />
  </>,
);
/* gravity: globe */
export const Heading1 = make(
  <>
    <path
      fill="currentColor"
      fillRule="evenodd"
      clipRule="evenodd"
      d="M14 4.25a.75.75 0 0 0-1.248-.56l-2.25 2a.75.75 0 0 0 .996 1.12l1.002-.89v5.83a.75.75 0 0 0 1.5 0zm-11.5 0a.75.75 0 0 0-1.5 0v7.496a.75.75 0 0 0 1.5 0V8.75h4v2.996a.75.75 0 0 0 1.5 0V4.25a.75.75 0 0 0-1.5 0v3h-4z"
    />
  </>,
);
/* gravity: heading-1 */
export const Heading2 = make(
  <>
    <path
      fill="currentColor"
      fillRule="evenodd"
      clipRule="evenodd"
      d="M2.5 4.25a.75.75 0 0 0-1.5 0v7.496a.75.75 0 0 0 1.5 0V8.75h4v2.996a.75.75 0 0 0 1.5 0V4.25a.75.75 0 0 0-1.5 0v3h-4zm8.403 1.783A1.364 1.364 0 0 1 12.226 5h.226a1.071 1.071 0 0 1 .672 1.906l-3.61 2.906a1.51 1.51 0 0 0 .947 2.688h3.789a.75.75 0 0 0 0-1.5h-3.793l-.003-.003l-.003-.004v-.004a.01.01 0 0 1 .004-.008l3.61-2.907A2.571 2.571 0 0 0 12.452 3.5h-.226c-1.314 0-2.46.894-2.778 2.17l-.038.148a.75.75 0 1 0 1.456.364z"
    />
  </>,
);
/* gravity: heading-2 */
export const Heading3 = make(
  <>
    <path
      fill="currentColor"
      fillRule="evenodd"
      clipRule="evenodd"
      d="M2.5 4.25a.75.75 0 0 0-1.5 0v7.496a.75.75 0 0 0 1.5 0V8.75h4v2.996a.75.75 0 0 0 1.5 0V4.25a.75.75 0 0 0-1.5 0v3h-4zm8.114 1.496c.202-.504.69-.834 1.232-.834h.28a.94.94 0 0 1 .929.796l.027.18a1.15 1.15 0 0 1-.911 1.303l-.8.16a.662.662 0 0 0 .129 1.31h1.21a.89.89 0 0 1 .882 1.017a1.67 1.67 0 0 1-1.414 1.414l-.103.015a1.81 1.81 0 0 1-1.828-.9l-.018-.033a.662.662 0 0 0-1.152.652l.018.032a3.13 3.13 0 0 0 3.167 1.559l.103-.015a2.99 2.99 0 0 0 2.537-2.537a2.21 2.21 0 0 0-1.058-2.216a2.47 2.47 0 0 0 .547-1.963l-.028-.179a2.26 2.26 0 0 0-2.237-1.919h-.28a2.65 2.65 0 0 0-2.46 1.666a.662.662 0 1 0 1.228.492"
    />
  </>,
);
/* gravity: heading-3 */
export const Highlighter = make(
  <>
    <path
      fill="currentColor"
      fillRule="evenodd"
      clipRule="evenodd"
      d="M8.135.693A1.003 1.003 0 0 1 9.567.486l.091.082l5.559 5.559c.39.39.39 1.025 0 1.416l-4.332 4.332a2.003 2.003 0 0 1-2.833 0l-.383-.383c-.11-.11-.295-.084-.391.038l-2.391 3.01a2.467 2.467 0 1 1-3.464-3.464l3.014-2.389c.122-.096.148-.28.038-.39l-.312-.311a2.004 2.004 0 0 1 .001-2.834L6.917 2.4a6 6 0 0 0 1.031-1.378l.123-.224zM5.227 6.215a.5.5 0 0 0-.001.708l.311.313c.86.86.525 2.08-.167 2.629l-3.013 2.389a.963.963 0 1 0 1.353 1.352l2.39-3.01h.002c.555-.698 1.776-1.02 2.63-.167l.382.383a.5.5 0 0 0 .708 0l.416-.416L5.64 5.8zm3.835-4.12c-.307.497-.67.956-1.082 1.368L6.703 4.738L11.3 9.335l2.502-2.5l-.759-.76l-1.067 1.082a.405.405 0 0 1-.68-.384l.493-1.951z"
    />
  </>,
);
/* gravity: paintbrush */
export const History = make(
  <>
    <path
      fill="currentColor"
      fillRule="evenodd"
      clipRule="evenodd"
      d="M1.5 8a6.5 6.5 0 1 1 7.348 6.445a.75.75 0 1 1-.194-1.487A5.001 5.001 0 1 0 4.5 11.57v-1.32a.75.75 0 0 1 1.5 0v3a.75.75 0 0 1-.75.75h-3a.75.75 0 0 1 0-1.5h1.06A6.48 6.48 0 0 1 1.5 8M8 4.25a.75.75 0 0 1 .75.75v2.625l1.033.775a.75.75 0 1 1-.9 1.2l-1.333-1a.75.75 0 0 1-.3-.6V5A.75.75 0 0 1 8 4.25"
    />
  </>,
);
/* gravity: clock-arrow-rotate-left */
export const Info = make(
  <>
    <path
      fill="currentColor"
      fillRule="evenodd"
      clipRule="evenodd"
      d="M8 13.5a5.5 5.5 0 1 0 0-11a5.5 5.5 0 0 0 0 11M8 15A7 7 0 1 0 8 1a7 7 0 0 0 0 14m1-9.5a1 1 0 1 1-2 0a1 1 0 0 1 2 0m-.25 3a.75.75 0 0 0-1.5 0V11a.75.75 0 0 0 1.5 0z"
    />
  </>,
);
/* gravity: circle-info */
export const Italic = make(
  <>
    <path
      fill="currentColor"
      fillRule="evenodd"
      clipRule="evenodd"
      d="M7.25 2a.75.75 0 0 0 0 1.5h1.317l-2.7 9H4.25a.75.75 0 1 0 0 1.5h4.5a.75.75 0 0 0 0-1.5H7.433l2.7-9h1.617a.75.75 0 0 0 0-1.5z"
    />
  </>,
);
/* gravity: italic */
export const LayoutGrid = make(
  <>
    <path
      fill="currentColor"
      fillRule="evenodd"
      clipRule="evenodd"
      d="M9.25 3.5h-2.5v2h2.5zM4 3.5h1.25v2H2.5V5A1.5 1.5 0 0 1 4 3.5M2.5 7h2.75v2H2.5zm0 3.5v.5A1.5 1.5 0 0 0 4 12.5h1.25v-2zm4.25 0v2h2.5v-2zm4 0v2H12a1.5 1.5 0 0 0 1.5-1.5v-.5zM13.5 9h-2.75V7h2.75zM9.25 9h-2.5V7h2.5zm1.5-5.5v2h2.75V5A1.5 1.5 0 0 0 12 3.5zM4 2a3 3 0 0 0-3 3v6a3 3 0 0 0 3 3h8a3 3 0 0 0 3-3V5a3 3 0 0 0-3-3z"
    />
  </>,
);
/* gravity: layout-cells */
export const Link = make(
  <>
    <path
      fill="currentColor"
      fillRule="evenodd"
      clipRule="evenodd"
      d="M3.47 6.53a.75.75 0 0 1 1.06 1.061l-.727.727a2.743 2.743 0 0 0 3.879 3.879l.727-.727a.75.75 0 0 1 1.06 1.06l-.726.727a4.243 4.243 0 0 1-6-6zm8 1.879a.75.75 0 0 0 1.06 1.06l.727-.726a4.243 4.243 0 0 0-6-6l-.727.727a.75.75 0 0 0 1.061 1.06l.727-.727a2.743 2.743 0 0 1 3.879 3.879zm-.94-1.879a.75.75 0 1 0-1.06-1.06l-4 4a.75.75 0 1 0 1.06 1.06z"
    />
  </>,
);
/* gravity: link */
export const List = make(
  <>
    <path
      fill="currentColor"
      fillRule="evenodd"
      clipRule="evenodd"
      d="M2.5 5a1.25 1.25 0 1 0 0-2.5a1.25 1.25 0 0 0 0 2.5m3.25-2a.75.75 0 0 0 0 1.5h8.5a.75.75 0 0 0 0-1.5zm0 8.5a.75.75 0 0 0 0 1.5h8.5a.75.75 0 0 0 0-1.5zM5 8a.75.75 0 0 1 .75-.75h8.5a.75.75 0 0 1 0 1.5h-8.5A.75.75 0 0 1 5 8M3.75 8a1.25 1.25 0 1 1-2.5 0a1.25 1.25 0 0 1 2.5 0M2.5 13.5a1.25 1.25 0 1 0 0-2.5a1.25 1.25 0 0 0 0 2.5"
    />
  </>,
);
/* gravity: list-ul */
export const ListChecks = make(
  <>
    <path
      fill="currentColor"
      fillRule="evenodd"
      clipRule="evenodd"
      d="M5.28 2.22a.75.75 0 0 1 0 1.06l-2 2a.75.75 0 0 1-1.06 0l-1-1a.75.75 0 0 1 1.06-1.06l.47.47l1.47-1.47a.75.75 0 0 1 1.06 0M6.5 3.75c0 .414.336.75.75.75h7a.75.75 0 0 0 0-1.5h-7a.75.75 0 0 0-.75.75m.75 3.5a.75.75 0 0 0 0 1.5h7a.75.75 0 0 0 0-1.5zm-1.97.03a.75.75 0 0 0-1.06-1.06L2.75 7.69l-.47-.47a.75.75 0 0 0-1.06 1.06l1 1a.75.75 0 0 0 1.06 0zm0 3.19a.75.75 0 0 1 0 1.06l-2 2a.75.75 0 0 1-1.06 0l-1-1a.75.75 0 1 1 1.06-1.06l.47.47l1.47-1.47a.75.75 0 0 1 1.06 0m1.97 1.03a.75.75 0 0 0 0 1.5h7a.75.75 0 0 0 0-1.5z"
    />
  </>,
);
/* gravity: list-check */
export const ListOrdered = make(
  <>
    <path
      fill="currentColor"
      fillRule="evenodd"
      clipRule="evenodd"
      d="M3.21 2.5a.5.5 0 0 0-.796-.403l-.71.524a.5.5 0 0 0 .507.856V5a.5.5 0 0 0 1 0zm2.54.5a.75.75 0 1 0 0 1.5h8.5a.75.75 0 0 0 0-1.5zm0 8.5a.75.75 0 0 0 0 1.5h8.5a.75.75 0 0 0 0-1.5zM5 8a.75.75 0 0 1 .75-.75h8.5a.75.75 0 0 1 0 1.5h-8.5A.75.75 0 0 1 5 8m-2.596-.67c-.07.051-.087.097-.09.116a.5.5 0 0 1-.988-.149c.051-.345.26-.61.495-.779c.236-.169.531-.268.83-.268c.345 0 .666.109.902.343s.337.543.337.85c0 .51-.321.855-.76 1.278l-.02.018l-.013.011h.493a.5.5 0 0 1 0 1H1.75a.5.5 0 0 1-.326-.879l1.022-.88c.223-.216.338-.344.398-.434c.046-.069.046-.092.046-.111v-.002c0-.09-.027-.127-.041-.141c-.013-.013-.062-.053-.199-.053a.44.44 0 0 0-.246.08M1.9 10.5a.5.5 0 0 0 0 1h.38l-.15.18a.5.5 0 0 0 .383.82c.259 0 .365.077.407.122c.051.054.08.133.08.218a.02.02 0 0 1-.004.014a.2.2 0 0 1-.057.052a.7.7 0 0 1-.371.094a.55.55 0 0 1-.258-.057c-.059-.032-.075-.061-.076-.064a.5.5 0 0 0-.91.416c.213.464.728.705 1.244.705c.319 0 .651-.082.92-.258c.275-.181.512-.487.512-.902c0-.293-.096-.634-.355-.906a1.3 1.3 0 0 0-.252-.206l.34-.407a.5.5 0 0 0-.383-.821z"
    />
  </>,
);
/* gravity: list-ol */
export const Loader = make(
  <>
    <path
      fill="currentColor"
      fillRule="evenodd"
      clipRule="evenodd"
      d="M8 1.5a6.5 6.5 0 0 0-6.445 5.649a.75.75 0 1 0 1.488.194A5.001 5.001 0 0 1 11.57 4.5h-1.32a.75.75 0 0 0 0 1.5h3a.75.75 0 0 0 .75-.75v-3a.75.75 0 0 0-1.5 0v1.06A6.48 6.48 0 0 0 8 1.5m-5.25 13a.75.75 0 0 1-.75-.75v-3a.75.75 0 0 1 .75-.75h3a.75.75 0 0 1 0 1.5H4.43a5.001 5.001 0 0 0 8.528-2.843a.75.75 0 1 1 1.487.194A6.501 6.501 0 0 1 3.5 12.691v1.059a.75.75 0 0 1-.75.75"
    />
  </>,
);
/* gravity: arrows-rotate-right */
export const Loader2 = make(
  <>
    <path
      fill="currentColor"
      fillRule="evenodd"
      clipRule="evenodd"
      d="M8 1.5a6.5 6.5 0 0 0-6.445 5.649a.75.75 0 1 0 1.488.194A5.001 5.001 0 0 1 11.57 4.5h-1.32a.75.75 0 0 0 0 1.5h3a.75.75 0 0 0 .75-.75v-3a.75.75 0 0 0-1.5 0v1.06A6.48 6.48 0 0 0 8 1.5m-5.25 13a.75.75 0 0 1-.75-.75v-3a.75.75 0 0 1 .75-.75h3a.75.75 0 0 1 0 1.5H4.43a5.001 5.001 0 0 0 8.528-2.843a.75.75 0 1 1 1.487.194A6.501 6.501 0 0 1 3.5 12.691v1.059a.75.75 0 0 1-.75.75"
    />
  </>,
);
/* gravity: arrows-rotate-right */
export const LogOut = make(
  <>
    <path
      fill="currentColor"
      fillRule="evenodd"
      clipRule="evenodd"
      d="M14.78 7.47a.75.75 0 0 1 0 1.06l-2.5 2.5a.75.75 0 1 1-1.06-1.06l1.22-1.22H4.75a.75.75 0 0 1 0-1.5h7.69l-1.22-1.22a.75.75 0 0 1 1.06-1.06zM9.5 4.25a.75.75 0 0 1-1.5 0V4a1.5 1.5 0 0 0-1.5-1.5H4A1.5 1.5 0 0 0 2.5 4v8A1.5 1.5 0 0 0 4 13.5h2.5A1.5 1.5 0 0 0 8 12v-.25a.75.75 0 0 1 1.5 0V12a3 3 0 0 1-3 3H4a3 3 0 0 1-3-3V4a3 3 0 0 1 3-3h2.5a3 3 0 0 1 3 3z"
    />
  </>,
);
/* gravity: arrow-right-from-square */
export const Mail = make(
  <>
    <path
      fill="currentColor"
      fillRule="evenodd"
      clipRule="evenodd"
      d="M3.5 4h9c.25 0 .485.06.692.169L8.75 7.5a1.25 1.25 0 0 1-1.5 0L2.808 4.169C3.015 4.06 3.251 4 3.5 4M2.001 5.438L2 5.5v5A1.5 1.5 0 0 0 3.5 12h9a1.5 1.5 0 0 0 1.5-1.5v-5l-.001-.062L9.65 8.7a2.75 2.75 0 0 1-3.3 0zM.5 5.5a3 3 0 0 1 3-3h9a3 3 0 0 1 3 3v5a3 3 0 0 1-3 3h-9a3 3 0 0 1-3-3z"
    />
  </>,
);
/* gravity: envelope */
export const Minus = make(
  <>
    <path
      fill="currentColor"
      fillRule="evenodd"
      clipRule="evenodd"
      d="M1.75 8a.75.75 0 0 1 .75-.75h11a.75.75 0 0 1 0 1.5h-11A.75.75 0 0 1 1.75 8"
    />
  </>,
);
/* gravity: minus */
export const Paintbrush = make(
  <>
    <path
      fill="currentColor"
      fillRule="evenodd"
      clipRule="evenodd"
      d="M8.135.693A1.003 1.003 0 0 1 9.567.486l.091.082l5.559 5.559c.39.39.39 1.025 0 1.416l-4.332 4.332a2.003 2.003 0 0 1-2.833 0l-.383-.383c-.11-.11-.295-.084-.391.038l-2.391 3.01a2.467 2.467 0 1 1-3.464-3.464l3.014-2.389c.122-.096.148-.28.038-.39l-.312-.311a2.004 2.004 0 0 1 .001-2.834L6.917 2.4a6 6 0 0 0 1.031-1.378l.123-.224zM5.227 6.215a.5.5 0 0 0-.001.708l.311.313c.86.86.525 2.08-.167 2.629l-3.013 2.389a.963.963 0 1 0 1.353 1.352l2.39-3.01h.002c.555-.698 1.776-1.02 2.63-.167l.382.383a.5.5 0 0 0 .708 0l.416-.416L5.64 5.8zm3.835-4.12c-.307.497-.67.956-1.082 1.368L6.703 4.738L11.3 9.335l2.502-2.5l-.759-.76l-1.067 1.082a.405.405 0 0 1-.68-.384l.493-1.951z"
    />
  </>,
);
/* gravity: paintbrush */
export const Paperclip = make(
  <>
    <path
      fill="currentColor"
      fillRule="evenodd"
      clipRule="evenodd"
      d="m9.77 10.73l.01-.01l3.08-3.08a3.889 3.889 0 1 0-5.5-5.5L4.73 4.77l-.01.01l-1.667 1.666a5.303 5.303 0 0 0 7.5 7.5l3.167-3.166a.75.75 0 1 0-1.061-1.06l-3.166 3.166a3.803 3.803 0 1 1-5.379-5.379L5.33 6.291l.011-.01L8.421 3.2a2.39 2.39 0 0 1 3.38 3.378l-1.13 1.13l-.012.012l-2.995 2.994a.975.975 0 1 1-1.378-1.378L9.28 6.34a.75.75 0 0 0-1.06-1.06L5.225 8.274a2.475 2.475 0 0 0 3.5 3.5z"
    />
  </>,
);
/* gravity: paperclip */
export const Pencil = make(
  <>
    <path
      fill="currentColor"
      fillRule="evenodd"
      clipRule="evenodd"
      d="M11.423 1A3.577 3.577 0 0 1 15 4.577c0 .27-.108.53-.3.722l-.528.529l-1.971 1.971l-5.059 5.059a3 3 0 0 1-1.533.82l-2.638.528a1 1 0 0 1-1.177-1.177l.528-2.638a3 3 0 0 1 .82-1.533l5.059-5.059l2.5-2.5c.191-.191.451-.299.722-.299m-2.31 4.009l-4.91 4.91a1.5 1.5 0 0 0-.41.766l-.38 1.903l1.902-.38a1.5 1.5 0 0 0 .767-.41l4.91-4.91a2.08 2.08 0 0 0-1.88-1.88m3.098.658a3.6 3.6 0 0 0-1.878-1.879l1.28-1.28c.995.09 1.788.884 1.878 1.88z"
    />
  </>,
);
/* gravity: pencil */
export const Plus = make(
  <>
    <path
      fill="currentColor"
      fillRule="evenodd"
      clipRule="evenodd"
      d="M8 1.75a.75.75 0 0 1 .75.75v4.75h4.75a.75.75 0 0 1 0 1.5H8.75v4.75a.75.75 0 0 1-1.5 0V8.75H2.5a.75.75 0 0 1 0-1.5h4.75V2.5A.75.75 0 0 1 8 1.75"
    />
  </>,
);
/* gravity: plus */
export const Quote = make(
  <>
    <path
      fill="currentColor"
      fillRule="evenodd"
      clipRule="evenodd"
      d="M12.411 6.33A2.75 2.75 0 0 1 14.5 9v.25A2.75 2.75 0 0 1 11.75 12h-.25a2.747 2.747 0 0 1-2.748-2.657V9.34H8.75V9q0-.197.027-.386q.03-.392.09-.77a8 8 0 0 1 .559-1.918a7.2 7.2 0 0 1 2.162-2.801l.098-.076A.24.24 0 0 1 11.83 3c.186 0 .306.202.22.367a19 19 0 0 0-.22.433a18 18 0 0 0-.43.951a14 14 0 0 0-.557 1.578l.054-.013a2.8 2.8 0 0 1 .603-.066h.25q.343.001.661.08m.549-1.405A4.25 4.25 0 0 1 16 9v.25a4.25 4.25 0 0 1-4.25 4.25h-.25A4.25 4.25 0 0 1 8 11.662A4.25 4.25 0 0 1 4.5 13.5h-.25A4.25 4.25 0 0 1 0 9.336V9q0-.275.035-.543c.207-2.62 1.358-4.966 3.488-6.599A1.74 1.74 0 0 1 4.58 1.5c1.341 0 2.146 1.425 1.548 2.564c-.111.211-.26.508-.418.86c.788.234 1.481.69 2.005 1.297a8.76 8.76 0 0 1 3.058-4.363A1.74 1.74 0 0 1 11.83 1.5c1.341 0 2.146 1.425 1.548 2.564c-.111.211-.26.508-.418.86M5.16 6.33a2.8 2.8 0 0 0-.661-.08h-.25a2.8 2.8 0 0 0-.657.079a14 14 0 0 1 .68-1.865A18 18 0 0 1 4.8 3.367A.25.25 0 0 0 4.58 3a.24.24 0 0 0-.144.049a8 8 0 0 0-.93.844a7.2 7.2 0 0 0-1.39 2.172a8 8 0 0 0-.498 1.779q-.062.378-.091.77A3 3 0 0 0 1.5 9v.339h.001v.004A2.747 2.747 0 0 0 4.25 12h.251a2.75 2.75 0 0 0 2.75-2.75V9c0-1.29-.89-2.374-2.089-2.67"
    />
  </>,
);
/* gravity: quote-open */
export const Redo2 = make(
  <>
    <path
      fill="currentColor"
      fillRule="evenodd"
      clipRule="evenodd"
      d="M8 1.5a6.5 6.5 0 1 0 6.445 7.348a.75.75 0 1 0-1.487-.194A5.001 5.001 0 1 1 11.57 4.5h-1.32a.75.75 0 0 0 0 1.5h3a.75.75 0 0 0 .75-.75v-3a.75.75 0 0 0-1.5 0v1.06A6.48 6.48 0 0 0 8 1.5"
    />
  </>,
);
/* gravity: arrow-rotate-right */
export const RefreshCw = make(
  <>
    <path
      fill="currentColor"
      fillRule="evenodd"
      clipRule="evenodd"
      d="M8 1.5a6.5 6.5 0 0 0-6.445 5.649a.75.75 0 1 0 1.488.194A5.001 5.001 0 0 1 11.57 4.5h-1.32a.75.75 0 0 0 0 1.5h3a.75.75 0 0 0 .75-.75v-3a.75.75 0 0 0-1.5 0v1.06A6.48 6.48 0 0 0 8 1.5m-5.25 13a.75.75 0 0 1-.75-.75v-3a.75.75 0 0 1 .75-.75h3a.75.75 0 0 1 0 1.5H4.43a5.001 5.001 0 0 0 8.528-2.843a.75.75 0 1 1 1.487.194A6.501 6.501 0 0 1 3.5 12.691v1.059a.75.75 0 0 1-.75.75"
    />
  </>,
);
/* gravity: arrows-rotate-right */
export const Search = make(
  <>
    <path
      fill="currentColor"
      fillRule="evenodd"
      clipRule="evenodd"
      d="M11.5 7a4.5 4.5 0 1 1-9 0a4.5 4.5 0 0 1 9 0m-.82 4.74a6 6 0 1 1 1.06-1.06l2.79 2.79a.75.75 0 1 1-1.06 1.06z"
    />
  </>,
);
/* gravity: magnifier */
export const SlidersHorizontal = make(
  <>
    <path
      fill="currentColor"
      fillRule="evenodd"
      clipRule="evenodd"
      d="M1 3.25a.75.75 0 0 1 .75-.75h12.5a.75.75 0 0 1 0 1.5H1.75A.75.75 0 0 1 1 3.25M3 8a.75.75 0 0 1 .75-.75h8.5a.75.75 0 0 1 0 1.5h-8.5A.75.75 0 0 1 3 8m3.75 4a.75.75 0 0 0 0 1.5h2.5a.75.75 0 0 0 0-1.5z"
    />
  </>,
);
/* gravity: bars-descending-align-center */
export const Sparkles = make(
  <>
    <path
      fill="currentColor"
      fillRule="evenodd"
      clipRule="evenodd"
      d="M13 10a.75.75 0 0 1 .725.556a2.37 2.37 0 0 0 1.72 1.72a.75.75 0 0 1 0 1.449a2.37 2.37 0 0 0-1.72 1.72a.75.75 0 0 1-1.45 0a2.37 2.37 0 0 0-1.72-1.72a.75.75 0 0 1 0-1.45a2.37 2.37 0 0 0 1.72-1.72l.043-.117A.75.75 0 0 1 13 10M7 0a1.5 1.5 0 0 1 1.48 1.253c.242 1.455.696 2.364 1.3 2.968c.603.603 1.512 1.057 2.967 1.3a1.5 1.5 0 0 1 0 2.958c-1.455.243-2.364.697-2.968 1.3c-.603.604-1.057 1.513-1.3 2.968a1.5 1.5 0 0 1-2.958 0c-.243-1.455-.697-2.364-1.3-2.968c-.604-.603-1.513-1.057-2.968-1.3a1.5 1.5 0 0 1 0-2.958c1.455-.243 2.364-.697 2.968-1.3c.603-.604 1.057-1.513 1.3-2.968l.028-.133A1.5 1.5 0 0 1 7 0m0 1.5C6.45 4.8 4.8 6.45 1.5 7c3.3.55 4.95 2.2 5.5 5.5c.55-3.3 2.2-4.95 5.5-5.5C9.2 6.45 7.55 4.8 7 1.5"
    />
  </>,
);
/* gravity: sparkles */
export const SquareDashed = make(
  <>
    <path
      fill="currentColor"
      fillRule="evenodd"
      clipRule="evenodd"
      d="M4.5 3A1.5 1.5 0 0 0 3 4.5v1.75a.75.75 0 0 1-1.5 0V4.5a3 3 0 0 1 3-3h1.75a.75.75 0 0 1 0 1.5zM9 2.25a.75.75 0 0 1 .75-.75h1.75a3 3 0 0 1 3 3v1.75a.75.75 0 0 1-1.5 0V4.5A1.5 1.5 0 0 0 11.5 3H9.75A.75.75 0 0 1 9 2.25M2.25 9a.75.75 0 0 1 .75.75v1.75A1.5 1.5 0 0 0 4.5 13h1.75a.75.75 0 0 1 0 1.5H4.5a3 3 0 0 1-3-3V9.75A.75.75 0 0 1 2.25 9m11.5 0a.75.75 0 0 1 .75.75v1.75a3 3 0 0 1-3 3H9.75a.75.75 0 0 1 0-1.5h1.75a1.5 1.5 0 0 0 1.5-1.5V9.75a.75.75 0 0 1 .75-.75"
    />
  </>,
);
/* gravity: square-dashed */
export const Strikethrough = make(
  <>
    <path
      fill="currentColor"
      fillRule="evenodd"
      clipRule="evenodd"
      d="M5.502 2.757C6.214 2.236 7.122 2 7.984 2c1.685 0 3.015.572 3.687 1.915a.75.75 0 1 1-1.342.67C10.001 3.93 9.331 3.5 7.984 3.5c-.611 0-1.19.17-1.597.468c-.384.28-.627.678-.627 1.242c0 .403.165.758.463 1.04H4.447a2.9 2.9 0 0 1-.187-1.04c0-1.084.507-1.916 1.242-2.453m6.047 5.993h1.201a.75.75 0 0 0 0-1.5h-9.5a.75.75 0 0 0 0 1.5h5.475l.043.012h.002c1.196.323 1.98 1.005 1.98 1.988c0 .669-.289 1.063-.742 1.33c-.5.296-1.222.437-2 .437c-1.398 0-2.453-.472-2.796-1.504a.75.75 0 1 0-1.424.474c.657 1.969 2.602 2.53 4.22 2.53c.915 0 1.94-.16 2.762-.644c.869-.512 1.48-1.376 1.48-2.623c0-.822-.276-1.481-.7-2"
    />
  </>,
);
/* gravity: strikethrough */
export const Target = make(
  <>
    <path
      fill="currentColor"
      fillRule="evenodd"
      clipRule="evenodd"
      d="M8 13.5a5.5 5.5 0 1 0 0-11a5.5 5.5 0 0 0 0 11M8 15A7 7 0 1 0 8 1a7 7 0 0 0 0 14m0-4.5a2.5 2.5 0 1 0 0-5a2.5 2.5 0 0 0 0 5M8 12a4 4 0 1 0 0-8a4 4 0 0 0 0 8m0-3a1 1 0 1 0 0-2a1 1 0 0 0 0 2"
    />
  </>,
);
/* gravity: target */
export const Trash2 = make(
  <>
    <path
      fill="currentColor"
      fillRule="evenodd"
      clipRule="evenodd"
      d="M9 2H7a.5.5 0 0 0-.5.5V3h3v-.5A.5.5 0 0 0 9 2m2 1v-.5a2 2 0 0 0-2-2H7a2 2 0 0 0-2 2V3H2.251a.75.75 0 0 0 0 1.5h.312l.317 7.625A3 3 0 0 0 5.878 15h4.245a3 3 0 0 0 2.997-2.875l.318-7.625h.312a.75.75 0 0 0 0-1.5zm.936 1.5H4.064l.315 7.562A1.5 1.5 0 0 0 5.878 13.5h4.245a1.5 1.5 0 0 0 1.498-1.438zm-6.186 2v5a.75.75 0 0 0 1.5 0v-5a.75.75 0 0 0-1.5 0m3.75-.75a.75.75 0 0 1 .75.75v5a.75.75 0 0 1-1.5 0v-5a.75.75 0 0 1 .75-.75"
    />
  </>,
);
/* gravity: trash-bin */
export const TriangleAlert = make(
  <>
    <path
      fill="currentColor"
      fillRule="evenodd"
      clipRule="evenodd"
      d="M7.134 2.994L2.217 11.5a1 1 0 0 0 .866 1.5h9.834a1 1 0 0 0 .866-1.5L8.866 2.993a1 1 0 0 0-1.732 0m3.03-.75c-.962-1.665-3.366-1.665-4.329 0L.918 10.749c-.963 1.666.24 3.751 2.165 3.751h9.834c1.925 0 3.128-2.085 2.164-3.751zM8 5a.75.75 0 0 1 .75.75v2a.75.75 0 0 1-1.5 0v-2A.75.75 0 0 1 8 5m1 5.75a1 1 0 1 1-2 0a1 1 0 0 1 2 0"
    />
  </>,
);
/* gravity: triangle-exclamation */
export const Trophy = make(
  <>
    <path
      fill="currentColor"
      fillRule="evenodd"
      clipRule="evenodd"
      d="M11.5 3.5h2a.5.5 0 0 1 .5.5v8a.5.5 0 0 1-.5.5h-2a.5.5 0 0 1-.5-.5V4a.5.5 0 0 1 .5-.5m-2.5 9a.5.5 0 0 0 .5-.5V7a.5.5 0 0 0-.5-.5H7a.5.5 0 0 0-.5.5v5a.5.5 0 0 0 .5.5zm-4.5 0A.5.5 0 0 0 5 12v-2a.5.5 0 0 0-.5-.5h-2a.5.5 0 0 0-.5.5v2a.5.5 0 0 0 .5.5zm-1 1.5h-1a2 2 0 0 1-2-2v-2a2 2 0 0 1 2-2h2q.26 0 .5.063V7a2 2 0 0 1 2-2h2q.26 0 .5.063V4a2 2 0 0 1 2-2h2a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2z"
    />
  </>,
);
/* gravity: chart-column */
export const Type = make(
  <>
    <path
      fill="currentColor"
      fillRule="evenodd"
      clipRule="evenodd"
      d="M8 2.25c-.618 0-1.169.39-1.373.974l-3.335 9.528a.75.75 0 0 0 1.416.496L5.845 10h4.31l1.137 3.248a.75.75 0 0 0 1.416-.496L9.373 3.224A1.455 1.455 0 0 0 8 2.25M9.63 8.5L8 3.842L6.37 8.5z"
    />
  </>,
);
/* gravity: font */
export const Underline = make(
  <>
    <path
      fill="currentColor"
      fillRule="evenodd"
      clipRule="evenodd"
      d="M5 2.75a.75.75 0 0 0-1.5 0V7a4.5 4.5 0 0 0 9 0V2.75a.75.75 0 0 0-1.5 0V7a3 3 0 0 1-6 0zm-.75 9.75a.75.75 0 0 0 0 1.5h7.5a.75.75 0 0 0 0-1.5z"
    />
  </>,
);
/* gravity: underline */
export const Undo2 = make(
  <>
    <path
      fill="currentColor"
      fillRule="evenodd"
      clipRule="evenodd"
      d="M8 1.5a6.5 6.5 0 1 1-6.445 7.348a.75.75 0 1 1 1.487-.194A5.001 5.001 0 1 0 4.43 4.5h1.32a.75.75 0 0 1 0 1.5h-3A.75.75 0 0 1 2 5.25v-3a.75.75 0 0 1 1.5 0v1.06A6.48 6.48 0 0 1 8 1.5"
    />
  </>,
);
/* gravity: arrow-rotate-left */
export const X = make(
  <>
    <path
      fill="currentColor"
      fillRule="evenodd"
      clipRule="evenodd"
      d="M3.47 3.47a.75.75 0 0 1 1.06 0L8 6.94l3.47-3.47a.75.75 0 1 1 1.06 1.06L9.06 8l3.47 3.47a.75.75 0 1 1-1.06 1.06L8 9.06l-3.47 3.47a.75.75 0 0 1-1.06-1.06L6.94 8L3.47 4.53a.75.75 0 0 1 0-1.06"
    />
  </>,
);
/* gravity: xmark */
export const ZoomIn = make(
  <>
    <path
      fill="currentColor"
      fillRule="evenodd"
      clipRule="evenodd"
      d="M6.75 11a4.25 4.25 0 1 0 0-8.5a4.25 4.25 0 0 0 0 8.5m0 1.5a5.73 5.73 0 0 0 3.501-1.188l2.719 2.718a.75.75 0 1 0 1.06-1.06l-2.718-2.719A5.75 5.75 0 1 0 6.75 12.5m.75-7.75a.75.75 0 0 0-1.5 0V6H4.75a.75.75 0 0 0 0 1.5H6v1.25a.75.75 0 0 0 1.5 0V7.5h1.25a.75.75 0 0 0 0-1.5H7.5z"
    />
  </>,
);
/* gravity: magnifier-plus */
export const ZoomOut = make(
  <>
    <path
      fill="currentColor"
      fillRule="evenodd"
      clipRule="evenodd"
      d="M6.75 11a4.25 4.25 0 1 0 0-8.5a4.25 4.25 0 0 0 0 8.5m0 1.5a5.73 5.73 0 0 0 3.501-1.188l2.719 2.718a.75.75 0 1 0 1.06-1.06l-2.718-2.719A5.75 5.75 0 1 0 6.75 12.5m-2-6.5a.75.75 0 0 0 0 1.5h4a.75.75 0 0 0 0-1.5z"
    />
  </>,
);
/* gravity: magnifier-minus */
export const TrendingUp = make(
  <>
    <path
      fill="currentColor"
      fillRule="evenodd"
      clipRule="evenodd"
      d="M14.75 12.5a.75.75 0 0 1 0 1.5H1.25a.75.75 0 0 1 0-1.5zm-.5-10a.75.75 0 0 1 .75.75V6.5a.75.75 0 0 1-1.5 0V5.06l-3.241 3.242a2.25 2.25 0 0 1-2.505.465L5.335 7.69a.75.75 0 0 0-.923.261l-2.044 2.973a.75.75 0 0 1-1.236-.85l2.044-2.972a2.25 2.25 0 0 1 2.767-.782l2.42 1.075a.75.75 0 0 0 .835-.155L12.44 4H11a.75.75 0 0 1 0-1.5z"
    />
  </>,
);
/* gravity: chart-line-arrow-up */

// ── The former hand-drawn brand glyphs, now Gravity like the rest ─────────────────
export const SmileFilled = make(
  <>
    <path
      fill="currentColor"
      fillRule="evenodd"
      clipRule="evenodd"
      d="M13.5 8a5.5 5.5 0 1 1-11 0a5.5 5.5 0 0 1 11 0M15 8A7 7 0 1 1 1 8a7 7 0 0 1 14 0M6.67 9.665a.75.75 0 0 0-1.34.67c.403.809 1.452 1.415 2.67 1.415s2.267-.606 2.67-1.415a.75.75 0 1 0-1.34-.67c-.097.191-.548.585-1.33.585s-1.233-.394-1.33-.585M10 8a.75.75 0 0 1-.75-.75v-1a.75.75 0 0 1 1.5 0v1A.75.75 0 0 1 10 8m-4.75-.75a.75.75 0 0 0 1.5 0v-1a.75.75 0 0 0-1.5 0z"
    />
  </>,
);
/* gravity: face-smile */
export const FrownFilled = make(
  <>
    <path
      fill="currentColor"
      fillRule="evenodd"
      clipRule="evenodd"
      d="M13.5 8a5.5 5.5 0 1 1-11 0a5.5 5.5 0 0 1 11 0M15 8A7 7 0 1 1 1 8a7 7 0 0 1 14 0m-5.67 2.835a.75.75 0 1 0 1.34-.67C10.268 9.356 9.219 8.75 8 8.75s-2.267.606-2.67 1.415a.75.75 0 1 0 1.34.67c.097-.191.548-.585 1.33-.585s1.233.394 1.33.585M10 8a.75.75 0 0 1-.75-.75v-1a.75.75 0 0 1 1.5 0v1A.75.75 0 0 1 10 8m-4.75-.75a.75.75 0 0 0 1.5 0v-1a.75.75 0 0 0-1.5 0z"
    />
  </>,
);
/* gravity: face-sad */
export const Kebab = make(
  <>
    <path
      fill="currentColor"
      fillRule="evenodd"
      clipRule="evenodd"
      d="M3 9.5a1.5 1.5 0 1 0 0-3a1.5 1.5 0 0 0 0 3M9.5 8a1.5 1.5 0 1 1-3 0a1.5 1.5 0 0 1 3 0m5 0a1.5 1.5 0 1 1-3 0a1.5 1.5 0 0 1 3 0"
    />
  </>,
);
/* gravity: ellipsis */
export const ChevronDownSolid = make(
  <>
    <path
      fill="currentColor"
      fillRule="evenodd"
      clipRule="evenodd"
      d="M4.177 6.705A.73.73 0 0 1 4.729 5.5h6.542a.73.73 0 0 1 .552 1.205L8.8 10.214a1 1 0 0 1-.757.347h-.084a1 1 0 0 1-.757-.347z"
    />
  </>,
);
/* gravity: caret-down */
export const KebabVertical = make(
  <>
    <path
      fill="currentColor"
      fillRule="evenodd"
      clipRule="evenodd"
      d="M8 4.5a1.5 1.5 0 1 0 0-3a1.5 1.5 0 0 0 0 3M9.5 8a1.5 1.5 0 1 1-3 0a1.5 1.5 0 0 1 3 0m0 5a1.5 1.5 0 1 1-3 0a1.5 1.5 0 0 1 3 0"
    />
  </>,
);
/* gravity: ellipsis-vertical */
export const LinkGlobe = make(
  <>
    <path
      fill="currentColor"
      fillRule="evenodd"
      clipRule="evenodd"
      d="M9.208 12.346c-.485 1-.953 1.154-1.208 1.154s-.723-.154-1.208-1.154c-.372-.768-.647-1.858-.749-3.187a21 21 0 0 0 3.914 0c-.102 1.329-.377 2.419-.75 3.187m.788-4.699C9.358 7.714 8.69 7.75 8 7.75s-1.358-.036-1.996-.103c.037-1.696.343-3.075.788-3.993C7.277 2.654 7.745 2.5 8 2.5s.723.154 1.208 1.154c.445.918.75 2.297.788 3.993m1.478 1.306c-.085 1.516-.375 2.848-.836 3.874a5.5 5.5 0 0 0 2.843-4.364c-.621.199-1.295.364-2.007.49m1.918-2.043c-.572.204-1.21.379-1.901.514c-.056-1.671-.354-3.14-.853-4.251a5.5 5.5 0 0 1 2.754 3.737m-8.883.514c.056-1.671.354-3.14.853-4.251A5.5 5.5 0 0 0 2.608 6.91c.572.204 1.21.379 1.901.514M2.52 8.463a5.5 5.5 0 0 0 2.843 4.364c-.46-1.026-.75-2.358-.836-3.874a15.5 15.5 0 0 1-2.007-.49M15 8A7 7 0 1 0 1 8a7 7 0 0 0 14 0"
    />
  </>,
);
/* gravity: globe */
export const OpenNewWindow = make(
  <>
    <path
      fill="currentColor"
      fillRule="evenodd"
      clipRule="evenodd"
      d="M10 1.5A.75.75 0 0 0 10 3h1.94L6.97 7.97a.75.75 0 0 0 1.06 1.06L13 4.06V6a.75.75 0 0 0 1.5 0V2.25a.75.75 0 0 0-.75-.75zM7.5 3.25a.75.75 0 0 0-.75-.75H4.5a3 3 0 0 0-3 3v6a3 3 0 0 0 3 3h6a3 3 0 0 0 3-3V9.25a.75.75 0 0 0-1.5 0v2.25a1.5 1.5 0 0 1-1.5 1.5h-6A1.5 1.5 0 0 1 3 11.5v-6A1.5 1.5 0 0 1 4.5 4h2.25a.75.75 0 0 0 .75-.75"
    />
  </>,
);
/* gravity: arrow-up-right-from-square */
export const GrowSparkles = make(
  <>
    <path
      fill="currentColor"
      fillRule="evenodd"
      clipRule="evenodd"
      d="M13 10a.75.75 0 0 1 .725.556a2.37 2.37 0 0 0 1.72 1.72a.75.75 0 0 1 0 1.449a2.37 2.37 0 0 0-1.72 1.72a.75.75 0 0 1-1.45 0a2.37 2.37 0 0 0-1.72-1.72a.75.75 0 0 1 0-1.45a2.37 2.37 0 0 0 1.72-1.72l.043-.117A.75.75 0 0 1 13 10M7 0a1 1 0 0 1 .986.836c.279 1.67.815 2.8 1.596 3.582c.781.781 1.912 1.317 3.582 1.596a1 1 0 0 1 0 1.972c-1.67.279-2.8.815-3.582 1.596c-.781.781-1.317 1.912-1.596 3.582a1 1 0 0 1-1.972 0c-.279-1.67-.815-2.8-1.596-3.582c-.781-.781-1.912-1.317-3.582-1.596a1 1 0 0 1 0-1.972c1.67-.279 2.8-.815 3.582-1.596c.781-.781 1.317-1.912 1.596-3.582l.018-.089A1 1 0 0 1 7 0"
    />
  </>,
);
/* gravity: sparkles-fill */
export const LinkFilled = make(
  <>
    <path
      fill="currentColor"
      fillRule="evenodd"
      clipRule="evenodd"
      d="M3.47 6.53a.75.75 0 0 1 1.06 1.061l-.727.727a2.743 2.743 0 0 0 3.879 3.879l.727-.727a.75.75 0 0 1 1.06 1.06l-.726.727a4.243 4.243 0 0 1-6-6zm8 1.879a.75.75 0 0 0 1.06 1.06l.727-.726a4.243 4.243 0 0 0-6-6l-.727.727a.75.75 0 0 0 1.061 1.06l.727-.727a2.743 2.743 0 0 1 3.879 3.879zm-.94-1.879a.75.75 0 1 0-1.06-1.06l-4 4a.75.75 0 1 0 1.06 1.06z"
    />
  </>,
);
/* gravity: link */
export const LockFilled = make(
  <>
    <path
      fill="currentColor"
      fillRule="evenodd"
      clipRule="evenodd"
      d="M8 1a4 4 0 0 1 4 4v1l.154.004A3 3 0 0 1 15 9v3a3 3 0 0 1-3 3H4a3 3 0 0 1-3-3V9a3 3 0 0 1 2.846-2.996L4 6V5a4 4 0 0 1 4-4m0 7.75a.75.75 0 0 0-.75.75v2a.75.75 0 0 0 1.5 0v-2A.75.75 0 0 0 8 8.75M8 2.5A2.5 2.5 0 0 0 5.5 5v1h5V5A2.5 2.5 0 0 0 8 2.5"
    />
  </>,
);
/* gravity: lock-fill — **solid, and deliberately so** (owner, 2026-08-21). It used to draw the
   outline `lock`, which meant the two states of the padlock differed only in whether the shackle
   was hanging open: at 16px that is a couple of pixels and you had to look for it. The pair is now
   solid-when-closed against outline-when-open, so "locked" reads at a glance.

   This is the sanctioned exception to "never a solid mark in a column of outline ones" — the same
   one `FolderOpen` / `FolderOpenFilled` uses. The filled twin marks the ON state of a toggle beside
   its own outline sibling; it is not a solid glyph sitting in a row of unrelated outline ones. */
export const LockOpenFilled = make(
  <>
    <path
      fill="currentColor"
      fillRule="evenodd"
      clipRule="evenodd"
      d="M10.5 5a2.5 2.5 0 0 0-4.532-1.456c-.242.336-.66.559-1.052.428c-.393-.131-.611-.56-.41-.922A4 4 0 0 1 12 5v1h.001a3 3 0 0 1 3 3v3a3 3 0 0 1-3 3H4a3 3 0 0 1-3-3V9a3 3 0 0 1 3-3h6.5zm.75 2.5H4A1.5 1.5 0 0 0 2.5 9v3A1.5 1.5 0 0 0 4 13.5h8a1.5 1.5 0 0 0 1.5-1.5V9A1.5 1.5 0 0 0 12 7.5zM8 8.75a.75.75 0 0 1 .75.75v2a.75.75 0 0 1-1.5 0v-2A.75.75 0 0 1 8 8.75"
    />
  </>,
);
/* gravity: lock-open */
export const User = make(
  <>
    <path
      fill="currentColor"
      fillRule="evenodd"
      clipRule="evenodd"
      d="M8 8.5c3.85 0 7 2.5 7 4.5a2 2 0 0 1-2 2H3a2 2 0 0 1-2-2c0-2 3.15-4.5 7-4.5M8 10c-1.61 0-3.064.526-4.092 1.234C2.798 12.001 2.5 12.733 2.5 13a.5.5 0 0 0 .5.5h10a.5.5 0 0 0 .5-.5c0-.267-.297-1-1.408-1.766C11.064 10.526 9.609 10 8 10m0-9a3.5 3.5 0 1 1 0 7a3.5 3.5 0 0 1 0-7m0 1.5a2 2 0 1 0 0 4a2 2 0 0 0 0-4"
    />
  </>,
);
/* gravity: person */
export const Filter = make(
  <>
    <path
      fill="currentColor"
      fillRule="evenodd"
      clipRule="evenodd"
      d="M1 3.25a.75.75 0 0 1 .75-.75h12.5a.75.75 0 0 1 0 1.5H1.75A.75.75 0 0 1 1 3.25M3 8a.75.75 0 0 1 .75-.75h8.5a.75.75 0 0 1 0 1.5h-8.5A.75.75 0 0 1 3 8m3.75 4a.75.75 0 0 0 0 1.5h2.5a.75.75 0 0 0 0-1.5z"
    />
  </>,
);
/* gravity: bars-descending-align-center */
export const SortAscending = make(
  <>
    <path
      fill="currentColor"
      fillRule="evenodd"
      clipRule="evenodd"
      d="M2.22 2.72a.75.75 0 0 1 1.06 0l2 2a.75.75 0 0 1-1.06 1.06l-.72-.72v7.69a.75.75 0 0 1-1.5 0V5.06l-.72.72A.75.75 0 0 1 .22 4.72zM7 12.75c0 .414.336.75.75.75h7.5a.75.75 0 0 0 0-1.5h-7.5a.75.75 0 0 0-.75.75m.75-4a.75.75 0 0 1 0-1.5h5.5a.75.75 0 0 1 0 1.5zm0-4.75a.75.75 0 0 1 0-1.5h2.5a.75.75 0 0 1 0 1.5z"
    />
  </>,
);
/* gravity: bars-ascending-align-left-arrow-up */
export const SortDescending = make(
  <>
    <path
      fill="currentColor"
      fillRule="evenodd"
      clipRule="evenodd"
      d="M2.22 13.28a.75.75 0 0 0 1.06 0l2-2a.75.75 0 1 0-1.06-1.06l-.72.72V3.25a.75.75 0 0 0-1.5 0v7.69l-.72-.72a.75.75 0 1 0-1.06 1.06zM7 3.25a.75.75 0 0 1 .75-.75h7.5a.75.75 0 0 1 0 1.5h-7.5A.75.75 0 0 1 7 3.25m.75 4a.75.75 0 0 0 0 1.5h5.5a.75.75 0 0 0 0-1.5zm0 4.75a.75.75 0 0 0 0 1.5h2.5a.75.75 0 0 0 0-1.5z"
    />
  </>,
);
/* gravity: bars-descending-align-left-arrow-down */
