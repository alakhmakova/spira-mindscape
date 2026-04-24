import { cn } from "@/lib/utils";

export function ConfidencePill({
  value,
  className,
}: {
  value: number;
  className?: string;
}) {
  const tone =
    value <= 3
      ? "bg-destructive/10 text-destructive border-destructive/30"
      : value <= 6
        ? "bg-amber-500/15 text-amber-700 dark:text-amber-500 border-amber-500/40"
        : "bg-primary-soft text-primary border-primary/30";
  return (
    <span
      className={cn(
        "inline-flex items-center gap-1.5 rounded-full border px-2.5 py-0.5 text-xs font-semibold num",
        tone,
        className,
      )}
      title={`Confidence ${value}/10`}
    >
      <span className="uppercase tracking-wider text-[10px]">
        Confidence
      </span>
      <span>
        {value}/10
      </span>
    </span>
  );
}

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
