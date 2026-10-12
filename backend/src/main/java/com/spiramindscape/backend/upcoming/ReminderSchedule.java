package com.spiramindscape.backend.upcoming;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

/**
 * When a reminder fires, and what state it is in — pure functions of the reminder, its event, the
 * user's zone and "now", so every rule can be tested without a clock or a database.
 *
 * <p><b>The event's day</b> is its date read in the user's zone: dates are stored as the instant of
 * local midnight on the device that picked them, so the zone the client reports is the one they
 * were picked in.
 *
 * <p><b>All day</b> ({@code eventTime == null}): "{@code n} days/weeks before, at
 * {@code atMinuteOfDay}" — a wall-clock time on a calendar day, so it stays 09:00 across a
 * daylight-saving change. <b>Timed</b>: minutes and hours are taken off the instant (so "1 hour
 * before" is always 60 real minutes), days and weeks off the calendar (so "1 day before" keeps the
 * event's wall-clock time even when the night between was 23 or 25 hours long).
 */
public final class ReminderSchedule {

    private ReminderSchedule() {
    }

    /** The user's zone, or UTC until the client has reported one. */
    public static ZoneId zone(String timeZone) {
        if (timeZone == null || timeZone.isBlank()) {
            return ZoneOffset.UTC;
        }
        try {
            return ZoneId.of(timeZone);
        } catch (RuntimeException e) {
            return ZoneOffset.UTC; // validated on the way in; a stored value cannot break a run
        }
    }

    /** When this reminder fires, or null when its event has no date (a link whose deadline was cleared). */
    public static Instant fireAt(UpcomingReminder reminder, ZoneId zone) {
        UpcomingEvent event = reminder.getEvent();
        return fireAt(event.effectiveDate(), event.getEventTime(), reminder.getAmount(), reminder.getUnit(),
                reminder.getAtMinuteOfDay(), zone);
    }

    public static Instant fireAt(Instant eventDate, LocalTime eventTime, int amount, String unit,
                                 Integer atMinuteOfDay, ZoneId zone) {
        if (eventDate == null) {
            return null;
        }
        LocalDate day = dayOf(eventDate, zone);
        if (eventTime != null) {
            ZonedDateTime at = ZonedDateTime.of(day, eventTime, zone);
            return switch (unit) {
                case "minute" -> at.toInstant().minusSeconds(60L * amount);
                case "hour" -> at.toInstant().minusSeconds(3600L * amount);
                case "week" -> at.minusWeeks(amount).toInstant();
                default -> at.minusDays(amount).toInstant();
            };
        }
        int minute = atMinuteOfDay == null ? UpcomingReminder.DEFAULT_MINUTE_OF_DAY : atMinuteOfDay;
        LocalTime time = LocalTime.ofSecondOfDay(60L * minute);
        return switch (unit) {
            case "week" -> ZonedDateTime.of(day.minusWeeks(amount), time, zone).toInstant();
            case "day" -> ZonedDateTime.of(day.minusDays(amount), time, zone).toInstant();
            // Refused on the way in (minutes and hours need a time); read as before 09:00 on the day.
            case "hour" -> ZonedDateTime.of(day, time, zone).toInstant().minusSeconds(3600L * amount);
            default -> ZonedDateTime.of(day, time, zone).toInstant().minusSeconds(60L * amount);
        };
    }

    /**
     * Whether a fire at {@code fireAt} counts at all. A reminder never fires for a moment from
     * before it was last <i>set</i>: "1 week before" added three days ahead of the date, "on the
     * day at 09:00" added at noon, a date edited into the past, or a linked deadline moved earlier
     * on the goal page — each stays silent rather than going off the instant it changes, which is
     * also what the form's sentence promises ("its moment has already passed").
     *
     * <p>"Set" is the latest of the reminder's creation, the event's last edit and the linked
     * item's last edit. A fire that has already gone out stays armed whatever is edited after it,
     * so an unanswered reminder does not vanish from "decide now" because a goal was renamed.
     */
    static boolean armed(UpcomingReminder reminder, Instant fireAt) {
        if (fireAt == null) {
            return false;
        }
        if (reminder.getLastFiredFor() != null && !reminder.getLastFiredFor().isBefore(fireAt)) {
            return true;
        }
        return !fireAt.isBefore(armedSince(reminder));
    }

    static Instant armedSince(UpcomingReminder reminder) {
        Instant since = reminder.getCreatedAt();
        UpcomingEvent event = reminder.getEvent();
        if (event != null) {
            since = later(since, event.getUpdatedAt());
            if (event.getGoal() != null) since = later(since, event.getGoal().getUpdatedAt());
            if (event.getTarget() != null) since = later(since, event.getTarget().getUpdatedAt());
            if (event.getChecklistItem() != null) since = later(since, event.getChecklistItem().getUpdatedAt());
        }
        return since;
    }

    private static Instant later(Instant a, Instant b) {
        if (a == null) return b;
        if (b == null) return a;
        return b.isAfter(a) ? b : a;
    }

    /**
     * The calendar day a stored date stands for. The web stores a day as the instant of local
     * midnight on the device that picked it, which is read in the user's zone. The Android app
     * stores it as midnight <b>UTC</b>; read in New York that is the evening before. An instant at
     * exactly 00:00 UTC is therefore read as its UTC date — for a web user that only happens in a
     * zone at UTC+0, where both readings agree.
     */
    public static LocalDate dayOf(Instant date, ZoneId zone) {
        if (date.getEpochSecond() % 86_400 == 0) {
            return date.atZone(ZoneOffset.UTC).toLocalDate();
        }
        return date.atZone(zone).toLocalDate();
    }

    /** The fire at {@code fireAt} has been answered (Done or Open). */
    static boolean answered(UpcomingReminder reminder, Instant fireAt) {
        return reminder.getAnsweredAt() != null
                && reminder.getLastFiredFor() != null
                && !reminder.getLastFiredFor().isBefore(fireAt);
    }

    static boolean snoozed(UpcomingReminder reminder, Instant now) {
        return reminder.getSnoozedUntil() != null && now.isBefore(reminder.getSnoozedUntil());
    }

    /** Due: its moment has passed and it is neither answered nor snoozed — what "decide now" shows. */
    public static boolean isDue(UpcomingReminder reminder, Instant fireAt, Instant now) {
        return armed(reminder, fireAt)
                && !now.isBefore(fireAt)
                && !answered(reminder, fireAt)
                && !snoozed(reminder, now);
    }

    /**
     * A fresh fire the dispatcher has not sent yet: its moment has passed and is later than the
     * last one sent. A deadline moved later re-arms the reminder; one moved earlier cannot repeat it.
     */
    public static boolean isFreshFire(UpcomingReminder reminder, Instant fireAt, Instant now) {
        return armed(reminder, fireAt)
                && !now.isBefore(fireAt)
                && (reminder.getLastFiredFor() == null || fireAt.isAfter(reminder.getLastFiredFor()));
    }

    /** A snooze that has run out on a fire still unanswered — sent again. */
    public static boolean isSnoozeOver(UpcomingReminder reminder, Instant now) {
        return reminder.getSnoozedUntil() != null
                && !now.isBefore(reminder.getSnoozedUntil())
                && reminder.getAnsweredAt() == null;
    }
}
