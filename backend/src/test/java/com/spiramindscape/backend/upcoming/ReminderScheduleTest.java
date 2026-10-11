package com.spiramindscape.backend.upcoming;

import com.spiramindscape.backend.goal.Goal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ReminderSchedule}: the moment each preset fires, across a daylight-saving change, and the
 * three states that decide whether a reminder is sent and whether "decide now" shows it.
 */
class ReminderScheduleTest {

    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    /** Dates are stored as the instant of local midnight on the device that picked them. */
    private static Instant midnight(String day, ZoneId zone) {
        return LocalDate.parse(day).atStartOfDay(zone).toInstant();
    }

    private static Instant at(String localDateTime, ZoneId zone) {
        return ZonedDateTime.of(java.time.LocalDateTime.parse(localDateTime), zone).toInstant();
    }

    @Nested
    @DisplayName("All-day events")
    class AllDay {
        private final Instant date = midnight("2030-06-19", BERLIN);

        @Test
        @DisplayName("The presets: on the day, 1 and 2 days, 1 week before — at 09:00 local")
        void presets() {
            assertThat(ReminderSchedule.fireAt(date, null, 0, "day", 540, BERLIN))
                    .isEqualTo(at("2030-06-19T09:00", BERLIN));
            assertThat(ReminderSchedule.fireAt(date, null, 1, "day", 540, BERLIN))
                    .isEqualTo(at("2030-06-18T09:00", BERLIN));
            assertThat(ReminderSchedule.fireAt(date, null, 2, "day", 540, BERLIN))
                    .isEqualTo(at("2030-06-17T09:00", BERLIN));
            assertThat(ReminderSchedule.fireAt(date, null, 1, "week", 540, BERLIN))
                    .isEqualTo(at("2030-06-12T09:00", BERLIN));
        }

        @Test
        @DisplayName("A custom time of day, and 09:00 when none is given")
        void timeOfDay() {
            assertThat(ReminderSchedule.fireAt(date, null, 3, "day", 18 * 60 + 30, BERLIN))
                    .isEqualTo(at("2030-06-16T18:30", BERLIN));
            assertThat(ReminderSchedule.fireAt(date, null, 0, "day", null, BERLIN))
                    .isEqualTo(at("2030-06-19T09:00", BERLIN));
        }

        @Test
        @DisplayName("\"1 day before at 09:00\" stays 09:00 across the spring change, and the autumn one")
        void daylightSaving() {
            // Clocks go forward on 2030-03-31 and back on 2030-10-27 in Berlin.
            assertThat(ReminderSchedule.fireAt(midnight("2030-04-01", BERLIN), null, 1, "day", 540, BERLIN))
                    .isEqualTo(at("2030-03-31T09:00", BERLIN))
                    .isEqualTo(Instant.parse("2030-03-31T07:00:00Z"));
            assertThat(ReminderSchedule.fireAt(midnight("2030-03-31", BERLIN), null, 1, "day", 540, BERLIN))
                    .isEqualTo(Instant.parse("2030-03-30T08:00:00Z"));
            assertThat(ReminderSchedule.fireAt(midnight("2030-10-28", BERLIN), null, 1, "day", 540, BERLIN))
                    .isEqualTo(Instant.parse("2030-10-27T08:00:00Z"));
        }

        @Test
        @DisplayName("The day is read in the user's zone, not in UTC")
        void dayInTheUsersZone() {
            // Berlin midnight of the 19th is the 18th at 22:00 UTC.
            assertThat(ReminderSchedule.fireAt(date, null, 0, "day", 540, BERLIN))
                    .isEqualTo(Instant.parse("2030-06-19T07:00:00Z"));
        }
    }

    @Nested
    @DisplayName("Timed events")
    class Timed {
        private final Instant date = midnight("2030-06-19", BERLIN);
        private final LocalTime time = LocalTime.of(14, 0);

        @Test
        @DisplayName("Minutes, hours, days and weeks before the event's time")
        void presets() {
            assertThat(ReminderSchedule.fireAt(date, time, 5, "minute", null, BERLIN))
                    .isEqualTo(at("2030-06-19T13:55", BERLIN));
            assertThat(ReminderSchedule.fireAt(date, time, 2, "hour", null, BERLIN))
                    .isEqualTo(at("2030-06-19T12:00", BERLIN));
            assertThat(ReminderSchedule.fireAt(date, time, 1, "day", null, BERLIN))
                    .isEqualTo(at("2030-06-18T14:00", BERLIN));
            assertThat(ReminderSchedule.fireAt(date, time, 1, "week", null, BERLIN))
                    .isEqualTo(at("2030-06-12T14:00", BERLIN));
            assertThat(ReminderSchedule.fireAt(date, time, 0, "minute", null, BERLIN))
                    .isEqualTo(at("2030-06-19T14:00", BERLIN));
        }

        @Test
        @DisplayName("Across a change: a day keeps the wall-clock time, an hour stays 60 real minutes")
        void daylightSaving() {
            Instant dayAfterChange = midnight("2030-03-31", BERLIN);
            LocalTime earlyMorning = LocalTime.of(4, 0); // 04:00 CEST, two real hours after 01:00 CET
            assertThat(ReminderSchedule.fireAt(midnight("2030-04-01", BERLIN), earlyMorning, 1, "day", null, BERLIN))
                    .isEqualTo(at("2030-03-31T04:00", BERLIN));
            assertThat(ReminderSchedule.fireAt(dayAfterChange, earlyMorning, 3, "hour", null, BERLIN))
                    .isEqualTo(Instant.parse("2030-03-31T02:00:00Z").minusSeconds(3 * 3600));
        }
    }

    @Test
    @DisplayName("A link whose deadline was cleared has no fire time at all")
    void noDate() {
        assertThat(ReminderSchedule.fireAt(null, null, 0, "day", 540, BERLIN)).isNull();
    }

    @Test
    @DisplayName("An unknown or missing zone falls back to UTC rather than breaking a run")
    void zoneFallback() {
        assertThat(ReminderSchedule.zone(null)).isEqualTo(ZoneOffset.UTC);
        assertThat(ReminderSchedule.zone("Mars/Olympus")).isEqualTo(ZoneOffset.UTC);
        assertThat(ReminderSchedule.zone("Europe/Berlin")).isEqualTo(BERLIN);
    }

    @Nested
    @DisplayName("State")
    class State {
        private final Instant created = Instant.parse("2030-06-01T00:00:00Z");
        private final Instant fire = Instant.parse("2030-06-19T07:00:00Z");

        private UpcomingReminder reminder() {
            UpcomingReminder r = new UpcomingReminder();
            r.setCreatedAt(created);
            return r;
        }

        @Test
        @DisplayName("Before its moment nothing; after it, a fresh fire that is due")
        void firesOnce() {
            UpcomingReminder r = reminder();
            assertThat(ReminderSchedule.isFreshFire(r, fire, fire.minusSeconds(1))).isFalse();
            assertThat(ReminderSchedule.isDue(r, fire, fire.minusSeconds(1))).isFalse();
            assertThat(ReminderSchedule.isFreshFire(r, fire, fire)).isTrue();
            assertThat(ReminderSchedule.isDue(r, fire, fire)).isTrue();

            r.setLastFiredFor(fire); // the dispatcher sent it
            assertThat(ReminderSchedule.isFreshFire(r, fire, fire.plusSeconds(60))).isFalse();
            assertThat(ReminderSchedule.isDue(r, fire, fire.plusSeconds(60))).as("sent, not answered").isTrue();
        }

        @Test
        @DisplayName("A moment from before the reminder existed never fires")
        void notBeforeItExisted() {
            UpcomingReminder r = reminder();
            Instant before = created.minusSeconds(3600);
            assertThat(ReminderSchedule.isFreshFire(r, before, created)).isFalse();
            assertThat(ReminderSchedule.isDue(r, before, created)).isFalse();
        }

        @Test
        @DisplayName("Answered, it is no longer due; a deadline moved later re-arms it")
        void answeredThenMovedLater() {
            UpcomingReminder r = reminder();
            r.setLastFiredFor(fire);
            r.setAnsweredAt(fire.plusSeconds(30));
            assertThat(ReminderSchedule.isDue(r, fire, fire.plusSeconds(60))).isFalse();

            Instant later = fire.plusSeconds(7 * 86400);
            assertThat(ReminderSchedule.isFreshFire(r, later, later)).isTrue();
            assertThat(ReminderSchedule.isDue(r, later, later)).isTrue();
        }

        @Test
        @DisplayName("A deadline moved earlier, into the past, does not fire a second time")
        void movedEarlierDoesNotRepeat() {
            UpcomingReminder r = reminder();
            r.setLastFiredFor(fire);
            r.setAnsweredAt(fire.plusSeconds(30));
            Instant earlier = fire.minusSeconds(86400);
            assertThat(ReminderSchedule.isFreshFire(r, earlier, fire.plusSeconds(60))).isFalse();
            assertThat(ReminderSchedule.isDue(r, earlier, fire.plusSeconds(60))).isFalse();
        }

        @Test
        @DisplayName("Snoozed, it is quiet until the snooze runs out, then due and sent again")
        void snooze() {
            UpcomingReminder r = reminder();
            r.setLastFiredFor(fire);
            r.setSnoozedUntil(fire.plusSeconds(600));
            assertThat(ReminderSchedule.isDue(r, fire, fire.plusSeconds(599))).isFalse();
            assertThat(ReminderSchedule.isSnoozeOver(r, fire.plusSeconds(599))).isFalse();
            assertThat(ReminderSchedule.isDue(r, fire, fire.plusSeconds(600))).isTrue();
            assertThat(ReminderSchedule.isSnoozeOver(r, fire.plusSeconds(600))).isTrue();
        }
    }

    @Test
    @DisplayName("A link reads its item's deadline live")
    void linkReadsDeadline() {
        Goal goal = new Goal();
        goal.setDeadline(midnight("2030-06-19", BERLIN));
        UpcomingEvent event = new UpcomingEvent();
        event.setKind("goal");
        event.setGoal(goal);
        UpcomingReminder r = new UpcomingReminder();
        r.setEvent(event);
        r.setAmount(0);
        r.setUnit("day");
        r.setAtMinuteOfDay(540);
        assertThat(ReminderSchedule.fireAt(r, BERLIN)).isEqualTo(at("2030-06-19T09:00", BERLIN));

        goal.setDeadline(midnight("2030-06-26", BERLIN));
        assertThat(ReminderSchedule.fireAt(r, BERLIN)).isEqualTo(at("2030-06-26T09:00", BERLIN));
    }
}
