package com.spiramindscape.backend.ai.cv;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads a vacancy map document for the CV coach: what is in it, what is still empty, and the
 * material a CV is written from.
 *
 * <p>The map is the user's own page (specs/2026-09-17-vacancy-map/), so everything read out of it
 * is her data — the caller fences what this returns before it goes near a prompt. The reading is
 * forgiving for the same reason the web client's is: the document is written a field at a time by
 * two writers, and a missing branch means "empty", never an error.
 *
 * <p>Pure: a {@link JsonNode} in, text or numbers out.
 */
public final class VacancyMapDocument {

    /** How much of one field the prompt shows. The whole map goes into every turn of the step. */
    private static final int FIELD_CHARS = 500;

    private VacancyMapDocument() {
    }

    /** What the map holds, counted. */
    public record Counts(int skills, int skillsTicked, int qualities, int qualitiesTicked,
                         int requirements, int important, int requirementsAnswered, int additional) {

        /** Requirements with no answer under any employer — what the CV cannot yet stand behind. */
        public int requirementsOpen() {
            return requirements - requirementsAnswered;
        }
    }

    public static Counts counts(JsonNode doc) {
        List<JsonNode> skills = items(doc, "skills");
        List<JsonNode> qualities = items(doc, "qualities");
        List<JsonNode> requirements = items(doc, "requirements");
        return new Counts(
                skills.size(),
                (int) skills.stream().filter(s -> s.path("checked").asBoolean(false)).count(),
                qualities.size(),
                (int) qualities.stream().filter(s -> s.path("checked").asBoolean(false)).count(),
                requirements.size(),
                (int) requirements.stream().filter(r -> r.path("important").asBoolean(false)).count(),
                (int) requirements.stream().filter(VacancyMapDocument::answered).count(),
                items(doc, "additional").size());
    }

    /** An employer column's name: her own label, or "Company N" by position. */
    public static String companyLabel(JsonNode company, int index) {
        String label = text(company, "label");
        return label.isBlank() ? "Company " + (index + 1) : label.strip();
    }

    /**
     * The map as the coach sees it while it is being filled: every part, what is in it, what is
     * empty, and the JSON Pointer path of each field so a write can name exactly one.
     */
    public static String promptView(JsonNode doc) {
        StringBuilder sb = new StringBuilder();
        JsonNode facts = doc.path("facts");
        sb.append("FACTS (the advert's conditions; her own comment beside three of them)\n");
        fact(sb, facts, "jobTitle", "job title", null);
        fact(sb, facts, "location", "location", null);
        fact(sb, facts, "link", "link", null);
        fact(sb, facts, "education", "education", "educationNote");
        fact(sb, facts, "experience", "years of experience", "experienceNote");
        fact(sb, facts, "language", "language", "languageNote");
        fact(sb, facts, "deadline", "deadline", null);

        Counts c = counts(doc);
        sb.append("\nSKILLS — ").append(c.skills()).append(", ").append(c.skillsTicked())
                .append(" ticked (a tick means she has it)\n");
        checkItems(sb, items(doc, "skills"), "skills");

        sb.append("\nPERSONAL QUALITIES — ").append(c.qualities()).append(", ").append(c.qualitiesTicked())
                .append(" ticked (shown in a CV by an example, never by the adjective)\n");
        checkItems(sb, items(doc, "qualities"), "qualities");

        sb.append("\nREQUIREMENTS — ").append(c.requirements()).append(", ").append(c.requirementsAnswered())
                .append(" with an answer. Each has one answer box per employer she has worked for.\n");
        List<JsonNode> requirements = items(doc, "requirements");
        for (int i = 0; i < requirements.size(); i++) {
            JsonNode r = requirements.get(i);
            sb.append("  [").append(i).append("] ")
                    .append(r.path("important").asBoolean(false) ? "VERY IMPORTANT · " : "")
                    // She has said outright that she cannot meet this one (owner, 2026-09-20).
                    .append(r.path("unmet").asBoolean(false) ? "SHE CANNOT MEET THIS · " : "")
                    .append(quote(text(r, "text"))).append("  (/requirements/").append(i).append(")\n");
            List<JsonNode> companies = arr(r.path("companies"));
            for (int j = 0; j < companies.size(); j++) {
                JsonNode company = companies.get(j);
                sb.append("      ").append(companyLabel(company, j))
                        .append(text(company, "label").isBlank() ? " (unnamed)" : "")
                        .append(": ").append(orEmpty(text(company, "text")))
                        .append("  (/requirements/").append(i).append("/companies/").append(j).append(")\n");
            }
        }

        sb.append("\nADDITIONAL INFORMATION — worth saying in the letter or the CV profile\n");
        List<JsonNode> additional = items(doc, "additional");
        for (int i = 0; i < additional.size(); i++) {
            JsonNode a = additional.get(i);
            sb.append("  [").append(i).append("] ").append(quote(text(a, "text")))
                    .append(" · tag: ").append(tagName(text(a, "tag")))
                    .append(" · her account: ").append(orEmpty(text(a, "detail")))
                    .append("  (/additional/").append(i).append(")\n");
        }
        if (additional.isEmpty()) sb.append("  (none)\n");

        JsonNode company = doc.path("company");
        sb.append("\nCOMPANY (/company)\n")
                .append("  name: ").append(orEmpty(text(company, "name"))).append('\n')
                .append("  link: ").append(orEmpty(text(company, "link"))).append('\n')
                .append("  about: ").append(orEmpty(text(company, "about"))).append('\n');
        List<JsonNode> comments = arr(company.path("comments"));
        sb.append("  her comments: ").append(comments.isEmpty() ? "(none)" : "").append('\n');
        for (int i = 0; i < comments.size(); i++) {
            sb.append("    [").append(i).append("] ").append(orEmpty(text(comments.get(i), "text")))
                    .append("  (/company/comments/").append(i).append(")\n");
        }
        return sb.toString();
    }

    /**
     * Everything the CV and the letter may be written from, as it stands on her map.
     *
     * <p>The owner's two rules decide what is left out (2026-09-17): a skill that is not ticked is
     * not hers, and a requirement with no answer under any employer is one the CV cannot stand
     * behind — it is named so she can be told, never filled in.
     */
    public static String material(JsonNode doc) {
        StringBuilder sb = new StringBuilder();
        JsonNode facts = doc.path("facts");
        for (String[] f : new String[][] {
                {"education", "educationNote", "Education"},
                {"experience", "experienceNote", "Years of experience"},
                {"language", "languageNote", "Languages"}}) {
            String note = text(facts, f[1]);
            if (!note.isBlank()) {
                sb.append(f[2]).append(" — the advert asks: ").append(orEmpty(text(facts, f[0])))
                        .append("; she says: ").append(clip(note)).append('\n');
            }
        }

        List<JsonNode> skills = items(doc, "skills");
        List<String> has = new ArrayList<>();
        List<String> not = new ArrayList<>();
        for (JsonNode s : skills) {
            String label = text(s, "text");
            if (label.isBlank()) continue;
            if (s.path("checked").asBoolean(false)) {
                String said = commentsOf(s);
                has.add(said.isEmpty() ? label : label + " (she says: " + said + ")");
            } else {
                not.add(label);
            }
        }
        if (!has.isEmpty()) {
            sb.append("Skills she HAS (these go in the competence block): ").append(String.join("; ", has)).append('\n');
        }
        if (!not.isEmpty()) {
            sb.append("Skills she has NOT ticked (never claim these anywhere): ").append(String.join(", ", not)).append('\n');
        }

        List<JsonNode> requirements = items(doc, "requirements");
        if (!requirements.isEmpty()) {
            sb.append("\nHER ANSWERS to the employer's requirements, employer by employer "
                    + "(each becomes a bullet under THAT employer):\n");
            for (JsonNode r : requirements) {
                sb.append("  • ").append(r.path("important").asBoolean(false) ? "[most important — lead the "
                        + "summary with it] " : "").append(clip(text(r, "text"))).append('\n');
                boolean any = false;
                List<JsonNode> companies = arr(r.path("companies"));
                for (int j = 0; j < companies.size(); j++) {
                    String answer = text(companies.get(j), "text");
                    if (answer.isBlank()) continue;
                    any = true;
                    sb.append("      at ").append(companyLabel(companies.get(j), j)).append(": ")
                            .append(clip(answer)).append('\n');
                }
                if (!any) {
                    sb.append("      (no answer — leave it OUT of the documents and tell her it is missing "
                            + "when you show the CV)\n");
                }
            }
        }

        List<JsonNode> qualities = items(doc, "qualities");
        List<String> shown = new ArrayList<>();
        for (JsonNode q : qualities) {
            if (!q.path("checked").asBoolean(false)) continue;
            String said = commentsOf(q);
            shown.add(text(q, "text") + (said.isEmpty() ? " (no example yet — name the quality only beside an "
                    + "example from elsewhere)" : ": " + said));
        }
        if (!shown.isEmpty()) {
            sb.append("\nHER PERSONAL QUALITIES and the examples that show them (name the quality beside "
                    + "each):\n");
            for (String s : shown) sb.append("  • ").append(s).append('\n');
        }

        List<JsonNode> additional = items(doc, "additional");
        List<String> extras = new ArrayList<>();
        for (JsonNode a : additional) {
            String detail = text(a, "detail");
            String tag = text(a, "tag");
            if (detail.isBlank() && "none".equals(tagName(tag))) continue;
            extras.add(clip(text(a, "text")) + " [for the " + tagName(tag) + "]"
                    + (detail.isBlank() ? "" : ": " + clip(detail)));
        }
        if (!extras.isEmpty()) {
            sb.append("\nADDITIONAL INFORMATION she wants used:\n");
            for (String e : extras) sb.append("  • ").append(e).append('\n');
        }

        JsonNode company = doc.path("company");
        String about = text(company, "about");
        List<JsonNode> comments = arr(company.path("comments"));
        if (!about.isBlank() || !comments.isEmpty()) {
            sb.append("\nTHE COMPANY (motivation for the letter, not claims about her):\n");
            if (!about.isBlank()) sb.append("  ").append(clip(about)).append('\n');
            for (JsonNode n : comments) {
                String t = text(n, "text");
                if (!t.isBlank()) sb.append("  • her note: ").append(clip(t)).append('\n');
            }
        }
        return sb.toString();
    }

    /**
     * Every word SHE put on the map, run together — what the fact and term gates check a proposed
     * document against. The advert's own lines are deliberately not in here: an employer's figures
     * and technologies must not pass as hers.
     */
    public static String evidenceText(JsonNode doc) {
        StringBuilder sb = new StringBuilder();
        JsonNode facts = doc.path("facts");
        for (String field : List.of("educationNote", "experienceNote", "languageNote")) {
            append(sb, text(facts, field));
        }
        for (String branch : List.of("skills", "qualities")) {
            for (JsonNode item : items(doc, branch)) {
                for (JsonNode comment : arr(item.path("comments"))) append(sb, text(comment, "text"));
            }
        }
        for (JsonNode r : items(doc, "requirements")) {
            for (JsonNode company : arr(r.path("companies"))) {
                append(sb, text(company, "label"));
                append(sb, text(company, "text"));
            }
        }
        for (JsonNode a : items(doc, "additional")) append(sb, text(a, "detail"));
        // Her notes about the company, but not `about`: that is usually lifted from the advert, and a
        // company's "400 people" must not let a CV line claiming 400 reports pass the fact gate.
        for (JsonNode n : arr(doc.path("company").path("comments"))) append(sb, text(n, "text"));
        return sb.toString();
    }

    // ── Reading helpers ─────────────────────────────────────────────────────

    private static boolean answered(JsonNode requirement) {
        for (JsonNode company : arr(requirement.path("companies"))) {
            if (!text(company, "text").isBlank()) return true;
        }
        return false;
    }

    private static void fact(StringBuilder sb, JsonNode facts, String field, String label, String noteField) {
        sb.append("  ").append(label).append(": ").append(orEmpty(text(facts, field)))
                .append("  (/facts/").append(field).append(")");
        if (noteField != null) {
            sb.append(" · her comment: ").append(orEmpty(text(facts, noteField)))
                    .append("  (/facts/").append(noteField).append(")");
        }
        sb.append('\n');
    }

    private static void checkItems(StringBuilder sb, List<JsonNode> list, String branch) {
        if (list.isEmpty()) {
            sb.append("  (none)\n");
            return;
        }
        for (int i = 0; i < list.size(); i++) {
            JsonNode item = list.get(i);
            String said = commentsOf(item);
            sb.append("  [").append(i).append("] ").append(orEmpty(text(item, "text")))
                    .append(item.path("checked").asBoolean(false) ? " · ticked" : " · not ticked")
                    .append(said.isEmpty() ? "" : " · comments: " + said)
                    .append("  (/").append(branch).append('/').append(i).append(")\n");
        }
    }

    private static String commentsOf(JsonNode item) {
        List<String> out = new ArrayList<>();
        for (JsonNode c : arr(item.path("comments"))) {
            String t = text(c, "text");
            if (t.isBlank()) continue;
            // A comment may name the employer it is about (owner, 2026-09-18) — the writer needs
            // that to put the example under the right job.
            String company = text(c, "company");
            out.add(company.isBlank() ? clip(t) : "at " + clip(company) + ": " + clip(t));
        }
        return String.join(" | ", out);
    }

    private static String tagName(String tag) {
        return switch (tag == null ? "" : tag) {
            case "cover_letter" -> "cover letter";
            case "profile" -> "CV profile";
            default -> "none";
        };
    }

    private static List<JsonNode> items(JsonNode doc, String branch) {
        return arr(doc.path(branch));
    }

    private static List<JsonNode> arr(JsonNode node) {
        List<JsonNode> out = new ArrayList<>();
        if (node != null && node.isArray()) node.forEach(out::add);
        return out;
    }

    private static String text(JsonNode node, String field) {
        if (node == null) return "";
        JsonNode value = node.path(field);
        return value.isTextual() ? value.asText() : "";
    }

    private static String orEmpty(String value) {
        return value == null || value.isBlank() ? "(empty)" : quote(value);
    }

    private static String quote(String value) {
        return "\"" + clip(value) + "\"";
    }

    private static String clip(String value) {
        String v = value == null ? "" : value.strip();
        return v.length() <= FIELD_CHARS ? v : v.substring(0, FIELD_CHARS - 1) + "…";
    }

    private static void append(StringBuilder sb, String text) {
        if (text != null && !text.isBlank()) sb.append(text).append('\n');
    }
}
