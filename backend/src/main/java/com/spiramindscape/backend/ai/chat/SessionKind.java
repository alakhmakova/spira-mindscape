package com.spiramindscape.backend.ai.chat;

/**
 * Which assistant is speaking on this turn.
 *
 * <p>This used to be a {@code boolean isGrow} threaded through
 * {@link AiChatService} in a dozen places. A third assistant (the CV and cover
 * letter writer) turns every one of those into a three-way question, and a
 * second boolean beside the first is how that spreads into combinations nobody
 * meant — {@code isGrow == false && isCv == false} being the only one that means
 * "ordinary chat". An enum makes the set closed and every branch exhaustive.
 *
 * <p>The wire value is the {@code sessionType} field of {@code ChatRequest},
 * which is client-supplied and therefore untrusted: anything unrecognised —
 * including {@code null}, which every pre-existing client sends — is
 * {@link #CHAT}. Failing closed to the least-privileged assistant is deliberate;
 * {@link #CHAT} is the one that gets no session tools and no coaching method.
 */
public enum SessionKind {

    /** The ordinary assistant. The default, and what an unknown value falls back to. */
    CHAT,

    /** A GROW coaching session: the coach's method, a clock, and {@code end_session}. */
    GROW,

    /** The CV and cover letter writer: one vacancy, its own phases, no clock. */
    CV;

    /**
     * Parse a {@code sessionType} from the wire. Case-insensitive, and never throws —
     * an unrecognised value is {@link #CHAT} rather than a 400, because this field has
     * always been optional and clients that predate a given assistant simply omit it.
     */
    public static SessionKind from(String raw) {
        if (raw == null) return CHAT;
        String v = raw.strip();
        if (GROW.name().equalsIgnoreCase(v)) return GROW;
        if (CV.name().equalsIgnoreCase(v)) return CV;
        return CHAT;
    }

    /** The wire value, for logging and for building a request. */
    public String wireValue() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * Does this assistant run as a session with a beginning and an end, rather than an
     * open-ended conversation? True for GROW and CV, false for chat.
     *
     * <p>What it gates is deliberately narrow — the things a *session* has no business
     * doing mid-flow. It is not a synonym for "has session tools": those differ between
     * GROW and CV and are chosen per kind.
     */
    public boolean isSession() {
        return this != CHAT;
    }
}
