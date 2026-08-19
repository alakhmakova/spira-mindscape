import { ChevronDown } from "@/components/spira/icons";
import { useState, type ReactNode } from "react";
import { cn } from "@/lib/utils";

export function Section({
  title,
  hint,
  children,
  defaultOpen = true,
  action,
  count,
  countVariant = "primary",
  headerOverride,
}: {
  title: string;
  hint?: string;
  children: ReactNode;
  defaultOpen?: boolean;
  action?: ReactNode;
  count?: number;
  countVariant?: "primary" | "orange";
  /**
   * Takes the whole header row in place of the title — how an **open search** works, the way the
   * Android All-goals header swaps its wordmark for a search field. On a phone a field squeezed in
   * beside the title and its count has nowhere to be; the title comes back when the search closes.
   */
  headerOverride?: ReactNode;
}) {
  const [open, setOpen] = useState(defaultOpen);
  return (
    <section className="surface-card overflow-hidden">
      <header className="flex items-center justify-between gap-3 px-5 sm:px-6 py-4 border-b hairline">
        {headerOverride ?? (
          <>
            <button
              onClick={() => setOpen((o) => !o)}
              className="flex items-center gap-3 text-left flex-1 min-w-0 group"
            >
              <ChevronDown
                className={cn(
                  "h-4 w-4 text-muted-foreground transition-transform shrink-0 group-hover:text-foreground",
                  !open && "-rotate-90",
                )}
              />
              {/* Regular serif weight — a section name is a heading, not a bold label (owner asked
              for these titles not to read as bold on mobile). */}
              <h2 className="font-heading text-2xl font-normal">{title}</h2>
              {typeof count === "number" && count > 0 && (
                <span
                  className={cn(
                    "inline-flex items-center justify-center h-10 w-10 rounded-full border-[5px] bg-white text-[15px] font-medium leading-none",
                    countVariant === "orange"
                      ? "border-[#F45D48] text-[#F45D48]"
                      : "border-primary text-primary",
                  )}
                >
                  {count}
                </span>
              )}
              {hint && (
                <span className="hidden sm:inline text-sm text-muted-foreground ml-1 truncate">
                  · {hint}
                </span>
              )}
            </button>
            {action}
          </>
        )}
      </header>
      {open && <div className="p-5 sm:p-6">{children}</div>}
    </section>
  );
}
