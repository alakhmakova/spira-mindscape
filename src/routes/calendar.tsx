import { createFileRoute } from "@tanstack/react-router";
import { CalendarMonth } from "@/components/spira/CalendarMonth";

export const Route = createFileRoute("/calendar")({
  head: () => ({
    meta: [
      { title: "Calendar — Spira" },
      {
        name: "description",
        content:
          "See goal and target deadlines on a clean monthly calendar with ISO week numbers.",
      },
    ],
  }),
  component: CalendarPage,
});

function CalendarPage() {
  return (
    <div className="mx-auto max-w-6xl px-4 sm:px-6 py-8 sm:py-12">
      <CalendarMonth />
    </div>
  );
}
