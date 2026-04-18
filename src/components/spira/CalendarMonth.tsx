import { useMemo, useState } from "react";
import {
  addMonths,
  endOfMonth,
  endOfWeek,
  format,
  getISOWeek,
  isSameDay,
  isSameMonth,
  startOfMonth,
  startOfWeek,
} from "date-fns";
import { ChevronLeft, ChevronRight, Target as TargetIcon, Flag } from "lucide-react";
import { Link } from "@tanstack/react-router";
import { useSpira } from "@/lib/spira/store";
import { cn } from "@/lib/utils";

type DayEvent = {
  goalId: string;
  goalTitle: string;
  label: string;
  kind: "goal" | "target";
};

export function CalendarMonth() {
  const goals = useSpira((s) => s.goals);
  const [cursor, setCursor] = useState(new Date());

  const events = useMemo(() => {
    const map = new Map<string, DayEvent[]>();
    const push = (d: string | undefined, ev: DayEvent) => {
      if (!d) return;
      const key = format(new Date(d), "yyyy-MM-dd");
      const arr = map.get(key) ?? [];
      arr.push(ev);
      map.set(key, arr);
    };
    for (const g of goals) {
      push(g.deadline, { goalId: g.id, goalTitle: g.title, label: g.title, kind: "goal" });
      for (const t of g.targets) {
        const d = (t as any).deadline as string | undefined;
        push(d, { goalId: g.id, goalTitle: g.title, label: t.title, kind: "target" });
      }
    }
    return map;
  }, [goals]);

  const start = startOfWeek(startOfMonth(cursor), { weekStartsOn: 1 });
  const end = endOfWeek(endOfMonth(cursor), { weekStartsOn: 1 });

  const weeks: Date[][] = [];
  for (let d = start; d <= end; d = new Date(d.getTime() + 7 * 86400000)) {
    weeks.push(Array.from({ length: 7 }, (_, i) => new Date(d.getTime() + i * 86400000)));
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="font-display text-3xl sm:text-4xl">{format(cursor, "MMMM yyyy")}</h1>
          <p className="text-sm text-muted-foreground mt-1">
            ISO weeks · Goal and target deadlines
          </p>
        </div>
        <div className="flex items-center gap-1">
          <button
            onClick={() => setCursor((c) => addMonths(c, -1))}
            className="h-9 w-9 grid place-items-center rounded-md border hairline-strong hover:bg-accent"
          >
            <ChevronLeft className="h-4 w-4" />
          </button>
          <button
            onClick={() => setCursor(new Date())}
            className="px-3 h-9 rounded-md border hairline-strong text-sm hover:bg-accent"
          >
            Today
          </button>
          <button
            onClick={() => setCursor((c) => addMonths(c, 1))}
            className="h-9 w-9 grid place-items-center rounded-md border hairline-strong hover:bg-accent"
          >
            <ChevronRight className="h-4 w-4" />
          </button>
        </div>
      </div>

      <div className="surface-card overflow-hidden">
        <div className="grid grid-cols-[44px_repeat(7,1fr)] border-b hairline">
          <div className="text-[10px] uppercase tracking-wider text-muted-foreground py-2 text-center">
            Wk
          </div>
          {["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"].map((d) => (
            <div
              key={d}
              className="text-[10px] uppercase tracking-wider text-muted-foreground py-2 text-center"
            >
              <span className="hidden sm:inline">{d}</span>
              <span className="sm:hidden">{d[0]}</span>
            </div>
          ))}
        </div>
        {weeks.map((week, wi) => (
          <div key={wi} className="grid grid-cols-[44px_repeat(7,1fr)] border-b hairline last:border-b-0">
            <div className="num text-xs text-muted-foreground grid place-items-center bg-surface-sunken/40">
              {getISOWeek(week[0])}
            </div>
            {week.map((day) => {
              const key = format(day, "yyyy-MM-dd");
              const dayEvents = events.get(key) ?? [];
              const muted = !isSameMonth(day, cursor);
              const today = isSameDay(day, new Date());
              return (
                <div
                  key={key}
                  className={cn(
                    "min-h-20 sm:min-h-24 p-1.5 border-l hairline space-y-1 relative",
                    muted && "opacity-40",
                  )}
                >
                  <div
                    className={cn(
                      "text-xs num inline-flex items-center justify-center h-5 min-w-5 px-1 rounded-full",
                      today && "bg-primary text-primary-foreground font-medium",
                    )}
                  >
                    {format(day, "d")}
                  </div>
                  {dayEvents.slice(0, 3).map((ev, i) => (
                    <Link
                      key={i}
                      to="/goals/$goalId"
                      params={{ goalId: ev.goalId }}
                      className={cn(
                        "block text-[11px] leading-tight px-1.5 py-1 rounded-md truncate flex items-center gap-1",
                        ev.kind === "goal"
                          ? "bg-primary/15 text-primary border border-primary/20"
                          : "bg-accent text-foreground border hairline",
                      )}
                      title={`${ev.label} — ${ev.goalTitle}`}
                    >
                      {ev.kind === "goal" ? (
                        <Flag className="h-2.5 w-2.5 shrink-0" />
                      ) : (
                        <TargetIcon className="h-2.5 w-2.5 shrink-0" />
                      )}
                      <span className="truncate">{ev.label}</span>
                    </Link>
                  ))}
                  {dayEvents.length > 3 && (
                    <div className="text-[10px] text-muted-foreground px-1.5">
                      +{dayEvents.length - 3} more
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        ))}
      </div>
    </div>
  );
}
