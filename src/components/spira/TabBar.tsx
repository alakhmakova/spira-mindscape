import { Link } from "@tanstack/react-router";
import { cn } from "@/lib/utils";

/**
 * A row of tabs — the web twin of Android's `GrowTabsRow` (`ui/components/GoalWorkspaceChrome.kt`),
 * so a tab row reads the same on the phone and the laptop.
 *
 * The shape, taken from that component:
 *
 *  - **The word alone marks the tab — no icons.** A tab bar is a set of places, and a mark beside
 *    each word only competed with it.
 *  - **The current tab carries a 3px Guava underline** as wide as its label, not as wide as its
 *    cell: run edge to edge, the mark reads as a stray rule rather than as belonging to that word.
 *    Guava as a small accent mark is an allowed use; Guava as a fill is not (CLAUDE.md).
 *  - The current word is near-black and bold; the rest are muted and medium.
 *
 * An item either switches state ([TabItem.onSelect]) or navigates ([TabItem.to]) — the All-goals
 * row needs both, because Calendar is its own route while Cards and Timeline are a view mode.
 */
export type TabItem = {
  label: string;
  /** Marked as current. For a `to` item the router decides, so this is only for state tabs. */
  active?: boolean;
  onSelect?: () => void;
  to?: string;
};

export function TabBar({
  items,
  className,
  align = "start",
}: {
  items: TabItem[];
  className?: string;
  /** `start` for a page-level row; `center` where the row is the page's own chrome. */
  align?: "start" | "center";
}) {
  return (
    <div
      role="tablist"
      className={cn(
        "flex items-end gap-6",
        align === "center" ? "justify-center" : "justify-start",
        className,
      )}
    >
      {items.map((item) =>
        item.to ? (
          <Link
            key={item.label}
            to={item.to}
            role="tab"
            className="group flex flex-col items-center"
          >
            {({ isActive }) => (
              <TabInner label={item.label} active={isActive} />
            )}
          </Link>
        ) : (
          <button
            key={item.label}
            type="button"
            role="tab"
            aria-selected={!!item.active}
            onClick={item.onSelect}
            className="flex flex-col items-center"
          >
            <TabInner label={item.label} active={!!item.active} />
          </button>
        ),
      )}
    </div>
  );
}

/** The word and the mark under it. Separate so the underline can be the width of the label. */
function TabInner({ label, active }: { label: string; active: boolean }) {
  return (
    <>
      <span
        className={cn(
          "px-1 pb-2.5 pt-3.5 text-[13.5px] transition-colors",
          active
            ? "font-bold text-foreground"
            : "font-medium text-muted-foreground hover:text-foreground",
        )}
      >
        {label}
      </span>
      <span
        className={cn(
          "h-[3px] w-full rounded-full",
          active ? "bg-[#F45D48]" : "bg-transparent",
        )}
      />
    </>
  );
}
