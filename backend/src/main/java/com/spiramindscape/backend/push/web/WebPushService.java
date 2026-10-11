package com.spiramindscape.backend.push.web;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.List;

/**
 * Subscriptions and sending. Every write is for the signed-in user only; the dispatcher sends to
 * a user by id.
 */
@Service
@RequiredArgsConstructor
public class WebPushService {

    /** A person with more browsers than this is unusual; the oldest falls off. */
    public static final int MAX_PER_USER = 10;

    private final WebPushSubscriptionRepository repository;
    private final WebPushSender sender;
    private final WebPushProperties properties;

    public boolean enabled() {
        return properties.enabled();
    }

    public String publicKey() {
        return properties.publicKey();
    }

    /** Validates and stores (or refreshes) a subscription for {@code userId}. */
    @Transactional
    public void subscribe(Long userId, String endpoint, String p256dh, String auth, String userAgent) {
        if (!WebPushEndpoints.isAllowed(endpoint)) {
            // A fixed message: the URL the client sent is not echoed back.
            throw new IllegalArgumentException("This push endpoint is not accepted");
        }
        try {
            WebPushCrypto.publicKey(WebPushCrypto.decode(p256dh));
            if (WebPushCrypto.decode(auth).length != 16) {
                throw new GeneralSecurityException("auth must be 16 bytes");
            }
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalArgumentException("The subscription keys are not valid");
        }

        String trimmed = endpoint.trim();
        WebPushSubscription subscription = repository.findByEndpoint(trimmed).orElseGet(WebPushSubscription::new);
        subscription.setUserId(userId);
        subscription.setEndpoint(trimmed);
        subscription.setP256dh(p256dh.trim());
        subscription.setAuth(auth.trim());
        subscription.setUserAgent(userAgent == null ? null
                : userAgent.substring(0, Math.min(userAgent.length(), 300)));
        subscription.setLastSeenAt(Instant.now());
        repository.save(subscription);

        List<WebPushSubscription> all = repository.findByUserIdOrderByLastSeenAtDesc(userId);
        if (all.size() > MAX_PER_USER) {
            repository.deleteAll(all.subList(MAX_PER_USER, all.size()));
        }
    }

    @Transactional
    public void unsubscribe(Long userId, String endpoint) {
        if (endpoint != null) {
            repository.deleteByUserIdAndEndpoint(userId, endpoint.trim());
        }
    }

    /**
     * Sends a payload to every browser the user has subscribed; drops the ones the push service
     * says are gone. Returns how many accepted it.
     */
    @Transactional
    public int sendToUser(Long userId, String json) {
        if (!properties.enabled()) {
            return 0;
        }
        byte[] payload = json.getBytes(StandardCharsets.UTF_8);
        int sent = 0;
        for (WebPushSubscription subscription : repository.findByUserIdOrderByLastSeenAtDesc(userId)) {
            switch (sender.send(subscription, payload)) {
                case SENT -> sent++;
                case GONE -> repository.delete(subscription);
                case FAILED -> { }
            }
        }
        return sent;
    }
}
