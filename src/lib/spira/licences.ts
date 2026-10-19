/**
 * **What Spira has to say about other people's work**, and the one place it is said.
 *
 * Every drawing here is a Noun Project work under **CC BY 3.0**, whose clause 4(c) asks for the
 * author, the title and the licensor's URI to be given "in any reasonable manner" — reasonable
 * *to the medium*. The medium is an app its users never see the source of, and the credit the
 * download carries (two `<text>` lines under the drawing) is stripped in `FlowerArt.tsx` and
 * `public/favicon.svg` so the marks can be drawn at any size. So it has to be stated in the app, and
 * this is where: **Settings → Licences**.
 *
 * It is a tab of its own rather than a row on About Spira (owner, 2026-10-09). About Spira is the
 * only place the method is explained to the person being coached; a legal note does not belong in
 * the middle of it. This tab is also where anything else third-party goes as it arrives — the
 * Gravity UI icon set (MIT), the brand fonts.
 *
 * **One line per drawing that ships** — three since the bin, used everywhere (2026-10-19). An earlier Noun Project drawing — a sprout
 * (`noun_Plant_7436720.svg`) — used to head the GROW start overlay's kicker and had no credit;
 * the owner took it off the kicker on 2026-10-09 ("там ничего не нужно") and `SproutArt.tsx` was
 * deleted with it. It is in the git history if it is ever wanted back, and it would need its
 * credit line before it could ship again.
 *
 * **The tab's mark changed on 2026-10-10**: Ladang Visual's flower gave way to Firda Wahyu
 * Dianti's sprout ("Plant"), and its line went with it — a credit is owed for what ships, and the
 * flower now ships nowhere (`SpiraMark.tsx` still draws it but nothing renders that component).
 * If `SpiraMark` is ever put on screen again, its line comes back with it.
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

/** The owner supplied each line verbatim (2026-10-09; the Plant line 2026-10-10). */
export const ARTWORK_CREDITS: ArtworkCredit[] = [
  {
    work: "Flower",
    author: "Firda Wahyu Dianti",
    where: "The bloom that heads the coach's empty chat.",
    href: "https://thenounproject.com/browse/icons/term/flower/",
    licence: "CC BY 3.0",
  },
  {
    work: "Plant",
    author: "Firda Wahyu Dianti",
    where: "Spira's own mark — the sprout in the browser tab.",
    href: "https://thenounproject.com/browse/icons/term/plant/",
    licence: "CC BY 3.0",
  },
  {
    // Owner, 2026-10-19: the app's one bin. Redrawn on the icon set's 16 grid (`TrashCan`) — an
    // adaptation, which CC BY allows, and which still owes the credit.
    work: "Trash",
    author: "Gregor Cresnar",
    where: "The bin on every Delete and Clear button.",
    href: "https://thenounproject.com/browse/icons/term/trash/",
    licence: "CC BY 3.0",
  },
];
