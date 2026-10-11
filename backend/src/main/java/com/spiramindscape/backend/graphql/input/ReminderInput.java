package com.spiramindscape.backend.graphql.input;

/**
 * One reminder as the client sends it: "{@code amount} {@code unit}s before", and for an all-day
 * event "… at {@code atMinuteOfDay}" (minutes after midnight). {@code channel} defaults to
 * {@code notification}. Every field is validated in {@code UpcomingEventService}.
 */
public record ReminderInput(
        Integer amount,
        String unit,
        Integer atMinuteOfDay,
        String channel
) {
}
