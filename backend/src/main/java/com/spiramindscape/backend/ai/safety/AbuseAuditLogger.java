package com.spiramindscape.backend.ai.safety;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Privacy-safe audit of safety decisions (OWASP A09 — security logging).
 *
 * <p>Records the category, disposition, session type and (when known) the user
 * id — but NEVER the raw message content. This makes repeated abuse visible
 * (e.g. a Cloud Logging alert on a spike of REFUSE events from one user)
 * without retaining what people typed.
 */
@Component
public class AbuseAuditLogger {

    private static final Logger log = LoggerFactory.getLogger("security.ai-safety");

    /** Logs a non-ALLOW verdict. ALLOW is the common path and is not logged. */
    public void record(SafetyVerdict verdict, String sessionType, Long userId) {
        if (verdict == null || verdict.isAllowed()) return;
        log.info("ai_safety_event disposition={} category={} sessionType={} userId={}",
                verdict.disposition(), verdict.category(),
                sessionType == null ? "chat" : sessionType,
                userId == null ? "?" : userId);
    }
}
