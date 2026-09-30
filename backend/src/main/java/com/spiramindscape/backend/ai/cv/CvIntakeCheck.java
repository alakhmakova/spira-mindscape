package com.spiramindscape.backend.ai.cv;

import com.spiramindscape.backend.resource.NoteEdit;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * What the intake form is missing, so the writer asks only for that.
 *
 * <p>The intake form ("anketa") is the dry skeleton a CV needs: contact details, links, employers,
 * education, courses. Deliberately NOT the prose describing the work — that is collected against
 * the advert's requirements in step 4, which is what makes the answers usable (owner, 2026-09-16).
 *
 * <p>Headings are matched by keyword in English, Swedish and Russian, because the user writes her
 * own form in whatever language she likes; contact details and links are also recognised by shape,
 * since people put those at the top without a heading. Nothing here blocks anything: she decides
 * when her details are complete.
 */
public final class CvIntakeCheck {

    private CvIntakeCheck() {
    }

    public record Result(List<String> missing, boolean hasLinks, int chars) {
        public boolean complete() {
            return missing.isEmpty();
        }
    }

    private record Part(String name, Pattern heading) {
    }

    private static final List<Part> PARTS = List.of(
            new Part("Contact", Pattern.compile("contact|kontakt|контакт|personal details|personuppgifter|личные данные")),
            // "employer" as well as "employment": the server's own intake list asks for
            // **Employers** by that word (see CvTransitions), and matching only "employment" meant
            // a form written to the app's own instructions was told its employers were missing —
            // so the writer asked again for what she had already given.
            new Part("Employers", Pattern.compile(
                    "experience|erfarenhet|employment|employer|arbetsgivare|work|arbete|anställning|опыт|работ")),
            new Part("Education", Pattern.compile("education|utbildning|образован|examen")),
            new Part("Courses", Pattern.compile("course|kurs|certifi|курс|стаж|internship|praktik|volunt|волонт")),
            // **A CV's language line is skeleton, and step 4 cannot recover it.** There is no
            // sensible "tell me about a time you were fluent in Swedish", so a form taken without
            // it simply loses the line — and on an advert whose must-haves include
            // "Goda kunskaper i svenska och engelska" that is a requirement the CV then cannot
            // answer (owner's live run, 2026-09-16).
            new Part("Languages", Pattern.compile("language|språk|sprak|язык|языки")));

    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+\\.[\\w.]+");
    private static final Pattern PHONE = Pattern.compile("(?m)(\\+\\d[\\d\\s()-]{7,})");
    private static final Pattern LINK = Pattern.compile("(?i)(github|gitlab|linkedin|bitbucket)\\.(com|org)/[\\w.-]+");

    /**
     * A language with a level on one line — "Svenska – flytande", "English: fluent".
     *
     * <p>People write these in the header beside the phone number as often as under a heading of
     * their own, exactly as they do with contact details.
     */
    private static final Pattern LANGUAGE_LINE = Pattern.compile("(?iu)\\b("
            + "svenska|engelska|ryska|danska|norska|finska|tyska|franska|spanska|polska|ukrainska"
            + "|swedish|english|russian|danish|norwegian|finnish|german|french|spanish|polish|ukrainian"
            + "|шведск\\p{L}*|английск\\p{L}*|русск\\p{L}*|датск\\p{L}*|немецк\\p{L}*|французск\\p{L}*"
            + ")\\b\\s*[–—:\\-]\\s*\\p{L}");

    public static Result check(String html) {
        String body = html == null ? "" : html;
        List<String> headings = new ArrayList<>();
        for (String h : NoteEdit.sections(body)) headings.add(h.toLowerCase(Locale.ROOT));
        List<String> missing = new ArrayList<>();
        for (Part part : PARTS) {
            boolean found = headings.stream().anyMatch(h -> part.heading().matcher(h).find());
            // Contact details are usually written at the top, with no heading of their own.
            if (!found && part.name().equals("Contact")) {
                found = EMAIL.matcher(body).find() && PHONE.matcher(body).find();
            }
            if (!found && part.name().equals("Languages")) {
                found = LANGUAGE_LINE.matcher(body).find();
            }
            if (!found) missing.add(part.name());
        }
        boolean links = LINK.matcher(body).find();
        if (!links) missing.add("Links");
        return new Result(missing, links, body.length());
    }
}
