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
import java.time.ZoneId;

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

    private void snooze(String id, String args) {
        graphQlTester.document("mutation { answerReminder(eventId: %s, action: \"snooze\", %s) }".formatted(id, args))
                .execute().path("answerReminder").entity(Boolean.class).isEqualTo(true);
    }

    private void expectSnoozeError(String id, String args, String messagePart) {
        graphQlTester.document("mutation { answerReminder(eventId: %s, action: \"snooze\", %s) }".formatted(id, args))
                .execute()
                .errors().satisfy(errors -> assertThat(errors)
                        .anyMatch(e -> e.getMessage().contains(messagePart)));
    }

    @Test
    @DisplayName("A snooze lasts the number and unit the user chose; months follow the calendar")
    void snoozeForChosenLength() {
        String id = birthday("{ amount: 0, unit: \"day\", atMinuteOfDay: 540 }");
        now(NINE_AM);

        snooze(id, "snoozeAmount: 3, snoozeUnit: \"hour\"");
        Instant noon = NINE_AM.plusSeconds(3 * 3600);
        now(noon.minusSeconds(1));
        assertThat(dueCount()).isZero();
        now(noon);
        assertThat(dueCount()).isEqualTo(1);

        // Two calendar months in the user's zone: noon on June 19 is due again at noon on Aug 19.
        snooze(id, "snoozeAmount: 2, snoozeUnit: \"month\"");
        Instant august = noon.atZone(ZoneId.of("Europe/Berlin")).plusMonths(2).toInstant();
        now(august.minusSeconds(1));
        assertThat(dueCount()).isZero();
        now(august);
        assertThat(dueCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("A snooze needs a number from 1 to 999 and a known unit, together")
    void snoozeValidation() {
        String id = birthday("{ amount: 0, unit: \"day\", atMinuteOfDay: 540 }");
        now(NINE_AM);
        expectSnoozeError(id, "snoozeAmount: 0, snoozeUnit: \"day\"", "from 1 to 999");
        expectSnoozeError(id, "snoozeAmount: 1000, snoozeUnit: \"day\"", "from 1 to 999");
        expectSnoozeError(id, "snoozeAmount: 2, snoozeUnit: \"year\"", "Unknown snooze unit");
        expectSnoozeError(id, "snoozeAmount: 2", "both a number and a unit");
        assertThat(dueCount()).as("a refused snooze changes nothing").isEqualTo(1);
    }

    private String resolve(String id, String status) {
        return graphQlTester.document(
                        "mutation { resolveUpcomingEvent(id: %s, status: \"%s\") { status resolvedAt } }"
                                .formatted(id, status))
                .execute().path("resolveUpcomingEvent.status").entity(String.class).get();
    }

    @Test
    @DisplayName("Ahead of the day a reminder only informs: I know settles it, and the next one still fires")
    void iKnowAheadOfTheDay() {
        String id = birthday("{ amount: 1, unit: \"day\", atMinuteOfDay: 540 }, "
                + "{ amount: 0, unit: \"day\", atMinuteOfDay: 540 }");

        now(NINE_AM.minusSeconds(86_400));
        graphQlTester.document("{ dueReminders { eventId onTheDay } }").execute()
                .path("dueReminders[0].onTheDay").entity(Boolean.class).isEqualTo(false);
        assertThat(answer(id, "dismiss")).isTrue();
        assertThat(dueCount()).isZero();

        now(NINE_AM);
        graphQlTester.document("{ dueReminders { eventId onTheDay } }").execute()
                .path("dueReminders[0].eventId").entity(String.class).isEqualTo(id)
                .path("dueReminders[0].onTheDay").entity(Boolean.class).isEqualTo(true);
    }

    @Test
    @DisplayName("A settled event leaves Upcoming's pending list, keeps its mark, and never reminds again")
    void resolveSettlesTheEvent() {
        String id = birthday("{ amount: 0, unit: \"day\", atMinuteOfDay: 540 }, "
                + "{ amount: 0, unit: \"day\", atMinuteOfDay: 600 }");
        graphQlTester.document("{ upcomingEvents { status resolvedAt } }").execute()
                .path("upcomingEvents[0].status").entity(String.class).isEqualTo("pending")
                .path("upcomingEvents[0].resolvedAt").valueIsNull();

        now(NINE_AM);
        assertThat(dueCount()).isEqualTo(1);
        assertThat(resolve(id, "wont_do")).isEqualTo("wont_do");
        assertThat(dueCount()).as("a settled event is not due").isZero();
        now(NINE_AM.plusSeconds(3600));
        assertThat(dueCount()).as("its later reminder does not fire either").isZero();

        graphQlTester.document("{ upcomingEvents { status resolvedAt } }").execute()
                .path("upcomingEvents[0].status").entity(String.class).isEqualTo("wont_do")
                .path("upcomingEvents[0].resolvedAt").entity(String.class).isEqualTo(NINE_AM.toString());

        graphQlTester.document("mutation { resolveUpcomingEvent(id: %s, status: \"maybe\") { id } }".formatted(id))
                .execute()
                .errors().satisfy(errors -> assertThat(errors)
                        .anyMatch(e -> e.getMessage().contains("Unknown event status")));
    }

    @Test
    @DisplayName("Another user cannot settle someone's event")
    void resolveIsOwnerScoped() {
        String id = birthday("");
        setCurrentUser(createAdditionalUser("other-sub-2", "other2@example.com"));
        graphQlTester.document("mutation { resolveUpcomingEvent(id: %s, status: \"done\") { id } }".formatted(id))
                .execute()
                .errors().satisfy(errors -> assertThat(errors)
                        .anyMatch(e -> e.getMessage().contains("not found")));
        setCurrentUser(testUser);
        graphQlTester.document("{ upcomingEvents { status } }").execute()
                .path("upcomingEvents[0].status").entity(String.class).isEqualTo("pending");
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
