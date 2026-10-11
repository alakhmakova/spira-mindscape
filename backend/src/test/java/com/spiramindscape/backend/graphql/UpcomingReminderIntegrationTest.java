package com.spiramindscape.backend.graphql;

import com.spiramindscape.backend.auth.AppUser;
import com.spiramindscape.backend.support.BaseGraphQlIntegrationTest;
import com.spiramindscape.backend.upcoming.UpcomingEventRepository;
import com.spiramindscape.backend.upcoming.UpcomingReminderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Clock;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Reminders through GraphQL: stored with the event, validated on the way in, replaced on edit
 * without losing what has already gone off, due at the right moment in the user's zone, answered
 * and snoozed — and owned: another user can neither see nor answer them.
 *
 * <p>The clock is a mock, so "now" is whatever the test says. Every date is in 2030, after the
 * moment the rows are really created, because a reminder never fires for a moment from before it
 * existed.
 */
class UpcomingReminderIntegrationTest extends BaseGraphQlIntegrationTest {

    /** Midnight of 2030-06-19 in Berlin — how the web client stores that day. */
    private static final String JUNE_19 = "2030-06-18T22:00:00Z";
    /** 09:00 on the 19th in Berlin. */
    private static final Instant NINE_AM = Instant.parse("2030-06-19T07:00:00Z");

    @MockitoBean private Clock clock;
    @Autowired private UpcomingEventRepository eventRepository;
    @Autowired private UpcomingReminderRepository reminderRepository;

    @BeforeEach
    void berlin() {
        now(Instant.parse("2030-06-01T00:00:00Z"));
        graphQlTester.document("mutation { setTimeZone(zone: \"Europe/Berlin\") }").execute()
                .path("setTimeZone").entity(Boolean.class).isEqualTo(true);
    }

    private void now(Instant instant) {
        when(clock.instant()).thenReturn(instant);
    }

    private String create(String fields) {
        return graphQlTester.document("mutation { createUpcomingEvent(input: { %s }) { id } }".formatted(fields))
                .execute()
                .path("createUpcomingEvent.id").entity(String.class).get();
    }

    private String birthday(String reminders) {
        return create("kind: \"important\", title: \"Mom's birthday\", date: \"%s\", reminders: [%s]"
                .formatted(JUNE_19, reminders));
    }

    private void expectCreateError(String fields, String messagePart) {
        graphQlTester.document("mutation { createUpcomingEvent(input: { %s }) { id } }".formatted(fields))
                .execute()
                .errors().satisfy(errors -> assertThat(errors)
                        .anyMatch(e -> e.getMessage().contains(messagePart)));
    }

    private int dueCount() {
        return graphQlTester.document("{ dueReminders { eventId } }").execute()
                .path("dueReminders").entityList(Object.class).get().size();
    }

    private boolean answer(String eventId, String action) {
        return graphQlTester.document("mutation { answerReminder(eventId: %s, action: \"%s\") }"
                        .formatted(eventId, action))
                .execute()
                .path("answerReminder").entity(Boolean.class).get();
    }

    @Test
    @DisplayName("An event keeps its time and its reminders")
    void storesTimeAndReminders() {
        create("kind: \"important\", title: \"Flight\", date: \"%s\", time: \"14:30\", reminders: [".formatted(JUNE_19)
                + "{ amount: 1, unit: \"hour\" }, { amount: 1, unit: \"day\" }]");

        graphQlTester.document("{ upcomingEvents { time reminders { amount unit atMinuteOfDay channel } } }")
                .execute()
                .path("upcomingEvents[0].time").entity(String.class).isEqualTo("14:30")
                .path("upcomingEvents[0].reminders").entityList(Object.class).hasSize(2)
                .path("upcomingEvents[0].reminders[0].unit").entity(String.class).isEqualTo("hour")
                .path("upcomingEvents[0].reminders[0].atMinuteOfDay").valueIsNull()
                .path("upcomingEvents[0].reminders[0].channel").entity(String.class).isEqualTo("notification");
    }

    @Test
    @DisplayName("An all-day reminder without a time of day means 09:00; an identical one is stored once")
    void allDayDefaultsAndDedupes() {
        birthday("{ amount: 0, unit: \"day\" }, { amount: 0, unit: \"day\", atMinuteOfDay: 540 }");

        graphQlTester.document("{ upcomingEvents { time reminders { amount unit atMinuteOfDay } } }")
                .execute()
                .path("upcomingEvents[0].time").valueIsNull()
                .path("upcomingEvents[0].reminders").entityList(Object.class).hasSize(1)
                .path("upcomingEvents[0].reminders[0].atMinuteOfDay").entity(Integer.class).isEqualTo(540);
    }

    @Test
    @DisplayName("Invalid reminders are refused, and a refused create writes nothing")
    void validation() {
        String base = "kind: \"important\", title: \"Trip\", date: \"%s\"".formatted(JUNE_19);
        expectCreateError(base + ", reminders: [{ amount: 1000, unit: \"day\" }]", "from 0 to 999");
        expectCreateError(base + ", reminders: [{ amount: -1, unit: \"day\" }]", "from 0 to 999");
        expectCreateError(base + ", reminders: [{ amount: 1, unit: \"year\" }]", "Unknown reminder unit");
        expectCreateError(base + ", reminders: [{ amount: 1, unit: \"day\", channel: \"sms\" }]",
                "Unknown reminder channel");
        expectCreateError(base + ", reminders: [{ amount: 5, unit: \"minute\" }]",
                "Minutes and hours before need the event to have a time");
        expectCreateError(base + ", reminders: [{ amount: 1, unit: \"day\", atMinuteOfDay: 1440 }]",
                "from 00:00 to 23:59");
        expectCreateError(base + ", time: \"10:00\", reminders: [{ amount: 1, unit: \"day\", atMinuteOfDay: 540 }]",
                "only for reminders of all-day events");
        expectCreateError(base + ", time: \"25:00\"", "Event time must be HH:mm");
        expectCreateError(base + ", reminders: [{ amount: 1, unit: \"day\" }, { amount: 2, unit: \"day\" }, "
                + "{ amount: 3, unit: \"day\" }, { amount: 4, unit: \"day\" }, { amount: 5, unit: \"day\" }, "
                + "{ amount: 6, unit: \"day\" }]", "at most 5 reminders");

        assertThat(eventRepository.count()).isZero();
        assertThat(reminderRepository.count()).isZero();
    }

    @Test
    @DisplayName("Due at its moment in the user's zone; snooze quiets it for ten minutes; done settles it")
    void dueSnoozeDone() {
        String id = birthday("{ amount: 0, unit: \"day\", atMinuteOfDay: 540 }");

        now(NINE_AM.minusSeconds(1));
        assertThat(dueCount()).isZero();

        now(NINE_AM);
        graphQlTester.document("{ dueReminders { eventId kind title date time goalId firedAt } }").execute()
                .path("dueReminders[0].eventId").entity(String.class).isEqualTo(id)
                .path("dueReminders[0].title").entity(String.class).isEqualTo("Mom's birthday")
                .path("dueReminders[0].kind").entity(String.class).isEqualTo("important")
                .path("dueReminders[0].firedAt").entity(String.class).isEqualTo(NINE_AM.toString());

        assertThat(answer(id, "snooze")).isTrue();
        now(NINE_AM.plusSeconds(599));
        assertThat(dueCount()).isZero();
        now(NINE_AM.plusSeconds(600));
        assertThat(dueCount()).isEqualTo(1);

        assertThat(answer(id, "done")).isTrue();
        assertThat(dueCount()).isZero();
        assertThat(answer(id, "done")).as("answering twice is harmless").isFalse();
    }

    @Test
    @DisplayName("A link is due under its item's name, with the goal to open")
    void linkCarriesItsNameAndGoal() {
        String goalId = graphQlTester.document("""
                        mutation { createGoal(input: { title: "Run a marathon", confidence: 6, deadline: "%s" }) { id } }
                        """.formatted(JUNE_19))
                .execute().path("createGoal.id").entity(String.class).get();
        String targetId = graphQlTester.document("""
                        mutation($g: ID!) { createTarget(goalId: $g, input: { type: "binary", title: "10 km run",
                                                                               deadline: "%s" }) { id } }
                        """.formatted(JUNE_19))
                .variable("g", goalId)
                .execute().path("createTarget.id").entity(String.class).get();
        create("kind: \"target\", targetId: %s, time: \"18:00\", reminders: [{ amount: 1, unit: \"hour\" }]"
                .formatted(targetId));

        now(Instant.parse("2030-06-19T15:00:00Z")); // 17:00 in Berlin
        graphQlTester.document("{ dueReminders { title goalId time } }").execute()
                .path("dueReminders[0].title").entity(String.class).isEqualTo("10 km run")
                .path("dueReminders[0].goalId").entity(String.class).isEqualTo(goalId)
                .path("dueReminders[0].time").entity(String.class).isEqualTo("18:00");
    }

    @Test
    @DisplayName("Saving the same reminders again keeps what has gone off; a new time starts afresh")
    void editKeepsState() {
        String id = birthday("{ amount: 0, unit: \"day\", atMinuteOfDay: 540 }");
        now(NINE_AM.plusSeconds(60));
        answer(id, "done");

        graphQlTester.document("""
                        mutation { updateUpcomingEvent(id: %s, input: {
                          reminders: [{ amount: 0, unit: "day", atMinuteOfDay: 540 }, { amount: 1, unit: "week" }] }) {
                          title reminders { unit } } }
                        """.formatted(id))
                .execute()
                .path("updateUpcomingEvent.title").entity(String.class).isEqualTo("Mom's birthday")
                .path("updateUpcomingEvent.reminders").entityList(Object.class).hasSize(2);
        assertThat(dueCount()).as("the answered one stays answered; the new week-before is from the past").isZero();

        graphQlTester.document("""
                        mutation { updateUpcomingEvent(id: %s, input: { title: "Mum's birthday" }) { title time reminders { id } } }
                        """.formatted(id))
                .execute()
                .path("updateUpcomingEvent.title").entity(String.class).isEqualTo("Mum's birthday")
                .path("updateUpcomingEvent.reminders").entityList(Object.class).hasSize(0);
    }

    @Test
    @DisplayName("Deleting the event deletes its reminders")
    void cascade() {
        String id = birthday("{ amount: 0, unit: \"day\" }, { amount: 1, unit: \"day\" }");
        assertThat(reminderRepository.count()).isEqualTo(2);
        graphQlTester.document("mutation { deleteUpcomingEvent(id: %s) }".formatted(id)).execute();
        assertThat(reminderRepository.count()).isZero();
    }

    @Test
    @DisplayName("Another user cannot see, answer or edit someone's reminders")
    void crossUser() {
        String id = birthday("{ amount: 0, unit: \"day\" }");
        now(NINE_AM);
        assertThat(dueCount()).isEqualTo(1);

        AppUser other = createAdditionalUser("other-sub", "other@example.com");
        setCurrentUser(other);
        assertThat(dueCount()).isZero();
        graphQlTester.document("mutation { answerReminder(eventId: %s, action: \"done\") }".formatted(id))
                .execute()
                .errors().satisfy(errors -> assertThat(errors)
                        .anyMatch(e -> e.getMessage().contains("not found")));
        graphQlTester.document("mutation { updateUpcomingEvent(id: %s, input: { reminders: [] }) { id } }".formatted(id))
                .execute()
                .errors().satisfy(errors -> assertThat(errors)
                        .anyMatch(e -> e.getMessage().contains("not found")));

        setCurrentUser(testUser);
        assertThat(dueCount()).as("still due for its owner").isEqualTo(1);
    }

    @Test
    @DisplayName("Only a real IANA region is accepted as a time zone, and an answer must be a known one")
    void zoneAndAnswerValidation() {
        graphQlTester.document("mutation { setTimeZone(zone: \"+02:00\") }").execute()
                .errors().satisfy(errors -> assertThat(errors)
                        .anyMatch(e -> e.getMessage().contains("Unknown time zone")));
        String id = birthday("");
        graphQlTester.document("mutation { answerReminder(eventId: %s, action: \"delete\") }".formatted(id))
                .execute()
                .errors().satisfy(errors -> assertThat(errors)
                        .anyMatch(e -> e.getMessage().contains("Unknown reminder answer")));
    }
}
