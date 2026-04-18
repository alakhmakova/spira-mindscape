import { ChevronDown } from "lucide-react";
import { useState, type ReactNode } from "react";
import { cn } from "@/lib/utils";

export function Section({
  title,
  hint,
  children,
  defaultOpen = true,
  action,
  count,
}: {
  title: string;
  hint?: string;
  children: ReactNode;
  defaultOpen?: boolean;
  action?: ReactNode;
  count?: number;
}) {
  const [open, setOpen] = useState(defaultOpen);
  return (
    <section className="surface-card overflow-hidden">
      <header className="flex items-center justify-between gap-3 px-4 sm:px-5 py-3.5 border-b hairline">
        <button
          onClick={() => setOpen((o) => !o)}
          className="flex items-center gap-2 text-left flex-1 min-w-0"
        >
          <ChevronDown
            className={cn(
              "h-4 w-4 text-muted-foreground transition-transform shrink-0",
              !open && "-rotate-90",
            )}
          />
          <h2 className="font-display text-lg sm:text-xl">{title}</h2>
          {typeof count === "number" && (
            <span className="num text-xs text-muted-foreground bg-muted px-1.5 py-0.5 rounded">
              {count}
            </span>
          )}
          {hint && (
            <span className="hidden sm:inline text-xs text-muted-foreground ml-2 truncate">
              {hint}
            </span>
          )}
        </button>
        {action}
      </header>
      {open && <div className="p-4 sm:p-5">{children}</div>}
    </section>
  );
}
