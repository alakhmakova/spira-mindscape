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
        "relative h-2 w-full overflow-hidden rounded-full bg-secondary",
        className,
      )}
    >
      <div
        className={cn(
          "absolute inset-y-0 left-0 rounded-full transition-all duration-500",
          tone === "primary"
            ? pct >= 100
              ? "bg-primary"
              : "bg-[#ea580c]"
            : "bg-muted-foreground/60",
        )}
        style={{ width: `${pct}%` }}
      />
    </div>
  );
}
