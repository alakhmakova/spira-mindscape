package com.spiramindscape.backend.upcoming;

/**
 * What a reminder's notification says — fetched by the service worker over the user's own
 * session, so a push service's queue only ever holds an event id, never a goal's text.
 */
public record ReminderNotice(Long eventId, String title, String body) {
}
