package com.spiramindscape.backend.upcoming;

import java.time.Instant;

/**
 * An event with at least one reminder that has gone off and is not answered yet — what the
 * "decide now" page shows. One per event, however many of its reminders are due: the page
 * answers the event, not a reminder. {@code title} and {@code date} are resolved here (a link's
 * are its item's), so the page and the service worker need nothing else to say what it is about.
 */
public record DueReminderView(
        Long eventId,
        String kind,
        String title,
        Instant date,
        String time,
        Long goalId,
        Instant firedAt
) {
}
