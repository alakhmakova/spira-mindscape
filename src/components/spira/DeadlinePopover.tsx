import { useState, useRef } from "react";
import { Calendar as CalendarIcon, Trash2, X, ChevronRight } from "lucide-react";
import { format, isPast, differenceInCalendarDays } from "date-fns";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { Calendar } from "@/components/ui/calendar";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { cn } from "@/lib/utils";

/**
 * Editable deadline trigger. Click opens a popover with a calendar
 * (ISO week numbers shown) and a Remove action.
 *
 * Visual: small inline pill — calendar icon, formatted date, and a
 * compact "Xd left / overdue" annotation. When no date is set, shows
 * "Set deadline" placeholder.
 */
export function DeadlinePopover({
  iso,
  onChange,
  size = "sm",
  align = "start",
  variant = "pill",
  placeholder,
  hideDaysLeft = false,
  className,
  disableScroll = false,
  side = "bottom",
  hideChevron = false,
  renderTrigger,
}: {
  iso?: string;
  onChange: (next: string | undefined) => void;
  size?: "sm" | "md";
  align?: "start" | "center" | "end";
  variant?: "pill" | "input" | "button" | "text" | "icon";
  placeholder?: React.ReactNode;
  hideDaysLeft?: boolean;
  className?: string;
  disableScroll?: boolean;
  side?: "top" | "bottom";
  hideChevron?: boolean;
  renderTrigger?: () => React.ReactNode;
}) {
  const [open, setOpen] = useState(false);
  const inputRef = useRef<HTMLDivElement>(null);
  const date = iso ? new Date(iso) : undefined;
  const [month, setMonth] = useState<Date>(date || new Date());
  
  const days = date ? differenceInCalendarDays(date, new Date()) : 0;
  const overdue = !!date && isPast(date) && days < 0;

  let toneClass = "text-muted-foreground border-border bg-surface hover:border-primary/40 hover:bg-primary-soft/50";
  if (date) {
    if (overdue) {
      toneClass = "text-destructive border-destructive/30 bg-destructive/10 hover:bg-destructive/20 hover:border-destructive/50";
    } else if (days <= 7) {
      toneClass = "text-amber-700 border-amber-500/30 bg-amber-500/10 hover:bg-amber-500/20 hover:border-amber-500/50";
    } else {
      toneClass = "text-foreground/80 border-border bg-surface hover:border-primary/40 hover:bg-primary-soft/50";
    }
  }

  return (
    <Popover 
      open={open} 
      onOpenChange={(o) => {
        setOpen(o);
        if (o) {
          setMonth(date || new Date());
          if (variant === "input" && inputRef.current && !disableScroll) {
            setTimeout(() => {
              inputRef.current?.scrollIntoView({ behavior: "smooth", block: "start" });
            }, 50);
          }
        }
      }}
    >
      {variant === "input" ? (
        <div ref={inputRef} className={cn("relative w-full scroll-mt-6", className)}>
          <PopoverTrigger
            className={cn(
              "w-full flex items-center h-11 bg-surface border border-input focus:outline-none focus-visible:border-primary focus-visible:ring-[3px] focus-visible:ring-ring rounded-md px-3.5 text-base text-left transition-colors",
              !date && "text-muted-foreground"
            )}
          >
            <CalendarIcon className="h-4 w-4 mr-2 opacity-60" />
            {date ? format(date, "PPP") : (placeholder || "Pick a deadline")}
          </PopoverTrigger>
          {date && (
            <button 
              onClick={(e) => { e.preventDefault(); e.stopPropagation(); onChange(undefined); }}
              className="absolute right-2 top-1/2 -translate-y-1/2 h-7 w-7 grid place-items-center rounded text-muted-foreground hover:bg-secondary transition-colors"
              aria-label="Clear deadline"
            >
              <X className="h-4 w-4" />
            </button>
          )}
        </div>
      ) : variant === "button" ? (
        <PopoverTrigger
          className={cn(
            "w-full inline-flex items-center justify-center gap-2 h-10 rounded-md text-sm font-semibold",
            "bg-primary-soft text-primary border border-primary/30 hover:bg-primary-soft/80 transition-colors",
            className
          )}
        >
          <CalendarIcon className="h-3.5 w-3.5" />
          {date ? format(date, "MMM d, yyyy") : (placeholder || "Set deadline")}
        </PopoverTrigger>
      ) : variant === "text" ? (
        <PopoverTrigger
          className={cn(
            "text-sm hover:text-primary transition-colors text-left",
            !date && "text-muted-foreground",
            className
          )}
        >
          {date ? (
            <div className="flex items-center gap-1.5">
              <span>{format(date, "MMM d, yyyy")}</span>
              {!hideDaysLeft && (
                <span className="opacity-60 font-normal">
                  ·{" "}
                  {overdue
                    ? `${Math.abs(days)}d overdue`
                    : days === 0
                      ? "today"
                      : `${days}d left`}
                </span>
              )}
              {!hideChevron && <ChevronRight className="h-3.5 w-3.5 shrink-0" />}
            </div>
          ) : (
            <span>{placeholder || "Set deadline"}</span>
          )}
        </PopoverTrigger>
      ) : renderTrigger ? (
        <PopoverTrigger asChild>
          <button type="button" className={cn("text-left", className)}>
            {renderTrigger()}
          </button>
        </PopoverTrigger>
      ) : variant === "icon" ? (
        <PopoverTrigger
          className={cn(
            "h-7 w-7 grid place-items-center rounded-md transition-colors",
            date ? "text-primary" : "text-muted-foreground hover:text-primary hover:bg-secondary",
            className
          )}
          title={date ? format(date, "MMM d, yyyy") : "Set deadline"}
        >
          <CalendarIcon className="h-3.5 w-3.5" />
        </PopoverTrigger>
      ) : (
        <PopoverTrigger
          className={cn(
            "inline-flex items-center gap-1.5 rounded-md border transition-colors num font-medium",
            size === "md" ? "h-9 px-3 text-sm" : "h-7 px-2 text-xs",
            toneClass,
            className
          )}
        >
          <CalendarIcon className={cn(size === "md" ? "h-4 w-4" : "h-3 w-3", "opacity-70")} />
          {date ? (
            <>
              <span>{format(date, "MMM d, yyyy")}</span>
              {!hideDaysLeft && (
                <span className="opacity-60 font-normal">
                  ·{" "}
                  {overdue
                    ? `${Math.abs(days)}d overdue`
                    : days === 0
                      ? "today"
                      : `${days}d left`}
                </span>
              )}
            </>
          ) : (
            <span>{placeholder || "Set deadline"}</span>
          )}
        </PopoverTrigger>
      )}
      <PopoverContent 
        align={align} 
        side={side} 
        avoidCollisions={true} 
        className="w-auto p-0 bg-surface border hairline shadow-lg overflow-hidden"
        onCloseAutoFocus={(e) => {
          if (variant === "input" && !disableScroll) {
            e.preventDefault();
            const container = document.getElementById("new-goal-scroll-container") || inputRef.current?.closest('.overflow-y-auto');
            if (container) {
              setTimeout(() => {
                container.scrollTo({ top: 0, behavior: "smooth" });
              }, 10);
            }
          }
        }}
      >
        <div className="flex items-center justify-between px-3 py-2 bg-primary text-primary-foreground">
          <span className="text-sm font-semibold">
            {date ? format(date, "MMMM d, yyyy") : "Set deadline"}
          </span>
          <button
            onClick={() => setOpen(false)}
            className="h-6 w-6 grid place-items-center rounded hover:bg-black/10 transition-colors"
            aria-label="Close"
          >
            <X className="h-3.5 w-3.5" />
          </button>
        </div>
        <Calendar
          mode="single"
          selected={date}
          month={month}
          onMonthChange={setMonth}
          onSelect={(d) => {
            onChange(d ? d.toISOString() : undefined);
            setOpen(false);
          }}
          showWeekNumber
          weekStartsOn={1}
          ISOWeek
          initialFocus
          fixedWeeks
          components={{
            CaptionLabel: () => {
              const currentYear = month.getFullYear();
              const years = Array.from({ length: 20 }, (_, i) => new Date().getFullYear() - 2 + i);
              return (
                <div className="flex items-center gap-1.5 ml-1">
                  <span className="text-[15px] font-semibold tracking-tight text-foreground/90">
                    {format(month, "MMMM")}
                  </span>
                  <Select 
                    value={currentYear.toString()} 
                    onValueChange={(y) => setMonth(new Date(parseInt(y), month.getMonth(), 1))}
                  >
                    <SelectTrigger className="h-6 w-fit px-2 py-0 border border-transparent shadow-none bg-transparent hover:bg-secondary focus:ring-0 text-sm font-semibold text-muted-foreground hover:text-foreground transition-colors [&>svg]:ml-2">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent className="max-h-56 min-w-[5rem]">
                      {years.map((y) => (
                        <SelectItem key={y} value={y.toString()} className="text-sm font-medium">
                          {y}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>
              );
            }
          }}
        />
        <div className="flex items-center justify-between px-3 py-2 border-t hairline">
          <button
            onClick={() => {
              const today = new Date();
              onChange(today.toISOString());
              setOpen(false);
            }}
            className="h-6 inline-flex items-center text-xs font-semibold text-muted-foreground hover:text-foreground transition-colors"
          >
            Today
          </button>
          {date ? (
            <button
              onClick={() => {
                onChange(undefined);
                setOpen(false);
              }}
              className="h-6 inline-flex items-center gap-1.5 text-xs font-semibold text-destructive/80 hover:text-destructive transition-colors"
            >
              <Trash2 className="h-3 w-3" /> Clear
            </button>
          ) : (
            <div />
          )}
        </div>
      </PopoverContent>
    </Popover>
  );
}
