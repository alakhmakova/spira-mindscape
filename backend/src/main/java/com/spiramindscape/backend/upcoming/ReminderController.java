package com.spiramindscape.backend.upcoming;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * What the service worker needs, over the user's own session: the words of a notification (a
 * push only carries an event id, so no goal text sits in a push service's queue) and the answer
 * from a notification's own buttons. Under {@code /api/**}: authenticated, CSRF on the POST,
 * owner-scoped — another user's event is a 404, the same as a missing one.
 */
@RestController
@RequestMapping("/api/reminders")
@RequiredArgsConstructor
public class ReminderController {

    private final ReminderService reminderService;

    public record AnswerRequest(String action) {}

    @GetMapping("/{eventId}/notice")
    public ResponseEntity<ReminderNotice> notice(@PathVariable Long eventId) {
        return reminderService.notice(eventId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/{eventId}/answer")
    public ResponseEntity<Map<String, Object>> answer(@PathVariable Long eventId,
                                                      @RequestBody(required = false) AnswerRequest body) {
        try {
            boolean any = reminderService.answer(eventId, body == null ? null : body.action());
            return ResponseEntity.ok(Map.of("answered", any));
        } catch (IllegalArgumentException e) {
            return e.getMessage().startsWith("Upcoming event not found")
                    ? ResponseEntity.notFound().build()
                    : ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
