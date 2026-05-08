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
import {
  ChevronLeft,
  ChevronRight,
  Target as TargetIcon,
  Flag,
  CheckSquare,
} from "lucide-react";
import { Link } from "@tanstack/react-router";
import { useSpira } from "@/lib/spira/store";
import { cn } from "@/lib/utils";

type DayEvent = {
  goalId: string;
  goalTitle: string;
  label: string;
  kind: "goal" | "target" | "task";
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
      push(g.deadline, {
        goalId: g.id,
        goalTitle: g.title,
        label: g.title,
        kind: "goal",
      });
      for (const t of g.targets) {
        const d = t.deadline;
        push(d, {
          goalId: g.id,
          goalTitle: g.title,
          label: t.title,
          kind: "target",
        });
        if (t.type === "checklist") {
          for (const item of t.items) {
            if (item.deadline) {
              push(item.deadline, {
                goalId: g.id,
                goalTitle: g.title,
                label: item.text,
                kind: "task",
              });
            }
          }
        }
      }
    }
    return map;
  }, [goals]);

  const start = startOfWeek(startOfMonth(cursor), { weekStartsOn: 1 });
  const end = endOfWeek(endOfMonth(cursor), { weekStartsOn: 1 });

  const weeks: Date[][] = [];
  for (let d = start; d <= end; d = new Date(d.getTime() + 7 * 86400000)) {
    weeks.push(
      Array.from({ length: 7 }, (_, i) => new Date(d.getTime() + i * 86400000)),
    );
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="font-display text-4xl sm:text-5xl tracking-tight">
            {format(cursor, "MMMM yyyy")}
          </h1>
          <p className="text-sm text-muted-foreground mt-1.5">
            ISO weeks · Goal, target and task deadlines
          </p>
        </div>
        <div className="flex items-center gap-1">
          <button
            onClick={() => setCursor((c) => addMonths(c, -1))}
            className="h-10 w-10 grid place-items-center rounded-md border-2 border-border hover:border-primary hover:text-primary"
          >
            <ChevronLeft className="h-4 w-4" />
          </button>
          <button
            onClick={() => setCursor(new Date())}
            className="px-4 h-10 rounded-md border-2 border-border text-sm font-semibold hover:border-primary hover:text-primary"
          >
            Today
          </button>
          <button
            onClick={() => setCursor((c) => addMonths(c, 1))}
            className="h-10 w-10 grid place-items-center rounded-md border-2 border-border hover:border-primary hover:text-primary"
          >
            <ChevronRight className="h-4 w-4" />
          </button>
        </div>
      </div>

      <div className="spira-calendar-grid surface-card overflow-hidden">
        <div className="grid grid-cols-[44px_repeat(7,1fr)] border-b hairline bg-secondary/40">
          <div className="text-[11px] uppercase tracking-wider text-muted-foreground font-semibold py-2.5 text-center">
            Wk
          </div>
          {["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"].map((d) => (
            <div
              key={d}
              className="text-[11px] uppercase tracking-wider text-muted-foreground font-semibold py-2.5 text-center"
            >
              <span className="hidden sm:inline">{d}</span>
              <span className="sm:hidden">{d[0]}</span>
            </div>
          ))}
        </div>
        {weeks.map((week, wi) => (
          <div
            key={wi}
            className="grid grid-cols-[44px_repeat(7,1fr)] border-b hairline last:border-b-0"
          >
            <div className="num text-xs text-muted-foreground font-semibold grid place-items-center bg-secondary/30">
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
                    "min-h-24 sm:min-h-28 p-2 border-l hairline space-y-1 relative",
                    muted && "bg-secondary/30 opacity-60",
                  )}
                >
                  <div
                    className={cn(
                      "text-xs num inline-flex items-center justify-center h-6 min-w-6 px-1.5 rounded-full font-semibold",
                      today
                        ? "bg-primary text-primary-foreground"
                        : "text-foreground/70",
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
                        "text-[11px] leading-tight px-1.5 py-1 rounded truncate flex items-center gap-1 font-medium",
                        ev.kind === "goal"
                          ? "bg-primary-soft text-primary border border-primary/20"
                          : "bg-secondary text-foreground/80 border hairline",
                      )}
                      title={`${ev.label} — ${ev.goalTitle}`}
                    >
                      {ev.kind === "goal" ? (
                        <Flag className="h-2.5 w-2.5 shrink-0" />
                      ) : ev.kind === "target" ? (
                        <TargetIcon className="h-2.5 w-2.5 shrink-0" />
                      ) : (
                        <CheckSquare className="h-2.5 w-2.5 shrink-0" />
                      )}
                      <span className="truncate">{ev.label}</span>
                    </Link>
                  ))}
                  {dayEvents.length > 3 && (
                    <div className="text-[10px] text-muted-foreground px-1.5 font-semibold">
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
