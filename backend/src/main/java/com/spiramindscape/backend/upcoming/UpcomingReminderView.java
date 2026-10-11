package com.spiramindscape.backend.upcoming;

/** The GraphQL shape of an {@link UpcomingReminder}: what it is, never its delivery state. */
public record UpcomingReminderView(
        Long id,
        int amount,
        String unit,
        Integer atMinuteOfDay,
        String channel
) {
    static UpcomingReminderView of(UpcomingReminder reminder) {
        return new UpcomingReminderView(reminder.getId(), reminder.getAmount(), reminder.getUnit(),
                reminder.getAtMinuteOfDay(), reminder.getChannel());
    }
}
