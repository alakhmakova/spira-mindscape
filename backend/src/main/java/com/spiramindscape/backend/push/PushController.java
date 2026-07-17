package com.spiramindscape.backend.push;

import com.spiramindscape.backend.auth.CurrentUserProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Device-token registration + a self-test push, for FCM notifications.
 *
 * <p>All endpoints live under {@code /api/**}, so Spring Security requires an authenticated
 * session and enforces CSRF (the app echoes {@code X-XSRF-TOKEN}, like the web). Every action
 * is scoped to the signed-in user — a caller can only register/unregister their own device and
 * can only test-push to their own devices.
 */
@RestController
@RequestMapping("/api/push")
public class PushController {

    private final CurrentUserProvider currentUserProvider;
    private final PushNotificationService pushService;

    public PushController(CurrentUserProvider currentUserProvider,
                          PushNotificationService pushService) {
        this.currentUserProvider = currentUserProvider;
        this.pushService = pushService;
    }

    /** Body for register/unregister: the FCM token and (optional) platform. */
    public record TokenRequest(String token, String platform) {}

    /** Register (or refresh) this device's FCM token for the signed-in user. */
    @PostMapping("/register")
    public ResponseEntity<Void> register(@RequestBody(required = false) TokenRequest body) {
        if (body == null || body.token() == null || body.token().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        Long userId = currentUserProvider.getCurrentUser().getId();
        pushService.register(userId, body.token().trim(), body.platform());
        return ResponseEntity.noContent().build();
    }

    /** Remove this device's token (e.g. on sign-out). */
    @PostMapping("/unregister")
    public ResponseEntity<Void> unregister(@RequestBody(required = false) TokenRequest body) {
        if (body == null || body.token() == null || body.token().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        Long userId = currentUserProvider.getCurrentUser().getId();
        pushService.unregister(userId, body.token().trim());
        return ResponseEntity.noContent().build();
    }

    /**
     * Send a test notification to the caller's own devices. Handy for verifying the whole
     * pipeline (backend → FCM → device) end to end. Returns whether FCM is enabled and how
     * many devices the push reached.
     */
    @PostMapping("/test")
    public ResponseEntity<Map<String, Object>> test() {
        Long userId = currentUserProvider.getCurrentUser().getId();
        int sent = pushService.sendToUser(userId, "Spira",
                "Test push — notifications are working.");
        return ResponseEntity.ok(Map.of("enabled", pushService.isEnabled(), "sent", sent));
    }
}
