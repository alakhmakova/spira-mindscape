package com.spiramindscape.backend.ai.safety;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Normalizes text before safety matching so trivial obfuscation can't slip a
 * disallowed request past the heuristic layer. Pure, dependency-free, fast.
 *
 * <p>Handles: Unicode compatibility folding (NFKC) + accent stripping, zero-
 * width and control characters, common homoglyphs, leetspeak digit/symbol
 * substitution, and inter-character spacing ("b o m b" / "b.o.m.b").
 *
 * <p>This is a HEURISTIC aid, not the whole defense. The language-agnostic guarantee is
 * meant to come from an LLM classifier — <b>which does not exist yet</b>, so in practice
 * this IS the whole keyword layer, and it must therefore favour precision: a false match
 * costs a real user their request and writes an abuse event against their account.
 */
public final class TextNormalizer {

    private TextNormalizer() {}

    public static String normalize(String input) {
        if (input == null || input.isEmpty()) return "";

        // 1) Compatibility decomposition (e.g. ﬁ→fi, fullwidth→ascii) + strip
        //    combining marks (accents), so "bömb"/"ｂｏｍｂ" fold to "bomb".
        String s = Normalizer.normalize(input, Normalizer.Form.NFKC);
        s = Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");

        s = s.toLowerCase(Locale.ROOT);

        // 2) Remove zero-width / control / formatting characters.
        s = s.replaceAll("[\\p{Cf}\\p{Cc}\\u200B-\\u200D\\uFEFF]", "");

        // 3) Common homoglyphs → ASCII (Cyrillic/Greek look-alikes).
        s = mapHomoglyphs(s);

        // 4) Leetspeak: only inside word-like runs, so we don't mangle real numbers.
        s = deLeet(s);

        // 5) Collapse separators used to space out a word ("b o m b", "b.o.m.b").
        s = collapseSpacedLetters(s);
        s = s.replaceAll("\\s+", " ").trim();

        return s;
    }

    /**
     * Join letters that were spaced out one at a time — and NOTHING else.
     *
     * <p>A separator is dropped only when the letter on each side stands alone, so
     * "b o m b" and "b.o.m.b" fold to "bomb" while ordinary prose keeps its word
     * boundaries.
     *
     * <p><b>This used to delete the separator between ANY two letters.</b> The regex
     * carried no single-letter constraint although the comment above it claimed one, so
     * every long paste became one unbroken run of letters with no word boundaries left.
     * {@link SafetyService} matched by plain substring, so innocent text manufactured
     * disallowed terms out of nothing: Swedish "till olika" became "tillolika", which
     * contains "loli" — a CSAM term. The request was refused before it ever reached a
     * provider, and a CSAM abuse event was logged against the user's own account
     * (reported 2026-09-09). It is not a quirk of Swedish: "topics among" gives
     * "topicsamong", which contains "csam" in plain English.
     */
    private static String collapseSpacedLetters(String s) {
        // Left side: a letter with no letter before it. Right side: a letter with no
        // letter after it. Both lookarounds read the ORIGINAL string, so a whole run of
        // single letters still collapses in one pass.
        return s.replaceAll("(?<=(?<!\\p{L})\\p{L})[\\s._\\-*]+(?=\\p{L}(?!\\p{L}))", "");
    }

    private static String mapHomoglyphs(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            sb.append(switch (c) {
                case 'а' -> 'a';   // Cyrillic a
                case 'е', 'ё' -> 'e';
                case 'о' -> 'o';   // Cyrillic o
                case 'р' -> 'p';   // Cyrillic r
                case 'с' -> 'c';   // Cyrillic s
                case 'х' -> 'x';   // Cyrillic h
                case 'у' -> 'y';   // Cyrillic u
                case 'к' -> 'k';
                case 'м' -> 'm';
                case 'т' -> 't';
                case 'в' -> 'b';
                case 'н' -> 'h';
                case 'і', 'ı' -> 'i';
                case 'ѕ' -> 's';
                case 'α' -> 'a';   // Greek alpha
                case 'ο' -> 'o';   // Greek omicron
                case 'ρ' -> 'p';   // Greek rho
                default -> c;
            });
        }
        return sb.toString();
    }

    private static String deLeet(String s) {
        // Apply only to tokens that contain at least one letter, so pure numbers
        // (dates, amounts) are untouched.
        StringBuilder out = new StringBuilder(s.length());
        for (String token : s.split("(?<=\\s)|(?=\\s)")) {
            if (token.chars().anyMatch(Character::isLetter)) {
                token = token
                        .replace('0', 'o').replace('1', 'i').replace('3', 'e')
                        .replace('4', 'a').replace('5', 's').replace('7', 't')
                        .replace('@', 'a').replace('$', 's').replace('!', 'i');
            }
            out.append(token);
        }
        return out.toString();
    }
}
