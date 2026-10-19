package com.spiramindscape.backend.upcoming;

import java.time.Instant;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * The GraphQL shape of an {@link UpcomingEvent}. A link carries the ids of what it points at, and
 * no date and no title of its own: the client already holds the goal graph and reads both from
 * there, so a deadline edited anywhere moves the event with it. {@code time} ({@code HH:mm}) and
 * the reminders are the entry's own, whatever its kind.
 */
public record UpcomingEventView(
        Long id,
        String kind,
        String title,
        Instant date,
        Long goalId,
        Long targetId,
        Long taskId,
        String time,
        List<UpcomingReminderView> reminders,
        String status,
        Instant resolvedAt
) {
    static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    static UpcomingEventView of(UpcomingEvent event) {
        return new UpcomingEventView(
                event.getId(),
                event.getKind(),
                event.getTitle(),
                event.getEventDate(),
                event.getGoal() == null ? null : event.getGoal().getId(),
                event.getTarget() == null ? null : event.getTarget().getId(),
                event.getChecklistItem() == null ? null : event.getChecklistItem().getId(),
                formatTime(event.getEventTime()),
                event.getReminders().stream().map(UpcomingReminderView::of).toList(),
                event.getStatus(),
                event.getResolvedAt());
    }

    static String formatTime(LocalTime time) {
        return time == null ? null : time.format(HH_MM);
    }
}
