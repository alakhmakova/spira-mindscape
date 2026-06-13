package com.spiramindscape.backend.ai.safety;

/**
 * The result of a safety check: which {@link SafetyCategory} applied. Carries
 * the category so the chat layer can choose the right response (refuse, crisis
 * line, professional referral, or proceed) and the audit log can record it
 * WITHOUT the raw message.
 */
public record SafetyVerdict(SafetyCategory category) {

    public static final SafetyVerdict ALLOWED = new SafetyVerdict(SafetyCategory.ALLOW);

    public boolean isAllowed() {
        return category.isAllowed();
    }

    public SafetyCategory.Disposition disposition() {
        return category.disposition();
    }
}
