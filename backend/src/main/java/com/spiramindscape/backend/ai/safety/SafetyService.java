package com.spiramindscape.backend.ai.safety;

import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

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
 *
 * <p><b>How a term is matched, and why it is not a plain substring.</b> Every term is
 * compiled once into a pattern anchored at a <i>word start</i>, plus — for long
 * multi-word terms only — a separator-free form for catching "b o m b"-style spacing
 * evasion. It used to be `normalized.contains(normalizedTerm)`, and combined with the
 * {@link TextNormalizer} defect documented there, that refused a user's own Swedish job
 * advert as CSAM and logged an abuse event against them. Two rules keep that from
 * recurring:
 * <ul>
 *   <li><b>a term must start where a word starts</b>, so "loli" cannot be found inside
 *       "…lolika" and "csam" cannot be found inside "…icsamount";</li>
 *   <li><b>the end is deliberately NOT anchored</b>, so inflected languages still match
 *       ("суицид" catches "суицида") — the app's users write Russian and Swedish.</li>
 * </ul>
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
                    // "doxx" is matched as a whole word (see WHOLE_WORD_MAX_LENGTH), so
                    // its inflections are listed rather than left to a loose match.
                    "how to stalk", "track someone without", "doxx", "doxxing", "doxxed",
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

    // ── Matching ────────────────────────────────────────────────────────────

    /**
     * At or below this length a term must match a WHOLE word, not just its start.
     *
     * <p>Four characters is short enough to begin an innocent word — "loli" begins
     * "lolita", "csam" could begin a name — and none of the terms this short ("csam",
     * "loli", "doxx") inflect in any of the languages here, so anchoring both ends of
     * them costs no recall. Anything longer keeps its end open, because Russian and
     * Swedish inflect and "суицид" has to catch "суицида". Where the rule does cost a
     * form we want, the form is added to the list instead of loosening the rule —
     * "doxxing" alongside "doxx".
     */
    private static final int WHOLE_WORD_MAX_LENGTH = 4;

    /** Separators someone spaces a word out with: "b o m b", "b.o.m.b", "b-o-m-b". */
    private static final String SEPARATORS = "[\\s._\\-*]*";

    /**
     * One term, compiled once into the one pattern that has to match it.
     *
     * <p>The pattern is the term's own letters <b>with a separator allowed between every
     * one of them</b>, anchored at a word start:
     * {@code (?<!\p{L})k[\s._\-*]*i[\s._\-*]*l[\s._\-*]*l[\s._\-*]*m…}. That single shape
     * covers all three cases at once — the term written normally, the term spaced out
     * letter by letter, and a term whose own words have been run together — while the
     * word-start anchor is what stops it reaching across two innocent words.
     *
     * <p><b>This replaced a length-floored fallback that had a hole in it.</b> The first
     * fix compiled the term literally and kept a separator-free comparison for terms of
     * twelve characters or more; a review found that "k i l l m y s e l f" then matched
     * neither form — the literal pattern still contained a space the text no longer had,
     * and "killmyself" is ten characters, under the floor. Every multi-word term shorter
     * than that had lost its evasion coverage: "child porn", "make meth", "end my life",
     * "self harm", "не хочу жить". Making the separators optional needs no floor, so
     * there is no length at which coverage quietly stops.
     */
    private record Term(Pattern boundary) {
        static Term of(String raw) {
            String normalized = TextNormalizer.normalize(raw);
            StringBuilder sb = new StringBuilder("(?<!\\p{L})");
            boolean first = true;
            for (int i = 0; i < normalized.length(); i++) {
                char c = normalized.charAt(i);
                // The term's own separators become the same optional separator as the gap
                // between two of its letters: one rule, and "kill myself" then matches
                // "killmyself" without a second pass over different text.
                if (!Character.isLetterOrDigit(c)) continue;
                if (!first) sb.append(SEPARATORS);
                sb.append(Pattern.quote(String.valueOf(c)));
                first = false;
            }
            // A short term must match a WHOLE word — see WHOLE_WORD_MAX_LENGTH.
            if (normalized.length() <= WHOLE_WORD_MAX_LENGTH) sb.append("(?!\\p{L})");
            return new Term(Pattern.compile(sb.toString()));
        }

        boolean matches(String text) {
            return boundary.matcher(text).find();
        }
    }

    private static List<Term> compile(List<String> raw) {
        // A term with no letters or digits in it would compile to the anchor alone, which
        // matches at the start of nearly every message — and would refuse all of them.
        // "Not blank" is not enough: a term of zero-width characters normalises to "".
        return raw.stream()
                .filter(t -> t != null && t.codePoints().anyMatch(Character::isLetterOrDigit))
                .map(Term::of)
                .toList();
    }

    private static Map<SafetyCategory, List<Term>> compile(Map<SafetyCategory, List<String>> raw) {
        // EnumMap, not the source Map.of: iteration order decides which category is
        // reported when a message matches two, and that should not vary between runs.
        Map<SafetyCategory, List<Term>> out = new EnumMap<>(SafetyCategory.class);
        raw.forEach((category, terms) -> out.put(category, compile(terms)));
        return out;
    }

    private static final List<Term> CRISIS = compile(CRISIS_TERMS);
    private static final Map<SafetyCategory, List<Term>> REFUSE = compile(REFUSE_TERMS);
    private static final Map<SafetyCategory, List<Term>> REFER = compile(REFER_TERMS);

    /** Classify a user message. Never throws; pure function of the input. */
    public SafetyVerdict classify(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) return SafetyVerdict.ALLOWED;
        String text = TextNormalizer.normalize(userMessage);

        // Crisis takes priority over everything else.
        for (Term term : CRISIS) {
            if (term.matches(text)) return new SafetyVerdict(SafetyCategory.CRISIS);
        }
        for (var entry : REFUSE.entrySet()) {
            for (Term term : entry.getValue()) {
                if (term.matches(text)) return new SafetyVerdict(entry.getKey());
            }
        }
        for (var entry : REFER.entrySet()) {
            for (Term term : entry.getValue()) {
                if (term.matches(text)) return new SafetyVerdict(entry.getKey());
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
