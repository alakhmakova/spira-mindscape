package com.spiramindscape.backend.graphql.input;

import java.time.Instant;
import java.util.List;

/**
 * {@code kind = important} takes {@code title} and {@code date}; {@code goal} takes {@code goalId},
 * {@code target} takes {@code targetId}, {@code task} takes {@code taskId} (a checklist item).
 *
 * <p>Any kind takes an optional {@code time} ({@code HH:mm}; absent = all day) and up to five
 * {@code reminders}. Absent reminders mean none: the default reminder is the form's, which shows
 * the user when it will arrive, not something the server adds behind their back.
 */
public record CreateUpcomingEventInput(
        String kind,
        String title,
        Instant date,
        Long goalId,
        Long targetId,
        Long taskId,
        String time,
        List<ReminderInput> reminders
) {
}
