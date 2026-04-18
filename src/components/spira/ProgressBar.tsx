import { cn } from "@/lib/utils";

export function ProgressBar({
  value,
  className,
  tone = "primary",
}: {
  value: number; // 0..1
  className?: string;
  tone?: "primary" | "muted";
}) {
  const pct = Math.round(Math.max(0, Math.min(1, value)) * 100);
  return (
    <div
      className={cn(
        "relative h-1.5 w-full overflow-hidden rounded-full bg-surface-sunken border hairline",
        className,
      )}
    >
      <div
        className={cn(
          "absolute inset-y-0 left-0 rounded-full transition-all",
          tone === "primary" ? "bg-primary" : "bg-muted-foreground/60",
        )}
        style={{ width: `${pct}%` }}
      />
    </div>
  );
}
