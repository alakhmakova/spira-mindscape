package com.spiramindscape.backend.ai.cv;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The deterministic fact gate (spec §9.9): does this document claim a figure the user never
 * gave?
 *
 * <p>"Never invent a number" is in the writer's method, and a rule in a prompt is a rule a
 * model can talk itself out of — usually while being helpful, filling a gap it thinks it is
 * meant to fill. This check runs in code, on the finished document, every time, and cannot
 * be argued with.
 *
 * <p><b>It checks NUMBERS, and deliberately nothing else.</b> The writer's whole job is to
 * rewrite the user's substance in better words ("The substance is the client's. The wording
 * is yours"), so checking names, titles or technologies against what the user typed would
 * flag the rewriting the feature exists to do — and worse, the method REQUIRES tool names
 * and the job title to match the advert exactly, so those would fail a check against the
 * user's own words. A number is the one class of fact that cannot be paraphrased: 40% is
 * either something they told you or something you made up.
 *
 * <p>Three kinds of number are checked, chosen so that ordinary CV detail does not trip it:
 * <ul>
 *   <li><b>percentages</b> — "cut defects by 40%";</li>
 *   <li><b>money</b> — "saved 250 000 SEK";</li>
 *   <li><b>anything of three digits or more</b> — years, headcounts, volumes.</li>
 * </ul>
 * A one- or two-digit number on its own is not checked: those are version numbers
 * ("Java 17"), small counts and list markers, where a false accusation would cost more than
 * the check is worth. That is a stated limit, not an oversight — see the spec.
 *
 * <p>Pure and dependency-free, so the corpus can come from anywhere and the whole thing is
 * unit-testable. What the corpus IS matters as much as the check: it is the recorded
 * evidence plus the user's own notes, never the job advert. Including the advert would
 * license exactly the fabrication this guards against — copying an employer's numbers into
 * a candidate's CV.
 */
public final class CvFactGate {

    private CvFactGate() {}

    /** A digit run, after the separators inside it have been folded away. */
    private static final Pattern NUMBER = Pattern.compile("\\d+");

    /** Separators that appear INSIDE one number: 1 200, 1,200, 3.5. */
    private static final Pattern INNER_SEPARATOR =
            Pattern.compile("(?<=\\d)[.,\\s\\u00A0](?=\\d)");

    private static final Pattern TAG = Pattern.compile("<[^>]+>");

    /**
     * Currency words and symbols, either side of the figure.
     *
     * <p><b>Anchored on both sides, and it has to be.</b> Unanchored, every one of these
     * fragments lives inside an ordinary Swedish word — {@code sek} in "sektion" and
     * "sekund", {@code kr} in "krav" and "kronor", {@code nok} in "Nokia" — and the window
     * this is searched in is only six characters wide, so "worked with 12 European
     * clients" promoted a two-digit number to a money claim and told the user their own
     * "12 EUR" was invented (found in review, 2026-09-10). The symbols are exempt from the
     * letter boundaries: no word contains a currency sign.
     */
    private static final Pattern MONEY_CONTEXT = Pattern.compile(
            "(?<!\\p{L})(kr|sek|eur|usd|gbp|nok|dkk|msek|tkr)(?!\\p{L})|[$€£]",
            Pattern.CASE_INSENSITIVE);

    private static final int UNCHECKED_DIGITS = 2;

    /**
     * Figures in {@code document} that nothing in {@code corpus} supports.
     *
     * @return the offending figures as the document writes them, in the order they appear,
     *         without duplicates — empty when the document makes no unsupported claim
     */
    public static List<String> unsupportedFigures(String document, String corpus) {
        if (document == null || document.isBlank()) return List.of();
        String text = normalise(document);
        String known = normalise(corpus == null ? "" : corpus);
        Set<String> knownNumbers = numbersIn(known);
        Set<String> knownPercentages = percentagesIn(known);

        Set<String> flagged = new LinkedHashSet<>();
        Matcher m = NUMBER.matcher(text);
        while (m.find()) {
            String token = m.group();
            boolean percentage = isPercentage(text, m.end());
            // **A percentage has to have been GIVEN as a percentage.** Plain membership is
            // not enough here: "cut defects by 40%" was passing on the strength of "40
            // minutes" sitting somewhere else in the interview, which is not the same
            // claim in any sense a reader would recognise. Percentages are also the most
            // commonly invented figure on a CV, so they get the strict test; a derived one
            // ("from 100 to 60, so 40%") is flagged too, and rightly — the user should
            // confirm arithmetic done on their behalf.
            boolean supported = percentage
                    ? knownPercentages.contains(token)
                    : knownNumbers.contains(token);
            if (supported) continue;
            if (!percentage && !isChecked(token, text, m.start(), m.end())) continue;
            flagged.add(display(token, text, m.end()));
        }
        return new ArrayList<>(flagged);
    }

    /**
     * What the writer says to the user when the gate finds something.
     *
     * <p>Addressed to the person, not to the model: they are the only one who knows the real
     * figure, and the document is not saved until they approve it. That is the whole design
     * of this app's proposals, and it makes the gate an ally of the user rather than a
     * silent filter over the model.
     */
    public static String warning(List<String> figures) {
        if (figures.isEmpty()) return "";
        return "\n\nBefore you approve that — "
                + (figures.size() == 1 ? "this figure is" : "these figures are")
                + " not in anything you have told me: " + String.join(", ", figures)
                + ". I should not have put "
                + (figures.size() == 1 ? "it" : "them")
                + " there. Give me the real "
                + (figures.size() == 1 ? "number" : "numbers")
                + ", or tell me to take "
                + (figures.size() == 1 ? "it" : "them")
                + " out.";
    }

    /** Is this figure one of the kinds the gate rules on? (Percentages are handled above.) */
    private static boolean isChecked(String token, String text, int start, int end) {
        if (token.length() > UNCHECKED_DIGITS) return true;
        // Money, named on either side — a small amount is still a claim about money.
        String around = text.substring(Math.max(0, start - 6), start)
                + text.substring(end, Math.min(text.length(), end + 6));
        return MONEY_CONTEXT.matcher(around).find();
    }

    /** "40%", "40 %", "40 percent", "40 procent" — the same claim, however it is written. */
    private static boolean isPercentage(String text, int end) {
        String after = text.substring(end, Math.min(text.length(), end + 9)).stripLeading()
                .toLowerCase(Locale.ROOT);
        return after.startsWith("%") || after.startsWith("percent") || after.startsWith("procent");
    }

    private static Set<String> percentagesIn(String text) {
        Set<String> found = new LinkedHashSet<>();
        Matcher m = NUMBER.matcher(text);
        while (m.find()) {
            if (isPercentage(text, m.end())) found.add(m.group());
        }
        return found;
    }

    /** The figure with its unit, so the user reads "40%" rather than a bare "40". */
    private static String display(String token, String text, int end) {
        String after = text.substring(end, Math.min(text.length(), end + 5));
        if (after.stripLeading().startsWith("%")) return token + "%";
        Matcher money = MONEY_CONTEXT.matcher(after);
        return money.lookingAt() || (after.startsWith(" ") && money.find() && money.start() <= 1)
                ? token + " " + money.group().toUpperCase(Locale.ROOT)
                : token;
    }

    /** Tags out, numbers made comparable: "1 200" and "1,200" both become "1200". */
    private static String normalise(String raw) {
        // One pass is enough: the lookarounds read the INPUT rather than the partially
        // built output, so every separator in "1 200 000" is matched independently. This
        // used to loop four times over a comment claiming otherwise.
        return INNER_SEPARATOR.matcher(TAG.matcher(raw).replaceAll(" ")).replaceAll("");
    }

    private static Set<String> numbersIn(String text) {
        Set<String> found = new LinkedHashSet<>();
        Matcher m = NUMBER.matcher(text);
        while (m.find()) found.add(m.group());
        return found;
    }
}
