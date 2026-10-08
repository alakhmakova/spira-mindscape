import { Link, useNavigate } from "@tanstack/react-router";
import { useState } from "react";
import {
  Award,
  CircleQuestion,
  Home,
  Gear,
  Key,
  LogOut,
  Sparkles,
  X,
} from "@/components/spira/icons";
import { useAi } from "@/components/ai/ai-store";
import { useAuth } from "@/lib/spira/auth";
import { useSpira } from "@/lib/spira/store";
import { DEFAULT_AVATAR } from "./AppShell";
import { cn } from "@/lib/utils";
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover";

/**
 * The web's standing navigation — the twin of Android's `SpiraDrawer`
 * (`GoalsDashboardScreen.kt`), rebuilt from it row for row (owner, 2026-08-23).
 *
 * Android hides the same list behind a menu glyph purely to save space on a phone; a laptop
 * has the room, so here it stays open. Below `lg` it disappears and the teal header keeps
 * doing the navigating, exactly as before.
 *
 * The measurements are the drawer's, so the two read as one design:
 *
 * | | Android | here |
 * |---|---|---|
 * | Width | 244dp | `w-[320px]` — **wider than Android's since 2026-10-07**, see below |
 * | Row padding | 20dp / 10dp | `px-5 py-2.5` |
 * | Glyph | 18dp | 18px |
 * | "You are here" | a 3dp Kale bar, 24dp tall | the same, absolutely positioned — but against the **right** edge (owner, 2026-10-07) |
 * | Sub-item rail | 3dp lane, 1.5dp hairline when not here | **gone** (owner, 2026-10-07) — a sub-item is marked by the same right-hand bar |
 * | Sub-item indent | 26dp, then 16dp to the word | `pl-[26px]`, `ml-4` |
 *
 * Three rules carried over verbatim:
 *
 *  - **The "you are here" bar is drawn behind the row, not inside it**, so showing it never
 *    shifts the icon or the label sideways.
 *  - **A rubric with sub-items is never marked "you are here" — only the open sub-item is.**
 *    The goal's name is a heading over places, not a place you can stand in.
 *  - **320px, not 244** (owner, 2026-10-07), measured off the reference she brought: its sidebar
 *    runs 0..317 with the scrollbar beside it. It went to 200 first, on the argument that no rail
 *    can show "Get a job as a Software Developer" whole so it may as well stop paying for a few
 *    more characters — the owner's answer was that 200 is simply too narrow to read. The full
 *    name is in the `title` tooltip either way. This is the one measurement that no longer
 *    matches Android's drawer.
 *  - **There is no icon-only rail** (owner, 2026-10-07: "должно вообще исчезать без сворачивания
 *    до иконок"). The rail used to narrow to 64px of glyphs whenever the coach opened, so the two
 *    could share the left edge. It is one width now and the menu mark in the header takes it away
 *    entirely — a strip of unlabelled icons is a worse answer than no strip, because it keeps the
 *    space while losing the words.
 *  - **There is no rail at all any more** (owner, 2026-10-07). It was per item rather than one
 *    line down the side, precisely so each stretch could be its own colour — but down a list of
 *    goals those stretches still read as one ragged vertical line beside the words. Every "you
 *    are here" in this rail is now the same thing: one rounded Kale bar against the right edge,
 *    drawn behind the row so showing it never nudges the label.
 *
 * Deliberately absent: **Calendar** (the drawer has no such row — the dashboard offers it as a
 * view tab) and **Knowledge** (Android's is an unwired placeholder, and copying dead rows onto
 * a second surface would just double the dead ends).
 *
 * **Collapses to an icon-only rail while the coach is open** (owner, 2026-09-03, reverting the
 * coach to the LEFT column: "боковое меню сворачивается до полосы с иконками" — "the side menu
 * collapses to a strip of icons"). This is what the 2026-08-23 move to the right was avoiding —
 * "the AI panel used to be the left column, which left nowhere for standing navigation to sit
 * without the two shoving each other" (`AppShell.tsx`) — solved properly this time instead of by
 * relocating the panel: the destinations stay exactly the same, sub-item lists (which need room
 * for text) fold into their parent row, and every row keeps a `title` tooltip so nothing is lost,
 * only narrowed.
 */
export function SideNav({
  path,
  goalId,
  onOpenChat,
  onOpenKeys,
}: {
  path: string;
  /** The open goal, so its row in the goals list can mark itself. */
  goalId?: string;
  /**
   * Brings the coach back and puts this rail away — the two share the left edge and only one
   * of them is on screen at a time (owner, 2026-10-07). `AppShell` owns both halves of that,
   * so the rail asks rather than reaching for the panel itself.
   */
  onOpenChat: () => void;
  /** The same, for the coach's key sheet — it is one of the panel's own surfaces. */
  onOpenKeys: () => void;
}) {
  const user = useAuth((s) => s.user);
  const logout = useAuth((s) => s.logout);
  const navigate = useNavigate();
  const goals = useSpira((s) => s.goals);
  // A chat that is open but parked behind this rail — see `collapsed` in `ai-store`. The mark
  // below pulses while it is, because nothing else on screen says where the conversation went.
  const chatParked = useAi((s) => s.isOpen && s.collapsed);
  const [emailOpen, setEmailOpen] = useState(false);
  // The coach sits directly to this rail's right (owner, 2026-09-03) — the two would otherwise
  // fight for the same edge of the screen, so the rail narrows to icons while it's open instead.
  const goToAbout = () =>
    void navigate({ to: "/settings", search: { tab: "about" } });

  return (
    <aside
      aria-label="Main"
      className={cn(
        "sticky top-16 z-30 hidden h-[calc(100vh-4rem)] shrink-0 flex-col border-r hairline bg-card transition-[width] duration-150 lg:flex",
        "w-[223px]",
      )}
    >
      <div className="flex min-h-0 flex-1 flex-col overflow-y-auto pb-4 pt-[14px]">
        <nav>
          {/* **Home goes to the dashboard; "All goals" only opens the list** (owner, 2026-10-08).
              They used to be one row, and one tap did both: the list unfolded *and* the app
              navigated away, so the list you had just opened was on a page you had just left.
              One row cannot be two controls. Home is the destination and carries nothing under
              it; the rubric below it is a heading over places, which is the rule a rubric with
              sub-items has always followed here. */}
          <NavRow
            to="/"
            icon={<Home className="h-[18px] w-[18px]" />}
            label="Home"
            active={path === "/" && !goalId}
          />

          {/* **Under the rubric, every goal** (owner, 2026-10-07). What used to be here was
              "Home" plus — only while a goal was open — that goal's name and its five sections.
              The owner's objection was that it "появляется, то исчезает": a navigation that
              rearranges itself as you move through the app cannot be learned, and the sections it
              offered were already named by the page's own tab row directly above them. A list of
              goals is the opposite: it is the same list wherever you stand, and it is the one
              move this nav can make that the page cannot. */}
          <NavSection
            title="All goals"
            icon={<Award className="h-[18px] w-[18px]" />}
            items={goals.map((g) => ({
              label: g.title || "Untitled goal",
              to: `/goals/${g.id}`,
              active: g.id === goalId,
            }))}
          />

          <NavSection
            title="About Spira"
            icon={<CircleQuestion className="h-[18px] w-[18px]" />}
            // Both rows lead to the same page — they are two of its sections, not two
            // destinations. Android's drawer offers exactly this pair.
            items={["How to use", "What is GROW"].map((label) => ({
              label,
              onClick: goToAbout,
            }))}
          />
        </nav>
      </div>

      {/* **The foot is one segmented group of marks over one account button** (owner, 2026-10-07,
          after Google AI Studio's foot and Gusto's joined button group).

          **No rule above it.** It had a `border-t`, which drew a second line a few pixels from the
          group's own top edge — two rules for one separation, where the gap already does the job.

          The point of the shape is the address: it is cut with an ellipsis when it is long, and the
          whole of it is shown **over the button that was tapped**, never in a dialog in the middle
          of the screen — no marquee, no wrapping, and nothing that changes width as you look at it. */}
      <div className="p-3">
        {/* **One outlined box divided into cells, not four separate boxes.** Four boxes read as
            four unrelated controls; shared hairlines read as one group of related ones, which is
            what they are. `divide-y` when the rail is collapsed, because then the group runs down
            rather than across. */}
        <div
          className={cn(
            "mb-2 flex divide-x overflow-hidden rounded-lg border hairline",
          )}
        >
          <FootMark
            label={chatParked ? "Back to the chat" : "AI coach"}
            onClick={onOpenChat}
          >
            <Sparkles
              className={cn(
                "h-[18px] w-[18px]",
                chatParked && "animate-pulse text-primary",
              )}
            />
          </FootMark>
          <FootMark label="API keys" onClick={onOpenKeys}>
            <Key className="h-[18px] w-[18px]" />
          </FootMark>
          <FootMark
            label="Account"
            onClick={() => void navigate({ to: "/settings" })}
          >
            <Gear className="h-[18px] w-[18px]" />
          </FootMark>
          <FootMark
            label="Sign out"
            onClick={async () => {
              await logout();
              void navigate({ to: "/login" });
            }}
          >
            <LogOut className="h-[18px] w-[18px]" />
          </FootMark>
        </div>

        {user?.email && (
          <Popover open={emailOpen} onOpenChange={setEmailOpen}>
            <PopoverTrigger asChild>
              <button
                type="button"
                aria-label={`Signed in as ${user.email}`}
                className={cn(
                  "flex w-full items-center gap-2 rounded-lg border hairline px-2 py-1.5 text-sm transition-colors hover:bg-muted",
                )}
              >
                {/* The same face the header wears — one account, one picture. */}
                <img
                  src={user.pictureUrl ?? DEFAULT_AVATAR}
                  alt=""
                  referrerPolicy="no-referrer"
                  className="h-6 w-6 shrink-0 rounded-full object-cover"
                />
                <span className="min-w-0 flex-1 truncate text-left text-foreground">
                  {user.email}
                </span>
              </button>
            </PopoverTrigger>
            {/* Anchored **over the button**, the way a hint belongs beside the thing it explains.
                It was a centred `Dialog` first, which put the answer across the screen from the
                question. `w-[220px]` rather than the primitive's `w-72`, so it stays within the
                244px rail; `collisionPadding` keeps it off the window's own edge. */}
            <PopoverContent
              side="top"
              align="start"
              sideOffset={8}
              collisionPadding={12}
              className="w-[220px] p-3"
            >
              {/* **The X sits INSIDE the card, top right** — the rule every notice in the app
                  follows (CLAUDE.md → Notices and toasts). It replaced a worded "Got it" below the
                  text, which put the way out at the bottom of a card you read top-down. */}
              <div className="flex items-start justify-between gap-2">
                <p className="text-sm font-semibold text-foreground">
                  Signed in as
                </p>
                <button
                  type="button"
                  onClick={() => setEmailOpen(false)}
                  aria-label="Close"
                  className="-mr-1 -mt-0.5 shrink-0 rounded p-1 text-muted-foreground transition-colors hover:text-foreground"
                >
                  <X className="h-3.5 w-3.5" />
                </button>
              </div>
              {/* `break-all`, because an address has no break opportunity of its own and this
                  surface exists precisely for the ones too long to fit the rail. */}
              <p className="mt-1 break-all text-sm text-muted-foreground">
                {user.email}
              </p>
            </PopoverContent>
          </Popover>
        )}
      </div>
    </aside>
  );
}

/**
 * One cell of the foot's segmented group — the coach, the keys, the account, signing out.
 *
 * **It carries no border or radius of its own**: the group around it draws one box and divides it,
 * so neighbours share a single hairline instead of each putting up its own. `flex-1` so the four
 * split the rail evenly, and `h-8` keeps every cell over the 24px WCAG 2.5.8 asks of a target.
 * `title` and `aria-label` are the only names these have, so both stay.
 */
function FootMark({
  label,
  onClick,
  children,
}: {
  label: string;
  onClick: () => void;
  children: React.ReactNode;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      title={label}
      aria-label={label}
      className="grid h-8 flex-1 place-items-center text-foreground transition-colors hover:bg-muted"
    >
      {children}
    </button>
  );
}

/**
 * A rubric with its own list under it.
 *
 * **A rubric is never a destination and is never lit** — it is a heading over places, not a place
 * you can stand in, and the only thing tapping it does is open and close its list. "All goals"
 * was briefly both (owner, 2026-10-08): one tap unfolded the list *and* navigated, so the list
 * appeared on a page you had just left. The destination is a `NavRow` of its own above it.
 */
function NavSection({
  title,
  icon,
  items,
}: {
  title: string;
  icon: React.ReactNode;
  items: {
    label: string;
    to?: string;
    active?: boolean;
    onClick?: () => void;
  }[];
}) {
  // **The list opens on a tap of its rubric** (owner, 2026-10-07). It used to be always open, so
  // a dozen goals pushed About Spira off the rail. Open by default only when something inside is
  // where you are — otherwise the rail would hide the row that says so.
  const [open, setOpen] = useState(items.some((i) => i.active));
  const head = (
    <>
      <span className="shrink-0 text-foreground">{icon}</span>
      <span
        className="truncate text-sm font-semibold text-foreground"
        title={title}
      >
        {title}
      </span>
    </>
  );
  return (
    <div className="pt-2">
      <button
        type="button"
        onClick={() => setOpen((o) => !o)}
        aria-expanded={open}
        className="flex w-full items-center gap-3 px-5 py-2.5 text-left hover:bg-muted"
      >
        {head}
      </button>
      {/* **Nested rows start where the parent's LABEL starts, not under its icon** (owner,
          2026-10-07, measured off her reference: both sit at x=52 there). 50px here is the same
          sum — `px-5` (20) + the 18px glyph + `gap-3` (12). Indenting past the label would make
          the list look like a third level. */}
      <div hidden={!open} className="pl-[50px]">
        {items.map((item) => {
          const body = (
            <>
              {/* **No lane down the side** (owner, 2026-10-07). Each sub-item used to carry its own
                  3px stretch — a hairline when not here, Kale when it was — and down a list of
                  goals those stretches read as one ragged vertical line beside the words. The
                  current item is marked the way the rubric above it is: one rounded bar against
                  the rail's **right** edge, drawn behind the row so showing it never moves the
                  words. */}
              {item.active && (
                <span
                  aria-hidden="true"
                  className="absolute right-0 top-1/2 h-6 w-[3px] -translate-y-1/2 rounded-l-[3px] bg-primary"
                />
              )}
              <span
                className={cn(
                  "truncate py-2.5 pr-5 text-sm transition-colors group-hover:text-primary",
                  item.active
                    ? "font-semibold text-primary"
                    : "text-muted-foreground",
                )}
                title={item.label}
              >
                {item.label}
              </span>
            </>
          );
          const shared =
            "group relative flex w-full items-stretch text-left hover:bg-muted";
          return item.to ? (
            <Link key={item.label} to={item.to} className={shared}>
              {body}
            </Link>
          ) : (
            <button
              key={item.label}
              type="button"
              onClick={item.onClick}
              className={shared}
            >
              {body}
            </button>
          );
        })}
      </div>
    </div>
  );
}

function NavRow({
  to,
  icon,
  label,
  active,
}: {
  to: string;
  icon: React.ReactNode;
  label: string;
  active: boolean;
}) {
  return (
    <Link to={to} className="relative flex items-center" title={label}>
      {/* Behind the row, hard against the RIGHT edge (owner, 2026-10-07) — showing it must not
          shift the label. */}
      {active && (
        <span
          aria-hidden="true"
          className="absolute right-0 h-6 w-[3px] rounded-l-[3px] bg-primary"
        />
      )}
      <span
        className={cn(
          "flex w-full items-center gap-3 px-5 py-2.5 text-sm transition-colors",
          active
            ? "font-semibold text-primary"
            : "text-foreground hover:bg-muted",
        )}
      >
        <span className="shrink-0">{icon}</span>
        {label}
      </span>
    </Link>
  );
}
