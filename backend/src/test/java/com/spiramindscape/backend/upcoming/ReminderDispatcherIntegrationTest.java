package com.spiramindscape.backend.upcoming;

import com.spiramindscape.backend.auth.AppUser;
import com.spiramindscape.backend.push.web.WebPushSender;
import com.spiramindscape.backend.push.web.WebPushSubscription;
import com.spiramindscape.backend.push.web.WebPushSubscriptionRepository;
import com.spiramindscape.backend.support.BaseGraphQlIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The dispatcher end to end, with the clock and the push service faked: a reminder is pushed once
 * at its moment, again after a snooze runs out, never twice for one moment, not at all when found
 * an hour late — and the push carries the event's id, never its name.
 */
@TestPropertySource(properties = {
        "spira.webpush.public-key=BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8",
        "spira.webpush.private-key=yfWPiYE-n46HLnH0KqZOF1fJJU3MYrct3AELtAQ-oRw",
})
class ReminderDispatcherIntegrationTest extends BaseGraphQlIntegrationTest {

    private static final String JUNE_19 = "2030-06-18T22:00:00Z"; // Berlin midnight
    private static final Instant NINE_AM = Instant.parse("2030-06-19T07:00:00Z");

    @MockitoBean private Clock clock;
    @MockitoBean private WebPushSender sender;
    @Autowired private ReminderDispatcher dispatcher;
    @Autowired private WebPushSubscriptionRepository subscriptions;
    @Autowired private UpcomingEventRepository events;

    private String eventId;

    @BeforeEach
    void seed() {
        when(clock.instant()).thenReturn(Instant.parse("2030-06-01T00:00:00Z"));
        when(sender.send(any(), any())).thenReturn(WebPushSender.Result.SENT);
        graphQlTester.document("mutation { setTimeZone(zone: \"Europe/Berlin\") }").execute();
        eventId = graphQlTester.document("""
                        mutation { createUpcomingEvent(input: { kind: "important", title: "Mom's birthday",
                          date: "%s", reminders: [{ amount: 0, unit: "day", atMinuteOfDay: 540 }] }) { id } }
                        """.formatted(JUNE_19))
                .execute().path("createUpcomingEvent.id").entity(String.class).get();
        subscribe(testUser, "https://fcm.googleapis.com/fcm/send/mine");
    }

    @AfterEach
    void cleanUp() {
        subscriptions.deleteAll();
        events.deleteAll();
    }

    private void subscribe(AppUser user, String endpoint) {
        WebPushSubscription s = new WebPushSubscription();
        s.setUserId(user.getId());
        s.setEndpoint(endpoint);
        s.setP256dh("BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4");
        s.setAuth("BTBZMqHH6r4Tts7J_aSIgg");
        subscriptions.save(s);
    }

    @Test
    @DisplayName("Pushed once at its moment, with the event id only; a second run sends nothing")
    void firesOnce() {
        assertThat(dispatcher.dispatch(NINE_AM.minusSeconds(1)).fired()).isZero();
        verify(sender, never()).send(any(), any());

        assertThat(dispatcher.dispatch(NINE_AM)).isEqualTo(new ReminderDispatcher.Result(1, 1));
        ArgumentCaptor<byte[]> payload = ArgumentCaptor.forClass(byte[].class);
        verify(sender).send(any(), payload.capture());
        String json = new String(payload.getValue(), StandardCharsets.UTF_8);
        assertThat(json).isEqualTo("{\"type\":\"reminder\",\"eventId\":\"" + eventId + "\"}");
        assertThat(json).doesNotContain("birthday");

        clearInvocations(sender);
        assertThat(dispatcher.dispatch(NINE_AM.plusSeconds(30)).fired()).isZero();
        verify(sender, never()).send(any(), any());
    }

    @Test
    @DisplayName("A snooze that runs out is pushed again; an answered reminder is not")
    void snoozeThenDone() {
        dispatcher.dispatch(NINE_AM);
        when(clock.instant()).thenReturn(NINE_AM.plusSeconds(60));
        graphQlTester.document("mutation { answerReminder(eventId: %s, action: \"snooze\") }".formatted(eventId))
                .execute();
        clearInvocations(sender);

        assertThat(dispatcher.dispatch(NINE_AM.plusSeconds(600)).fired()).isZero();
        assertThat(dispatcher.dispatch(NINE_AM.plusSeconds(660)).pushed()).isEqualTo(1);

        when(clock.instant()).thenReturn(NINE_AM.plusSeconds(700));
        graphQlTester.document("mutation { answerReminder(eventId: %s, action: \"done\") }".formatted(eventId))
                .execute();
        clearInvocations(sender);
        assertThat(dispatcher.dispatch(NINE_AM.plusSeconds(3600)).fired()).isZero();
        verify(sender, never()).send(any(), any());
    }

    @Test
    @DisplayName("Found more than an hour late, it is marked fired without a push — and still due in the app")
    void lateIsSilent() {
        assertThat(dispatcher.dispatch(NINE_AM.plusSeconds(2 * 3600)))
                .isEqualTo(new ReminderDispatcher.Result(1, 0));
        verify(sender, never()).send(any(), any());

        when(clock.instant()).thenReturn(NINE_AM.plusSeconds(2 * 3600));
        graphQlTester.document("{ dueReminders { eventId } }").execute()
                .path("dueReminders[0].eventId").entity(String.class).isEqualTo(eventId);
    }

    @Test
    @DisplayName("Only the owner's browsers are pushed; a subscription the service says is gone is dropped")
    void ownerOnlyAndGone() {
        AppUser other = createAdditionalUser("other-sub", "other@example.com");
        subscribe(other, "https://fcm.googleapis.com/fcm/send/theirs");
        when(sender.send(any(), any())).thenReturn(WebPushSender.Result.GONE);

        dispatcher.dispatch(NINE_AM);

        ArgumentCaptor<WebPushSubscription> target = ArgumentCaptor.forClass(WebPushSubscription.class);
        verify(sender, times(1)).send(target.capture(), any());
        assertThat(target.getValue().getEndpoint()).endsWith("/mine");
        assertThat(subscriptions.findAll()).extracting(WebPushSubscription::getEndpoint)
                .containsExactly("https://fcm.googleapis.com/fcm/send/theirs");
    }
}
