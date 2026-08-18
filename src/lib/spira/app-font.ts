/**
 * The app's body face, switchable at runtime — the Settings page's **Fonts** tab (GRO-122).
 *
 * The owner is choosing Spira's sans and wants to judge each candidate **in the product**, not on
 * a specimen sheet: a face that looks handsome in a paragraph of Lorem can fall apart in a goal
 * card, a badge and a 10px label. So the choice applies to the whole app at once and survives a
 * reload.
 *
 * It swaps the **body** font only. GCentra is the current body face and the list is a list of
 * replacements for it; headings stay on ITC Clearface, which is not what is being chosen here.
 *
 * The Android twin is `ui/theme/AppFont.kt` — keep the two lists in step, or the same phone and
 * the same laptop will disagree about what "Guidy" looks like.
 */

import { useEffect } from "react";
import { create } from "zustand";
import { persist, createJSONStorage } from "zustand/middleware";

export type AppFontId =
  | "gcentra"
  | "tilda-sans"
  | "pt-root-ui"
  | "fixel"
  | "garet"
  | "bartina"
  | "liberation-sans"
  | "dejavu-sans"
  | "archivo"
  | "arimo"
  | "ibm-plex-sans"
  | "onest"
  | "golos-text"
  | "montserrat"
  | "futura"
  | "futura-futuris";

export type AppFontChoice = {
  id: AppFontId;
  /** What the tab calls it. */
  label: string;
  /** The `font-family` stack, minus the shared system fallbacks appended below. */
  family: string;
  /** One line on where it came from / what it is, so a name in a list means something. */
  note: string;
  /**
   * Whether the file carries the Russian alphabet.
   *
   * GCentra does **not** — nor do most of the first shortlist — so Cyrillic text in them is drawn
   * by the system sans and the row is showing two fonts at once. The tab says so rather than
   * letting the owner compare a face against Roboto by accident.
   */
  cyrillic?: boolean;
};

/** The system faces every choice falls back to, so a failed download never leaves blank text. */
const FALLBACKS =
  '-apple-system, BlinkMacSystemFont, "Segoe UI", Helvetica, Arial, sans-serif';

export const APP_FONTS: AppFontChoice[] = [
  {
    id: "gcentra",
    label: "GCentra",
    family: "GCentra",
    note: "The current body face — Book and Medium. Latin only: Russian falls back to the system sans.",
  },
  {
    id: "tilda-sans",
    label: "Tilda Sans",
    family: '"Tilda Sans"',
    note: "ParaType/Tilda. Geometric grotesque, Cyrillic for 140+ languages.",
    cyrillic: true,
  },
  {
    id: "pt-root-ui",
    label: "PT Root UI",
    family: '"PT Root UI"',
    note: "ParaType. Geometric with a humanist bend and open apertures — the closest here to GCentra.",
    cyrillic: true,
  },
  {
    id: "fixel",
    label: "Fixel",
    family: "Fixel",
    note: "MacPaw. Geometric-humanist; this is the Text cut, drawn for running text.",
    cyrillic: true,
  },
  {
    id: "garet",
    label: "Garet",
    family: "Garet",
    note: "Type Forward. Geometric, tall x-height, closed oval shapes. Book and Heavy only, so its bold is heavy.",
    cyrillic: true,
  },
  {
    id: "bartina",
    label: "Bartina",
    family: "Bartina",
    note: "Geometric grotesque, five weights Thin–Bold.",
    cyrillic: true,
  },
  {
    id: "liberation-sans",
    label: "Liberation Sans",
    family: '"Liberation Sans"',
    note: "Red Hat / SIL. Neo-grotesque, metric-compatible with Arial. Not on Google Fonts.",
    cyrillic: true,
  },
  {
    id: "dejavu-sans",
    label: "DejaVu Sans",
    family: '"DejaVu Sans"',
    note: "Humanist grotesque in the Verdana mould. Not on Google Fonts.",
    cyrillic: true,
  },
  {
    id: "archivo",
    label: "Archivo",
    family: "Archivo",
    note: "Grotesque for headlines and small text alike.",
  },
  {
    id: "arimo",
    label: "Arimo",
    family: "Arimo",
    note: "Neo-grotesque, metric-compatible with Arial.",
    cyrillic: true,
  },
  {
    id: "ibm-plex-sans",
    label: "IBM Plex Sans",
    family: '"IBM Plex Sans"',
    note: "IBM's corporate grotesque; neutral, with a few engineered details.",
    cyrillic: true,
  },
  {
    id: "onest",
    label: "Onest",
    family: "Onest",
    note: "Contemporary geometric grotesque drawn for screens.",
    cyrillic: true,
  },
  {
    id: "golos-text",
    label: "Golos Text",
    family: '"Golos Text"',
    note: "Russian-first grotesque; its Cyrillic is the point rather than an afterthought.",
    cyrillic: true,
  },
  {
    id: "montserrat",
    label: "Montserrat",
    family: "Montserrat",
    note: "Geometric sans inspired by old Buenos Aires signage; even, wide letterforms. Cyrillic included.",
    cyrillic: true,
  },
  {
    id: "futura",
    label: "Futura (Jost*)",
    // Jost* — indestructible type's OFL revival of Futura. True Futura is proprietary and can't be
    // bundled; Jost* is the standard free stand-in and carries the geometric Futura shapes.
    family: "Jost",
    note: "Futura, via indestructible type's free Jost* revival. Geometric with a low waist. Cyrillic included.",
    cyrillic: true,
  },
  {
    id: "futura-futuris",
    label: "Futura Futuris",
    family: '"Futura Futuris"',
    // The real thing rather than the Jost* stand-in above — ParaType's Cyrillic Futura, supplied
    // by the owner. Worth having both in the list: Jost* is a revival and the two differ most in
    // exactly the small sizes this app is made of.
    note: "ParaType's Cyrillic Futura — the real face, not the Jost* revival. Set a weight lighter than it ships: Light carries the body, its regular the bold.",
    cyrillic: true,
  },
];

export function fontStack(id: AppFontId): string {
  const choice = APP_FONTS.find((f) => f.id === id) ?? APP_FONTS[0];
  return `${choice.family}, ${FALLBACKS}`;
}

type State = {
  font: AppFontId;
  setFont: (font: AppFontId) => void;
};

export const useAppFont = create<State>()(
  persist(
    (set) => ({
      // Tilda Sans is the web's working body face (the owner's pick, 2026-08-14). The Fonts tab
      // still switches freely; this is only the default a fresh device lands on.
      font: "tilda-sans",
      setFont: (font) => set({ font }),
    }),
    {
      name: "spira:app-font",
      storage: createJSONStorage(() => localStorage),
    },
  ),
);

/**
 * Writes the chosen face onto the document root.
 *
 * Both `--font-sans` and `--font-display` move: Tailwind's `font-sans` utility and the `body` rule
 * read those variables at paint time, so overriding them on `:root` re-fonts every surface at once
 * — which is the whole point of the exercise. `--font-heading` is deliberately left alone.
 *
 * Mounted once, in the app shell. It has to be an effect rather than a one-off at import time:
 * zustand's `persist` rehydrates from localStorage asynchronously, so reading the store at import
 * would reliably get the default and then never correct itself.
 */
export function useApplyAppFont() {
  const font = useAppFont((s) => s.font);
  useEffect(() => {
    if (typeof document === "undefined") return;
    const stack = fontStack(font);
    const root = document.documentElement;
    root.style.setProperty("--font-sans", stack);
    root.style.setProperty("--font-display", stack);
  }, [font]);
}
