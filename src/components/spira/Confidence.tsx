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
      ? "bg-destructive/15 text-destructive border-destructive/30"
      : value <= 6
        ? "bg-warning/15 text-warning border-warning/30"
        : "bg-primary/15 text-primary border-primary/30";
  return (
    <span
      className={cn(
        "inline-flex items-center gap-1 rounded-full border px-2 py-0.5 text-xs num",
        tone,
        className,
      )}
      title={`Confidence ${value}/10`}
    >
      <span className="opacity-70">conf</span>
      <span>{value}</span>
      <span className="opacity-50">/10</span>
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
        return (
          <button
            key={n}
            onClick={() => onChange(n)}
            className={cn(
              "h-7 flex-1 rounded-sm transition-colors num text-[11px]",
              active
                ? n === value
                  ? "bg-primary text-primary-foreground"
                  : "bg-primary/40 text-primary-foreground/90"
                : "bg-muted text-muted-foreground hover:bg-accent",
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
