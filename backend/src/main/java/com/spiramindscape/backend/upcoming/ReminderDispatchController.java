package com.spiramindscape.backend.upcoming;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The scheduler's entry point. Not a user endpoint: it lives under {@code /internal/**}, which has
 * its own security chain ({@code InternalSecurityConfig}) that admits only an OIDC token from the
 * scheduler's service account.
 */
@RestController
@RequiredArgsConstructor
public class ReminderDispatchController {

    private final ReminderDispatcher dispatcher;

    @PostMapping("/internal/reminders/dispatch")
    public ReminderDispatcher.Result dispatch() {
        return dispatcher.dispatch();
    }
}
