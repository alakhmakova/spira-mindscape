package com.spiramindscape.backend.push.web;

import com.spiramindscape.backend.auth.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Web Push for the browser: the VAPID public key, and subscribe / unsubscribe for the signed-in
 * user. Under {@code /api/**}, so a session and CSRF are required, and the subscribe endpoint has
 * its own small rate limit ({@code RateLimitFilter}).
 */
@RestController
@RequestMapping("/api/push/web")
@RequiredArgsConstructor
public class WebPushController {

    private final WebPushService webPushService;
    private final CurrentUserProvider currentUserProvider;

    public record Keys(String p256dh, String auth) {}

    /** The shape of {@code PushSubscription.toJSON()} in the browser. */
    public record SubscriptionRequest(String endpoint, Keys keys) {}

    public record EndpointRequest(String endpoint) {}

    @GetMapping("/key")
    public Map<String, Object> key() {
        return webPushService.enabled()
                ? Map.of("enabled", true, "publicKey", webPushService.publicKey())
                : Map.of("enabled", false);
    }

    @PostMapping("/subscribe")
    public ResponseEntity<Map<String, String>> subscribe(
            @RequestBody(required = false) SubscriptionRequest body,
            @RequestHeader(value = "User-Agent", required = false) String userAgent) {
        if (body == null || body.endpoint() == null || body.keys() == null
                || body.keys().p256dh() == null || body.keys().auth() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "A subscription is required"));
        }
        try {
            webPushService.subscribe(currentUserProvider.getCurrentUser().getId(), body.endpoint(),
                    body.keys().p256dh(), body.keys().auth(), userAgent);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (DataIntegrityViolationException e) {
            // Two tabs of one browser subscribed at once and the other stored this endpoint a
            // moment earlier: the outcome the caller wanted is already true.
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/unsubscribe")
    public ResponseEntity<Void> unsubscribe(@RequestBody(required = false) EndpointRequest body) {
        if (body == null || body.endpoint() == null) {
            return ResponseEntity.badRequest().build();
        }
        webPushService.unsubscribe(currentUserProvider.getCurrentUser().getId(), body.endpoint());
        return ResponseEntity.noContent().build();
    }
}
