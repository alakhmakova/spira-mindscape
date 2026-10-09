import { cn } from "@/lib/utils";

export function ConfidenceStepper({
  value,
  onChange,
}: {
  value: number;
  onChange: (v: number) => void;
}) {
  return (
    <div className="flex items-center gap-1">
      {Array.from({ length: 10 }, (_, i) => i + 1).map((n) => {
        const active = n <= value;
        const isExact = n === value;
        return (
          <button
            key={n}
            onClick={() => onChange(n)}
            className={cn(
              "h-9 flex-1 rounded-md transition-colors num text-xs font-semibold border",
              isExact
                ? "bg-primary text-primary-foreground border-primary shadow-sm"
                : active
                  ? "bg-primary-soft text-primary border-primary/30"
                  : "bg-surface text-muted-foreground border-border hover:border-border-strong hover:text-foreground",
            )}
            aria-label={`Confidence ${n}`}
          >
            {n}
          </button>
        );
      })}
    </div>
  );
}
