import { format, isPast, differenceInCalendarDays } from "date-fns";
import { Calendar } from "lucide-react";
import { cn } from "@/lib/utils";

export function DeadlineLabel({
  iso,
  className,
}: {
  iso?: string;
  className?: string;
}) {
  if (!iso)
    return (
      <span
        className={cn(
          "inline-flex items-center gap-1.5 text-xs text-muted-foreground",
          className,
        )}
      >
        <Calendar className="h-3 w-3" />
        No deadline
      </span>
    );
  const d = new Date(iso);
  const days = differenceInCalendarDays(d, new Date());
  const overdue = isPast(d) && days < 0;
  const tone = overdue
    ? "text-destructive"
    : days <= 7
      ? "text-[oklch(0.45_0.12_60)]"
      : "text-foreground/80";
  return (
    <span
      className={cn(
        "inline-flex items-center gap-1.5 text-xs num font-medium",
        tone,
        className,
      )}
    >
      <Calendar className="h-3 w-3 opacity-70" />
      {format(d, "MMM d, yyyy")}
      <span className="opacity-60 font-normal">
        · {overdue ? `${Math.abs(days)}d overdue` : days === 0 ? "today" : `${days}d left`}
      </span>
    </span>
  );
}
