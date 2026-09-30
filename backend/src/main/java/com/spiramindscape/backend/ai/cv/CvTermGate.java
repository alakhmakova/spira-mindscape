package com.spiramindscape.backend.ai.cv;

import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The technology gate: does a document name a tool nothing the user gave supports?
 *
 * <p>{@link CvFactGate} checks numbers and deliberately nothing else. The owner's first real
 * session showed what that leaves open: the writer put JWT authentication, Apollo Client and
 * Testcontainers into her profile — none of them in her repository, her notes or anything she
 * said (2026-09-15). A technology name cannot be paraphrased any more than a number can, so a
 * curated lexicon ({@code prompts/cv/tech-terms.txt}) is checked the same way: a term in the
 * document that does not appear in the corpus is flagged to the user before she approves.
 *
 * <p>Warn, never refuse — the same design as the fact gate. The corpus for a document is her
 * own material and what was read from her sources, never the job advert: an advert's tool
 * list is exactly what must not leak into a CV unbacked.
 */
public final class CvTermGate {

    private CvTermGate() {
    }

    private static final List<String> TERMS = load();
    private static final Pattern TAG = Pattern.compile("<[^>]+>");

    /** Lexicon terms in {@code document} that {@code corpus} does not contain, in order, without duplicates. */
    public static List<String> unsupportedTerms(String document, String corpus) {
        if (document == null || document.isBlank()) return List.of();
        String text = normalise(document);
        String known = normalise(corpus == null ? "" : corpus);
        Set<String> flagged = new LinkedHashSet<>();
        for (String term : TERMS) {
            String t = normalise(term);
            if (contains(text, t) && !contains(known, t)) flagged.add(term);
        }
        // "Spring" flagged next to "Spring Boot" says the same thing twice.
        List<String> out = new ArrayList<>(flagged);
        out.removeIf(a -> out.stream().anyMatch(b -> !b.equals(a)
                && normalise(b).contains(normalise(a))));
        return out;
    }

    /** What the user reads. Warn-only: she knows whether she used it. */
    public static String warning(List<String> terms, String language) {
        if (terms.isEmpty()) return "";
        String list = String.join(", ", terms);
        return switch (CvTransitions.lang(language)) {
            case "ru" -> "\n\nПроверьте перед сохранением: " + list + " — этого нет ни в вашем профиле, "
                    + "ни в прочитанных источниках, ни в ваших ответах. Если вы с этим не работали, "
                    + "скажите мне, и я уберу.";
            case "sv" -> "\n\nKontrollera innan du sparar: " + list + " — det finns inte i din profil, "
                    + "i källorna jag läste eller i dina svar. Har du inte arbetat med det, säg till så "
                    + "tar jag bort det.";
            default -> "\n\nCheck before you save: " + list + " — nothing in your profile, the sources I "
                    + "read or your answers mentions " + (terms.size() == 1 ? "it" : "them")
                    + ". If you have not worked with " + (terms.size() == 1 ? "it" : "them")
                    + ", tell me and I will take " + (terms.size() == 1 ? "it" : "them") + " out.";
        };
    }

    /** Lower case, tags out, and {@code - _ /} as spaces so "spring-boot-starter" contains "spring boot". */
    static String normalise(String raw) {
        String s = TAG.matcher(raw).replaceAll(" ").toLowerCase(Locale.ROOT)
                .replace(' ', ' ')
                .replaceAll("[-_/]", " ")
                .replaceAll("\\s+", " ");
        return " " + s.strip() + " ";
    }

    private static boolean contains(String haystack, String term) {
        String needle = term.strip();
        int from = 0;
        while (true) {
            int i = haystack.indexOf(needle, from);
            if (i < 0) return false;
            boolean startOk = i == 0 || !Character.isLetterOrDigit(haystack.charAt(i - 1));
            int end = i + needle.length();
            boolean endOk = end >= haystack.length() || !Character.isLetterOrDigit(haystack.charAt(end));
            if (startOk && endOk) return true;
            from = i + 1;
        }
    }

    private static List<String> load() {
        ClassPathResource resource = new ClassPathResource("prompts/cv/tech-terms.txt");
        try (var in = resource.getInputStream()) {
            List<String> terms = new ArrayList<>();
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\\R")) {
                String t = line.strip();
                if (!t.isEmpty() && !t.startsWith("#")) terms.add(t);
            }
            // Longest first, so the de-duplication above keeps "Spring Boot" over "Spring".
            terms.sort((a, b) -> Integer.compare(b.length(), a.length()));
            return List.copyOf(terms);
        } catch (IOException e) {
            throw new UncheckedIOException("tech-terms.txt could not be read", e);
        }
    }
}
