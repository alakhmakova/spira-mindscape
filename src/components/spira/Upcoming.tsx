import { useMemo, useState } from "react";
import { toast } from "sonner";
import { ChevronDown, ChevronUp, CirclePlus } from "@/components/spira/icons";
import {
  sampleUpcoming,
  upcomingWhen,
  type UpcomingGroup,
} from "@/lib/spira/upcoming";

/**
 * **Upcoming** — the All-goals page's right-hand column (owner, 2026-10-09), modelled on the
 * reference's own column of that name (`gusto/upcomingsynccalendar.png`).
 *
 * Measured off that picture: the heading is the same 20px medium as the page's "Goals"; each
 * category is a 16px medium word behind a small caret that folds it; each event is a 14px medium
 * title in near-black over a 14px grey line saying when. The category and its events share one
 * left edge, with the caret hanging in the gutter to the left of it.
 *
 * Where the reference offers "Sync calendar", this offers **Add event**, after an outline circle
 * with a plus. The events are hardcoded for now (`sampleUpcoming`), so the button says so rather
 * than doing nothing.
 */
export function Upcoming({ className }: { className?: string }) {
  // Dated once per mount: the labels are days, and a page left open over midnight is rare enough
  // not to re-render for.
  const groups = useMemo(() => sampleUpcoming(), []);

  return (
    <section aria-labelledby="upcoming-heading" className={className}>
      <div className="flex min-h-11 items-center justify-between gap-3">
        <h2
          id="upcoming-heading"
          className="text-xl font-medium leading-tight text-foreground"
        >
          Upcoming
        </h2>
        <button
          type="button"
          onClick={() => toast.info("Adding your own events is coming soon.")}
          // **An outline circle with a plus, then the word** (owner, 2026-10-09, after the
          // reference's "Add custom earning type" row): Gravity's `circle-plus` at 16px in the
          // link's own Kale — not a filled disc, which read as a separate button — and the word
          // underlined beside it. The glyph stays out of the underline.
          className="inline-flex items-center gap-2 py-1 text-[15px] font-medium text-primary transition-opacity hover:opacity-80"
        >
          <CirclePlus aria-hidden="true" className="h-4 w-4 shrink-0" />
          <span className="underline decoration-1 underline-offset-[3px]">
            Add event
          </span>
        </button>
      </div>

      <div className="mt-5 space-y-6">
        {groups.map((group) => (
          <UpcomingCategory key={group.category} group={group} />
        ))}
      </div>
    </section>
  );
}

function UpcomingCategory({ group }: { group: UpcomingGroup }) {
  const [open, setOpen] = useState(true);
  const listId = `upcoming-${group.category}`;
  const Caret = open ? ChevronUp : ChevronDown;

  return (
    <div>
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        aria-expanded={open}
        aria-controls={listId}
        // The caret hangs in a 24px gutter, so the word lines up with the events under it.
        className="flex items-center gap-1 py-0.5 text-left text-base font-medium leading-tight text-foreground"
      >
        <span className="grid w-5 shrink-0 place-items-center text-muted-foreground">
          <Caret className="h-3.5 w-3.5" />
        </span>
        {group.label}
      </button>
      <ul id={listId} hidden={!open} className="mt-1.5 space-y-2.5 pl-6">
        {group.events.map((event) => (
          <li key={event.id} className="flex flex-col gap-0.5">
            <span className="text-sm font-medium leading-snug text-foreground">
              {event.title}
            </span>
            <span className="text-sm leading-snug text-muted-foreground">
              {upcomingWhen(event.date)}
            </span>
          </li>
        ))}
      </ul>
    </div>
  );
}
