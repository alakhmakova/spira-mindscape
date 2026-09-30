import { createFileRoute, Navigate } from "@tanstack/react-router";

/**
 * The old address of a vacancy map, kept so a link already in a chat transcript still works.
 *
 * A map is not a page any more (owner, 2026-09-18): it opens as the same resizable side panel as
 * every other resource, on the goal page, so the chat beside it keeps the goal it was talking
 * about. This sends the old URL to `/goals/:goalId?resource=:resourceId`, which opens that panel.
 */
export const Route = createFileRoute("/goals_/$goalId/map/$resourceId")({
  component: VacancyMapRedirect,
});

function VacancyMapRedirect() {
  const { goalId, resourceId } = Route.useParams();
  return (
    <Navigate
      to="/goals/$goalId"
      params={{ goalId }}
      search={{ resource: resourceId }}
      replace
    />
  );
}
