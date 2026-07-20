package com.spiramindscape.backend.push;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Registers device tokens and sends FCM push notifications to a user's devices.
 *
 * <p>All reads/writes are scoped to a user id — a caller can only touch their own tokens
 * (owner-scoping, see {@code docs/security-model.md}). Sending degrades to a no-op when FCM
 * isn't configured (see {@link FirebaseConfig}), so the app runs fine without push.
 */
@Service
public class PushNotificationService {

    private static final Logger log = LoggerFactory.getLogger(PushNotificationService.class);

    private final DeviceTokenRepository repository;
    /** Null when FCM isn't configured; guarded on every send. */
    private final ObjectProvider<FirebaseMessaging> messagingProvider;

    public PushNotificationService(DeviceTokenRepository repository,
                                   ObjectProvider<FirebaseMessaging> messagingProvider) {
        this.repository = repository;
        this.messagingProvider = messagingProvider;
    }

    /** True when a service-account credential is configured and pushes can actually be sent. */
    public boolean isEnabled() {
        return messagingProvider.getIfAvailable() != null;
    }

    /**
     * Registers (or refreshes) a device token for a user. An FCM token is unique per install,
     * so if it already exists we move it to this user and bump {@code lastSeenAt} — never a
     * duplicate row.
     */
    @Transactional
    public DeviceToken register(Long userId, String token, String platform) {
        DeviceToken device = repository.findByToken(token).orElseGet(DeviceToken::new);
        device.setUserId(userId);
        device.setToken(token);
        if (platform != null && !platform.isBlank()) {
            device.setPlatform(platform);
        }
        device.setLastSeenAt(Instant.now());
        return repository.save(device);
    }

    /** Removes a token — but only if it belongs to this user (owner-scoped). */
    @Transactional
    public void unregister(Long userId, String token) {
        repository.deleteByTokenAndUserId(token, userId);
    }

    /**
     * Sends a notification to every device registered to {@code userId}.
     *
     * <p>Tokens FCM reports as permanently gone ({@code UNREGISTERED} / {@code INVALID_ARGUMENT})
     * are pruned so we don't keep trying dead devices.
     *
     * @return how many devices the push was accepted for (0 when FCM is disabled)
     */
    @Transactional
    public int sendToUser(Long userId, String title, String body) {
        FirebaseMessaging messaging = messagingProvider.getIfAvailable();
        if (messaging == null) {
            log.info("Skipping push to user {} — FCM is not configured.", userId);
            return 0;
        }
        List<DeviceToken> devices = repository.findByUserId(userId);
        int sent = 0;
        for (DeviceToken device : devices) {
            Message message = Message.builder()
                    .setToken(device.getToken())
                    .setNotification(Notification.builder().setTitle(title).setBody(body).build())
                    .build();
            try {
                messaging.send(message);
                sent++;
            } catch (FirebaseMessagingException e) {
                if (isTokenGone(e)) {
                    log.info("Pruning dead FCM token for user {} ({}).", userId, e.getMessagingErrorCode());
                    repository.delete(device);
                } else {
                    log.warn("FCM send to user {} failed: {}", userId, e.getMessage());
                }
            }
        }
        return sent;
    }

    private boolean isTokenGone(FirebaseMessagingException e) {
        MessagingErrorCode code = e.getMessagingErrorCode();
        return code == MessagingErrorCode.UNREGISTERED || code == MessagingErrorCode.INVALID_ARGUMENT;
    }
}
