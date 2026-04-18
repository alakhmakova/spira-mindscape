import { format, isPast, differenceInCalendarDays } from "date-fns";
import { cn } from "@/lib/utils";

export function DeadlineLabel({
  iso,
  className,
}: {
  iso?: string;
  className?: string;
}) {
  if (!iso) return <span className={cn("text-muted-foreground text-xs", className)}>No deadline</span>;
  const d = new Date(iso);
  const days = differenceInCalendarDays(d, new Date());
  const overdue = isPast(d) && days < 0;
  const tone = overdue
    ? "text-destructive"
    : days <= 7
      ? "text-warning"
      : "text-muted-foreground";
  return (
    <span className={cn("text-xs num", tone, className)}>
      {format(d, "d MMM yyyy")}{" "}
      <span className="opacity-70">
        · {overdue ? `${Math.abs(days)}d overdue` : days === 0 ? "today" : `${days}d`}
      </span>
    </span>
  );
}
