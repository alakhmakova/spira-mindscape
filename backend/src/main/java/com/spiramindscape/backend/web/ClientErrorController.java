package com.spiramindscape.backend.web;

import com.spiramindscape.backend.auth.AppUserOidcUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Receives errors that happened in the user's browser and writes them to the server log.
 *
 * <p>Until this existed, a JavaScript error in someone else's browser left no trace we
 * could ever see (BUG-005): the SPA logged to that user's own devtools console and nothing
 * more. This is the deliberate alternative to Sentry — the reports land in Cloud Logging
 * next to the backend line that shares their {@code traceId}, with no third-party service
 * and no user data leaving our own infrastructure.
 *
 * <p>Three properties keep an unauthenticated, CSRF-exempt POST safe:
 * <ol>
 *   <li>Its only effect is one log line — there is nothing for a forged request to achieve.</li>
 *   <li>The body is a fixed-field {@code record}. There is no {@code Map<String,Object>} and
 *       no {@code @JsonAnySetter}, so a client physically cannot ship arbitrary data;
 *       unknown JSON fields are dropped by Jackson.</li>
 *   <li>Every field is length-capped, and {@code RateLimitFilter} throttles the path.</li>
 * </ol>
 *
 * @see com.spiramindscape.backend.security.RateLimitFilter
 */
@RestController
@RequestMapping("/api/client-errors")
public class ClientErrorController {

    private static final Logger log = LoggerFactory.getLogger("client.web-error");

    /** Trust our own header, not the body, and keep it short. */
    private static final int MAX_USER_AGENT = 200;

    /**
     * What the browser may report. {@code crash-trail} carries the attachment-crash
     * breadcrumbs described in {@code specs/2026-08-03-attachment-crash-diagnostics/} —
     * step names, timings and sizes serialized into {@code stack}, never image bytes or
     * chat text.
     */
    public record ClientErrorReport(
            @NotBlank
            @Pattern(regexp = "render|window-error|unhandled-rejection|router|api|crash-trail",
                    message = "kind is not a recognised client error kind")
            String kind,

            @Size(max = 120, message = "name must be at most 120 characters")
            String name,

            @NotBlank
            @Size(max = 300, message = "message must be at most 300 characters")
            String message,

            @Size(max = 4000, message = "stack must be at most 4000 characters")
            String stack,

            @Size(max = 300, message = "url must be at most 300 characters")
            String url,

            @Size(max = 40, message = "appVersion must be at most 40 characters")
            String appVersion
    ) {}

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void report(@RequestBody @Valid ClientErrorReport report, HttpServletRequest request) {
        // WARN, not ERROR: a broken browser is worth seeing and alerting on, but it is not
        // the server failing, and mixing the two would make a server-error alert noisy.
        // The stack goes on its own line — the GCP formatter keeps it in one log entry.
        log.warn("web_client_error kind={} name={} url={} appVersion={} userId={} ua={}{}",
                report.kind(),
                blankToPlaceholder(report.name()),
                blankToPlaceholder(report.url()),
                blankToPlaceholder(report.appVersion()),
                currentUserId(),
                userAgent(request),
                describe(report));
    }

    /** The message and stack, appended on their own lines so the entry stays readable. */
    private static String describe(ClientErrorReport report) {
        StringBuilder text = new StringBuilder("\n").append(report.message());
        if (report.stack() != null && !report.stack().isBlank()) {
            text.append("\n").append(report.stack());
        }
        return text.toString();
    }

    /**
     * The signed-in user, or {@code anonymous}. Read straight from the security context —
     * {@code CurrentUserProvider} throws when there is no principal, and this endpoint is
     * reachable (deliberately) by logged-out users, whose errors are the ones we would
     * otherwise never see.
     */
    private static String currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AppUserOidcUser principal) {
            return String.valueOf(principal.getAppUser().getId());
        }
        return "anonymous";
    }

    private static String userAgent(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        if (userAgent == null || userAgent.isBlank()) {
            return "?";
        }
        String trimmed = userAgent.length() > MAX_USER_AGENT
                ? userAgent.substring(0, MAX_USER_AGENT)
                : userAgent;
        // Keep the line parseable: a newline in a header would split the key=value run.
        return trimmed.replaceAll("[\\r\\n]", " ");
    }

    private static String blankToPlaceholder(String value) {
        return (value == null || value.isBlank()) ? "?" : value;
    }
}
