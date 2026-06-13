package com.spiramindscape.backend.ai.safety;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Safety check that runs before every AI request and returns a
 * {@link SafetyVerdict}. Two responsibilities:
 * <ol>
 *   <li><b>Refuse misuse</b> the app is not for (weapons, illicit manufacturing,
 *       malware/intrusion, CSAM, targeted harassment).</li>
 *   <li><b>Refer, don't treat</b> — when a message signals a need beyond
 *       coaching (mental-health crisis or distress, medical symptoms, abuse,
 *       legal/financial jeopardy), Spira points to a professional rather than
 *       attempting to help itself.</li>
 * </ol>
 *
 * <p><b>Design note — multilingual:</b> this deterministic layer normalizes
 * text ({@link TextNormalizer}) to defeat obfuscation and carries term sets for
 * several major languages, but it is a HIGH-PRECISION FIRST PASS, not a
 * complete language-agnostic guarantee. The full guarantee (every language,
 * transliteration, paraphrase) is intended to come from an LLM classification
 * pass; see the security spec ({@code spira.safety.llm-classifier.enabled}).
 * This service is pure (no network), so it is always-on, fast, and fully
 * unit-testable.
 *
 * <p>Matching is intentionally tuned so that ordinary hard coaching topics
 * ("I lost my job", "I feel stuck") do NOT trigger referral — referral fires
 * only on strong professional-need signals.
 */
@Service
public class SafetyService {

    // ── Disallowed misuse — refuse ──────────────────────────────────────────
    // Normalized substrings, grouped so the category (hence the audit reason)
    // is precise. Multilingual entries are illustrative coverage of common
    // phrasings, not exhaustive — the LLM layer is the real net.

    private static final Map<SafetyCategory, List<String>> REFUSE_TERMS = Map.of(
            SafetyCategory.WEAPONS, List.of(
                    "how to make a bomb", "build a bomb", "make explosives", "build a gun",
                    "как сделать бомбу", "изготовить взрывчатку", "сделать оружие",
                    "fabriquer une bombe", "bombe bauen", "como hacer una bomba"),
            SafetyCategory.ILLICIT_DRUGS, List.of(
                    "synthesize meth", "make meth", "cook meth", "synthesize drugs",
                    "cocaine recipe", "heroin recipe", "how to make methamphetamine",
                    "как сделать метамфетамин", "синтез наркотиков", "сварить мет"),
            SafetyCategory.MALWARE_INTRUSION, List.of(
                    "write ransomware", "write malware", "create a virus", "build a keylogger",
                    "how to hack into", "sql injection payload", "ddos attack script",
                    "написать вирус", "создать вредоносное", "взломать аккаунт"),
            SafetyCategory.CSAM, List.of(
                    "child porn", "csam", "loli", "underage sexual"),
            SafetyCategory.TARGETED_HARASSMENT, List.of(
                    "how to stalk", "track someone without", "doxx", "doxxing someone",
                    "как выследить человека", "слежка за человеком")
    );

    // ── Self-harm / suicide — crisis (handled with care, not a refusal) ──────
    private static final List<String> CRISIS_TERMS = List.of(
            "suicide", "kill myself", "end my life", "want to die", "self-harm", "self harm",
            "cut myself", "take my own life",
            "покончить с собой", "не хочу жить", "хочу умереть", "свести счеты с жизнью",
            "себя порезать", "суицид",
            "me suicider", "quiero morir", "suizid");

    // ── Professional-need signals — refer out ────────────────────────────────
    // Strong signals only, to avoid over-triggering on normal coaching stress.
    private static final Map<SafetyCategory, List<String>> REFER_TERMS = Map.of(
            SafetyCategory.REFER_MENTAL_HEALTH, List.of(
                    "panic attacks", "i think i'm depressed", "diagnose my depression",
                    "hearing voices", "hallucinating",
                    "паническая атака", "у меня депрессия", "слышу голоса"),
            SafetyCategory.REFER_MEDICAL, List.of(
                    "diagnose my", "what medication should i take", "what's my dosage",
                    "is this a heart attack", "should i stop my medication",
                    "поставь диагноз", "какое лекарство принять", "какая дозировка"),
            SafetyCategory.REFER_ABUSE, List.of(
                    "hits me", "being abused", "domestic violence",
                    "my partner threatens", "меня избивают", "домашнее насилие"),
            SafetyCategory.REFER_LEGAL, List.of(
                    "should i sue", "represent me in court",
                    "подать в суд", "юридическая консультация"),
            SafetyCategory.REFER_FINANCIAL, List.of(
                    "which stocks should i buy", "guarantee me returns",
                    "should i invest my savings in", "какие акции купить")
    );

    /** Classify a user message. Never throws; pure function of the input. */
    public SafetyVerdict classify(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) return SafetyVerdict.ALLOWED;
        String n = TextNormalizer.normalize(userMessage);

        // Crisis takes priority over everything else.
        for (String term : CRISIS_TERMS) {
            if (n.contains(TextNormalizer.normalize(term))) return new SafetyVerdict(SafetyCategory.CRISIS);
        }
        for (var entry : REFUSE_TERMS.entrySet()) {
            for (String term : entry.getValue()) {
                if (n.contains(TextNormalizer.normalize(term))) return new SafetyVerdict(entry.getKey());
            }
        }
        for (var entry : REFER_TERMS.entrySet()) {
            for (String term : entry.getValue()) {
                if (n.contains(TextNormalizer.normalize(term))) return new SafetyVerdict(entry.getKey());
            }
        }
        return SafetyVerdict.ALLOWED;
    }

    /** Back-compat convenience: true unless the message must be refused. */
    public boolean isSafe(String userMessage) {
        return classify(userMessage).disposition() != SafetyCategory.Disposition.REFUSE;
    }

    /**
     * The message shown when a request is refused or routed to crisis support.
     * Brief, never naming which pattern matched. {@code REFER} is NOT handled
     * here — referral is woven into the AI's own reply (in the user's language)
     * via {@link #referInstruction}, so the conversation stays warm.
     */
    public String responseFor(SafetyCategory category) {
        return switch (category.disposition()) {
            case CRISIS -> "I'm really glad you told me, and I'm concerned for you. "
                    + "This is beyond what I can help with as a coach — please reach out right now to a "
                    + "qualified professional or a crisis line in your area. If you're in immediate danger, "
                    + "contact your local emergency number.";
            case REFUSE -> "I can't help with that — it's outside what Spira is for. "
                    + "I'm here to help you think through and act on your goals.";
            default -> ""; // ALLOW / REFER are not blocked here
        };
    }

    /**
     * A short instruction appended to the system prompt when the verdict is
     * {@code REFER}, telling the model to hand off to a professional IN THE
     * USER'S LANGUAGE instead of attempting to treat. Empty for other verdicts.
     */
    public String referInstruction(SafetyCategory category) {
        if (category.disposition() != SafetyCategory.Disposition.REFER) return "";
        String who = switch (category) {
            case REFER_MENTAL_HEALTH -> "a licensed mental-health professional (therapist/psychologist)";
            case REFER_MEDICAL -> "a doctor or other qualified medical professional";
            case REFER_ABUSE -> "a domestic-abuse support service or local authorities";
            case REFER_LEGAL -> "a qualified lawyer";
            case REFER_FINANCIAL -> "a licensed financial adviser";
            default -> "a relevant qualified professional";
        };
        return "\n\nIMPORTANT — DUTY TO REFER: The user's message signals a need beyond coaching. "
                + "Warmly acknowledge it, gently say this is outside what Spira can help with, and "
                + "encourage them to reach out to " + who + ". Do NOT diagnose, prescribe, or give a "
                + "treatment/legal/financial plan, even if asked. Respond in the user's own language. "
                + "Keep it kind and brief; you may still help with any genuinely coaching-related part.";
    }

    /** @deprecated use {@link #responseFor(SafetyCategory)}. */
    @Deprecated
    public String blockedMessage() {
        return responseFor(SafetyCategory.CRISIS);
    }
}
