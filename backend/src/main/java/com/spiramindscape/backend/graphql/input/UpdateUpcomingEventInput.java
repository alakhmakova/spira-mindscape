package com.spiramindscape.backend.graphql.input;

import java.time.Instant;
import java.util.List;

/**
 * Edits an event. {@code time} and {@code reminders} are its whole schedule and are replaced as
 * sent: an absent time makes it all day, an absent list removes every reminder. {@code title} and
 * {@code date} apply to an important date only and are left as they are when absent — a link's
 * name and date are its item's.
 */
public record UpdateUpcomingEventInput(
        String title,
        Instant date,
        String time,
        List<ReminderInput> reminders
) {
}
