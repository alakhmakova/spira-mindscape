package com.spiramindscape.backend.ai.safety;

/**
 * The single source of truth for safety categories — used by the classifier,
 * the system-prompt instructions, and the abuse audit log.
 *
 * <p>Two axes are folded into one enum:
 * <ul>
 *   <li>{@code ALLOW} — proceed normally.</li>
 *   <li>{@code CRISIS} / {@code REFER} — the user needs human/professional help;
 *       Spira refers out instead of "treating" (a care decision, not a refusal).</li>
 *   <li>everything else — disallowed misuse Spira refuses to assist with.</li>
 * </ul>
 */
public enum SafetyCategory {

    /** Safe to pass to the AI. */
    ALLOW(Disposition.ALLOW),

    /** Imminent self-harm / suicide — surface a crisis resource, warmly. */
    CRISIS(Disposition.CRISIS),

    /** Beyond coaching: refer to a relevant professional, don't treat. */
    REFER_MENTAL_HEALTH(Disposition.REFER),
    REFER_MEDICAL(Disposition.REFER),
    REFER_ABUSE(Disposition.REFER),
    REFER_LEGAL(Disposition.REFER),
    REFER_FINANCIAL(Disposition.REFER),

    /** Disallowed misuse — Spira will not assist. */
    WEAPONS(Disposition.REFUSE),
    ILLICIT_DRUGS(Disposition.REFUSE),
    MALWARE_INTRUSION(Disposition.REFUSE),
    CSAM(Disposition.REFUSE),
    TARGETED_HARASSMENT(Disposition.REFUSE),
    OTHER_ILLEGAL(Disposition.REFUSE);

    /** What the chat layer should DO with a category. */
    public enum Disposition { ALLOW, REFUSE, CRISIS, REFER }

    private final Disposition disposition;

    SafetyCategory(Disposition disposition) {
        this.disposition = disposition;
    }

    public Disposition disposition() {
        return disposition;
    }

    public boolean isAllowed() {
        return disposition == Disposition.ALLOW;
    }
}
