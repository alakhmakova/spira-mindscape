package com.spiramindscape.backend.push;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PushNotificationService}'s send logic — the parts that can't be
 * exercised without a live FCM (so FCM is mocked here). Registration + owner-scoping run
 * against a real DB in {@link DeviceTokenIntegrationTest}.
 */
@ExtendWith(MockitoExtension.class)
class PushNotificationServiceTest {

    @Mock
    private DeviceTokenRepository repository;

    @Mock
    private ObjectProvider<FirebaseMessaging> messagingProvider;

    @Mock
    private FirebaseMessaging messaging;

    private PushNotificationService service() {
        return new PushNotificationService(repository, messagingProvider);
    }

    private DeviceToken token(Long userId, String value) {
        DeviceToken t = new DeviceToken();
        t.setUserId(userId);
        t.setToken(value);
        return t;
    }

    @Test
    @DisplayName("When FCM is not configured, sending is a no-op and touches no devices")
    void disabledIsNoOp() {
        when(messagingProvider.getIfAvailable()).thenReturn(null);

        PushNotificationService service = service();

        assertThat(service.isEnabled()).isFalse();
        assertThat(service.sendToUser(1L, "t", "b")).isZero();
        verify(repository, never()).findByUserId(any());
    }

    @Test
    @DisplayName("Sends one message per registered device and counts the successes")
    void sendsToEachDevice() throws Exception {
        when(messagingProvider.getIfAvailable()).thenReturn(messaging);
        when(repository.findByUserId(1L)).thenReturn(List.of(token(1L, "a"), token(1L, "b")));
        when(messaging.send(any(Message.class))).thenReturn("msg-id");

        int sent = service().sendToUser(1L, "Title", "Body");

        assertThat(sent).isEqualTo(2);
        verify(messaging, times(2)).send(any(Message.class));
    }

    @Test
    @DisplayName("A token FCM reports as gone (UNREGISTERED) is pruned, others still counted")
    void prunesDeadToken() throws Exception {
        DeviceToken live = token(1L, "live");
        DeviceToken dead = token(1L, "dead");
        when(messagingProvider.getIfAvailable()).thenReturn(messaging);
        when(repository.findByUserId(1L)).thenReturn(List.of(live, dead));

        FirebaseMessagingException gone = org.mockito.Mockito.mock(FirebaseMessagingException.class);
        when(gone.getMessagingErrorCode()).thenReturn(MessagingErrorCode.UNREGISTERED);
        when(messaging.send(any(Message.class))).thenReturn("msg-id").thenThrow(gone);

        int sent = service().sendToUser(1L, "Title", "Body");

        assertThat(sent).isEqualTo(1);
        verify(repository).delete(dead);
        verify(repository, never()).delete(live);
    }
}
