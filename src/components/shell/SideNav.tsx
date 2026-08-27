import { Link, useNavigate } from "@tanstack/react-router";
import { CircleQuestion, Home, LogOut, Trophy } from "@/components/spira/icons";
import { useAuth } from "@/lib/spira/auth";
import { cn } from "@/lib/utils";

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
 * | Width | 244dp | `w-[244px]` |
 * | Row padding | 20dp / 10dp | `px-5 py-2.5` |
 * | Glyph | 18dp | 18px |
 * | "You are here" | a 3dp Kale bar, 24dp tall, hard against the left edge | the same, absolutely positioned |
 * | Sub-item rail | 3dp lane, 1.5dp hairline when not here | `w-[3px]` / `w-[1.5px]` |
 * | Sub-item indent | 26dp, then 16dp to the word | `pl-[26px]`, `ml-4` |
 *
 * Three rules carried over verbatim:
 *
 *  - **The "you are here" bar is drawn behind the row, not inside it**, so showing it never
 *    shifts the icon or the label sideways.
 *  - **A rubric with sub-items is never marked "you are here" — only the open sub-item is.**
 *    The goal's name is a heading over places, not a place you can stand in.
 *  - **The rail is per item, never one line down the side.** One shared line could only ever
 *    be one colour, and the open item marks itself by turning its own stretch Kale. The lane
 *    keeps its width in both states so lighting an item never nudges the words.
 *
 * Deliberately absent: **Calendar** (the drawer has no such row — the dashboard offers it as a
 * view tab) and **Knowledge** (Android's is an unwired placeholder, and copying dead rows onto
 * a second surface would just double the dead ends).
 */
export function SideNav({
  path,
  goalId,
  goalTitle,
}: {
  path: string;
  /** The open goal, when one is open — the sub-items are its sections. */
  goalId?: string;
  goalTitle?: string;
}) {
  const user = useAuth((s) => s.user);
  const logout = useAuth((s) => s.logout);
  const navigate = useNavigate();

  return (
    <aside
      aria-label="Main"
      className="sticky top-0 z-30 hidden h-screen w-[244px] shrink-0 flex-col border-r hairline bg-card lg:flex"
    >
      <div className="flex min-h-0 flex-1 flex-col overflow-y-auto py-4">
        <nav>
          {/* Home is the All-goals page: it is only "where you are" when no goal is open. */}
          <NavRow
            to="/"
            icon={<Home className="h-[18px] w-[18px]" />}
            label="Home"
            active={path === "/" && !goalId}
          />

          {goalId && goalTitle && (
            <NavSection
              title={goalTitle}
              icon={<Trophy className="h-[18px] w-[18px]" />}
              items={GOAL_SECTIONS.map((s) => ({
                label: s.label,
                onClick: () => {
                  const el = document.getElementById(s.id);
                  el?.scrollIntoView({ behavior: "smooth", block: "start" });
                },
              }))}
            />
          )}

          <NavSection
            title="About Spira"
            icon={<CircleQuestion className="h-[18px] w-[18px]" />}
            // Both rows lead to the same page — they are two of its sections, not two
            // destinations. Android's drawer offers exactly this pair.
            items={["How to use", "What is GROW"].map((label) => ({
              label,
              onClick: () =>
                void navigate({ to: "/settings", search: { tab: "about" } }),
            }))}
          />
        </nav>
      </div>

      <div className="border-t hairline py-3">
        {user?.email && (
          <p className="truncate px-5 pb-1 text-xs text-muted-foreground">
            {user.email}
          </p>
        )}
        <button
          type="button"
          onClick={async () => {
            await logout();
            void navigate({ to: "/login" });
          }}
          className="flex w-full items-center gap-3 px-5 py-2.5 text-left text-sm text-foreground transition-colors hover:bg-muted"
        >
          <LogOut className="h-[18px] w-[18px]" />
          Sign out
        </button>
      </div>
    </aside>
  );
}

/**
 * The goal page's own sections, in page order. They scroll rather than navigate: on the web a
 * goal is one long page where on Android it is a pager of tabs, so the drawer's GROW tabs
 * become anchors here. "Will do", not "Targets" — the page's own tab row and the Android tab
 * bar both call it that. The ids are in `routes/goals.$goalId.tsx`.
 */
const GOAL_SECTIONS = [
  { id: "goal-top", label: "Goal" },
  { id: "reality-section", label: "Reality" },
  { id: "resources-section", label: "Resources" },
  { id: "options-section", label: "Options" },
  { id: "targets-section", label: "Will do" },
];

function NavSection({
  title,
  icon,
  items,
}: {
  title: string;
  icon: React.ReactNode;
  items: { label: string; onClick: () => void }[];
}) {
  return (
    <div className="pt-2">
      {/* The rubric: a row with its mark, and never lit. */}
      <div className="flex items-center gap-3 px-5 py-2.5">
        <span className="shrink-0 text-foreground">{icon}</span>
        <span
          className="truncate text-sm font-semibold text-foreground"
          title={title}
        >
          {title}
        </span>
      </div>
      <div className="pl-[26px]">
        {items.map((item) => (
          <button
            key={item.label}
            type="button"
            onClick={item.onClick}
            className="group flex w-full items-stretch text-left"
          >
            {/* The lane keeps its 3px whether lit or not, so the words never move. */}
            <span
              aria-hidden="true"
              className="flex w-[3px] shrink-0 justify-start"
            >
              <span className="w-[1.5px] bg-border transition-colors group-hover:w-[3px] group-hover:bg-primary" />
            </span>
            <span className="ml-4 py-2.5 pr-5 text-sm text-muted-foreground transition-colors group-hover:text-primary">
              {item.label}
            </span>
          </button>
        ))}
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
    <Link to={to} className="relative flex items-center">
      {/* Behind the row, hard against the edge — showing it must not shift the label. */}
      {active && (
        <span
          aria-hidden="true"
          className="absolute left-0 h-6 w-[3px] rounded-r-[3px] bg-primary"
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
