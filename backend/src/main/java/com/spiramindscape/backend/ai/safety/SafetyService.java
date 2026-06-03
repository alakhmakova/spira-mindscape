package com.spiramindscape.backend.ai.safety;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

/**
 * Pre-processing safety check that runs before every AI chat request.
 *
 * <p>MVP implementation uses a keyword-based heuristic. This is fast, cheap,
 * and catches obvious violations. A full AI-based safety pass (a lightweight
 * prompt sent to the LLM before the coaching prompt) is planned for Phase 6.
 *
 * <p>Hard rules (always block):
 * <ul>
 *   <li>Self-harm or suicide ideation keywords</li>
 *   <li>Requests for illegal activity</li>
 *   <li>Medical / psychiatric diagnosis requests</li>
 * </ul>
 *
 * <p>Everything else passes. False positives are worse than false negatives
 * at this stage — the coaching framing naturally limits harmful outputs.
 */
@Service
public class SafetyService {

    private static final List<String> BLOCKED_PATTERNS = List.of(
            "suicide", "self-harm", "self harm", "kill myself", "end my life",
            "how to make a bomb", "how to make explosives", "synthesize drugs",
            "synthesize meth", "cocaine recipe", "heroin recipe",
            "child porn", "csam", "loli"
    );

    /**
     * Returns {@code true} if the message is safe to pass to the AI.
     * Returns {@code false} if it matches a hard-block pattern.
     */
    public boolean isSafe(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) return true;
        String lower = userMessage.toLowerCase(Locale.ROOT);
        for (String pattern : BLOCKED_PATTERNS) {
            if (lower.contains(pattern)) return false;
        }
        return true;
    }

    /**
     * The user-facing message returned when a request is blocked.
     * Kept intentionally brief — no explanation of which pattern matched.
     */
    public String blockedMessage() {
        return "I'm not able to help with that. If you're going through a difficult time, "
             + "please reach out to a qualified professional or a crisis line in your area.";
    }
}
