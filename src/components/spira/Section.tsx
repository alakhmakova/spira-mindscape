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
      <header className="flex items-center justify-between gap-3 px-5 sm:px-6 py-4 border-b hairline">
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
          <h2 className="font-heading text-2xl">{title}</h2>
          {typeof count === "number" && count > 0 && (
            <span className="num text-xs font-semibold text-primary bg-primary-soft border border-primary/20 px-2 py-0.5 rounded-full">
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
      </header>
      {open && <div className="p-5 sm:p-6">{children}</div>}
    </section>
  );
}
