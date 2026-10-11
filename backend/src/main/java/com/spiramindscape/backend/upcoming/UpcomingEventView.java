package com.spiramindscape.backend.upcoming;

import java.time.Instant;

/**
 * The GraphQL shape of an {@link UpcomingEvent}. A link carries the ids of what it points at, and
 * no date and no title of its own: the client already holds the goal graph and reads both from
 * there, so a deadline edited anywhere moves the event with it.
 */
public record UpcomingEventView(
        Long id,
        String kind,
        String title,
        Instant date,
        Long goalId,
        Long targetId,
        Long taskId
) {
    static UpcomingEventView of(UpcomingEvent event) {
        return new UpcomingEventView(
                event.getId(),
                event.getKind(),
                event.getTitle(),
                event.getEventDate(),
                event.getGoal() == null ? null : event.getGoal().getId(),
                event.getTarget() == null ? null : event.getTarget().getId(),
                event.getChecklistItem() == null ? null : event.getChecklistItem().getId());
    }
}
