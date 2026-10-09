/**
 * **What Spira has to say about other people's work**, and the one place it is said.
 *
 * Both flowers are Noun Project drawings under **CC BY 3.0**, whose clause 4(c) asks for the
 * author, the title and the licensor's URI to be given "in any reasonable manner" — reasonable
 * *to the medium*. The medium is an app its users never see the source of, and the credit the
 * download carries (two `<text>` lines under the drawing) is stripped in `FlowerArt.tsx` and
 * `SpiraMark.tsx` so the marks can be drawn at any size. So it has to be stated in the app, and
 * this is where: **Settings → Licences**.
 *
 * It is a tab of its own rather than a row on About Spira (owner, 2026-10-09). About Spira is the
 * only place the method is explained to the person being coached; a legal note does not belong in
 * the middle of it. This tab is also where anything else third-party goes as it arrives — the
 * Gravity UI icon set (MIT), the brand fonts.
 *
 * **Two lines, and that is the whole of it.** A third Noun Project drawing — a sprout
 * (`noun_Plant_7436720.svg`) — used to head the GROW start overlay's kicker and had no credit;
 * the owner took it off the kicker on 2026-10-09 ("там ничего не нужно") and `SproutArt.tsx` was
 * deleted with it, so there is nothing left to attribute. It is in the git history if it is ever
 * wanted back, and it would need its credit line before it could ship again.
 */

export type ArtworkCredit = {
  /** The work's title, as the licence line names it. */
  work: string;
  author: string;
  /** Where in Spira the drawing appears, so a reader can match the credit to what is on screen. */
  where: string;
  /** The URI the licensor specifies — what CC BY 4(c)(i) asks to be carried with the credit. */
  href: string;
  licence: string;
};

/** The owner supplied both lines verbatim (2026-10-09). */
export const ARTWORK_CREDITS: ArtworkCredit[] = [
  {
    work: "Flower",
    author: "Firda Wahyu Dianti",
    where: "The bloom that heads the coach's empty chat.",
    href: "https://thenounproject.com/browse/icons/term/flower/",
    licence: "CC BY 3.0",
  },
  {
    work: "Flower",
    author: "Ladang Visual",
    where: "Spira's own mark — the icon in the browser tab.",
    href: "https://thenounproject.com/browse/icons/term/flower/",
    licence: "CC BY 3.0",
  },
];
