package com.spiramindscape.backend.upcoming;

import com.spiramindscape.backend.goal.Goal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/** The notification's words: the event's name, then when and what — English, no emoji. */
class ReminderNoticeTest {

    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    private static final Instant NOW = Instant.parse("2030-06-19T07:00:00Z");

    private static UpcomingEvent important(String date, LocalTime time) {
        UpcomingEvent e = new UpcomingEvent();
        e.setId(7L);
        e.setKind("important");
        e.setTitle("Mom's birthday");
        e.setEventDate(Instant.parse(date));
        e.setEventTime(time);
        return e;
    }

    @Test
    @DisplayName("Today at a time, tomorrow, a countdown, and a date further out")
    void when() {
        assertThat(ReminderService.notice(important("2030-06-18T22:00:00Z", LocalTime.of(14, 0)), BERLIN, NOW))
                .isEqualTo(new ReminderNotice(7L, "Mom's birthday", "Today at 14:00 · Important date", true));
        assertThat(ReminderService.notice(important("2030-06-19T22:00:00Z", null), BERLIN, NOW).body())
                .isEqualTo("Tomorrow · Important date");
        assertThat(ReminderService.notice(important("2030-06-25T22:00:00Z", null), BERLIN, NOW).body())
                .isEqualTo("In 7 days · Important date");
        assertThat(ReminderService.notice(important("2030-07-14T22:00:00Z", null), BERLIN, NOW).body())
                .isEqualTo("Mon, Jul 15 · Important date");
    }

    @Test
    @DisplayName("A link is named after its item")
    void link() {
        Goal goal = new Goal();
        goal.setTitle("Run a marathon");
        goal.setDeadline(Instant.parse("2030-06-18T22:00:00Z"));
        UpcomingEvent e = new UpcomingEvent();
        e.setId(8L);
        e.setKind("goal");
        e.setGoal(goal);
        assertThat(ReminderService.notice(e, BERLIN, NOW))
                .isEqualTo(new ReminderNotice(8L, "Run a marathon", "Today · Goal", true));
    }

    @Test
    @DisplayName("A notice ahead of the day only informs; one on the day asks for a decision")
    void onTheDay() {
        UpcomingEvent tomorrow = important("2030-06-19T22:00:00Z", null);
        UpcomingReminder dayBefore = new UpcomingReminder();
        dayBefore.setEvent(tomorrow);
        dayBefore.setAmount(1);
        dayBefore.setUnit("day");
        dayBefore.setAtMinuteOfDay(540);
        tomorrow.getReminders().add(dayBefore);
        assertThat(ReminderService.notice(tomorrow, BERLIN, NOW).onTheDay()).isFalse();

        UpcomingEvent today = important("2030-06-18T22:00:00Z", null);
        UpcomingReminder onTheDay = new UpcomingReminder();
        onTheDay.setEvent(today);
        onTheDay.setAmount(0);
        onTheDay.setUnit("day");
        onTheDay.setAtMinuteOfDay(540);
        today.getReminders().add(onTheDay);
        assertThat(ReminderService.notice(today, BERLIN, NOW).onTheDay()).isTrue();
    }
}
