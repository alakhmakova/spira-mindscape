package com.spiramindscape.backend.ai.cv;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.spiramindscape.backend.graphql.input.MapPatchInput;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Turns what the analysis read out of an advert into patches for a vacancy map.
 *
 * <p>The mapping is the contract in {@code specs/2026-09-17-vacancy-map/}, and four of its
 * decisions are the owner's (2026-09-17):
 *
 * <ul>
 *   <li><b>A requirement's text IS the advert's sentence</b> — "требования это и есть прямые
 *       цитаты из вакансии". There is no separate quotes field, because the requirement is the
 *       quote.</li>
 *   <li><b>The core message is a requirement marked important</b>, not a section of its own. That
 *       control already exists on the card and means the same thing.</li>
 *   <li><b>A competence's fuller wording becomes a COMMENT on the skill.</b> This is the one place
 *       the advert's own line still earns its keep, and it is a measured defect that it must: the
 *       owner answered "по сути нет" to {@code Bash} where the advert said "Bash <em>eller annan
 *       scripting</em>" and her own project is full of scripting (live run, 2026-09-16). The short
 *       tag alone loses that correction.</li>
 *   <li><b>Nothing is ticked and no answer is invented.</b> Every skill and quality arrives
 *       unchecked and every company slot empty: the advert says what is wanted, the user says what
 *       she has.</li>
 * </ul>
 *
 * <p><b>These patches SET whole branches, so they are only safe on a map that is still empty.</b>
 * That is the first fill, where the analysis is the only writer. Re-reading an advert into a map
 * the user has already answered would discard her work, which is exactly what this design exists
 * to prevent — the caller checks, the same way {@code storeAnalysis} refuses once anything is
 * answered.
 *
 * <p>Pure: no entity, no repository, no clock beyond the ids it mints.
 */
public final class VacancyMapBuild {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** How many employer columns a requirement starts with — the web's DEFAULT_COMPANY_SLOTS. */
    private static final int COMPANY_SLOTS = 3;

    private VacancyMapBuild() {
    }

    /**
     * The patches that fill an empty map from one advert.
     *
     * @param link        the advert's own URL, or the link found inside it, or null
     * @param companyLink the employer's own site, as the advert gave it, or null
     * @param education        what the advert asks for, in its words, or null
     * @param experience       how much experience it asks for, in its words, or null
     * @param jobTitle         the role the advert is for, or null
     * @param language         the languages the candidate must speak, or null
     * @param items            the analysis's items, in any order
     */
    public static List<MapPatchInput> initialPatches(
            String jobTitle, String location, String link, String education, String experience,
            String language, String companyName, String companyLink,
            List<CvApplicationService.Item> items) {

        List<MapPatchInput> patches = new ArrayList<>();
        fact(patches, "jobTitle", jobTitle);
        fact(patches, "location", location);
        fact(patches, "link", link);
        fact(patches, "education", education);
        fact(patches, "experience", experience);
        fact(patches, "language", language);
        if (text(companyName)) patches.add(set("/company/name", string(companyName)));
        if (text(companyLink)) patches.add(set("/company/link", string(companyLink.strip())));

        ArrayNode skills = MAPPER.createArrayNode();
        ArrayNode qualities = MAPPER.createArrayNode();
        ArrayNode requirements = MAPPER.createArrayNode();
        ArrayNode additional = MAPPER.createArrayNode();

        for (CvApplicationService.Item item : items == null ? List.<CvApplicationService.Item>of() : items) {
            String category = item.category() == null ? "requirement" : item.category();
            switch (category) {
                case "competence" -> skills.add(checkItem(item.label(), quoteComments(item)));
                case "trait" -> qualities.add(checkItem(item.label(), contextComments(item)));
                // The core message is a requirement like any other — with `important` set.
                case "core" -> requirements.add(requirement(demandOf(item), true));
                case "requirement" -> requirements.add(requirement(demandOf(item), false));
                case "extra" -> additional.add(additionalItem(item));
                default -> requirements.add(requirement(demandOf(item), false));
            }
        }

        branch(patches, "/skills", skills);
        branch(patches, "/qualities", qualities);
        branch(patches, "/requirements", requirements);
        branch(patches, "/additional", additional);
        return patches;
    }

    // ── Applications started before the map ─────────────────────────────────

    /**
     * One item of the retired requirement map, with whatever she answered for it.
     *
     * @param verdict a competence's yes / no / partial, or null
     * @param answer  her own words: the checklist comment, or her evidence for a requirement or quality
     */
    public record LegacyItem(String category, String label, String demand, String context,
                             String extraUse, List<String> quotes, String verdict, String answer) {
    }

    /**
     * The patches that rebuild an old application's requirement map — and the answers she already
     * gave — as a vacancy map.
     *
     * <p>Her answers are the point: an application started under the six-step process holds them in
     * {@code cv_requirement} rows, and dropping them on the way to the new process would be losing
     * her work. Where each lands:
     *
     * <ul>
     *   <li>a competence she said yes or partly to is <b>ticked</b>, and her comment becomes a
     *       comment on the skill ("Partly: …" when it was partial);</li>
     *   <li>an answer to a requirement goes under <b>Company 1</b> — the old process had no notion
     *       of which employer, so the first column is the honest place and she can move it;</li>
     *   <li>an answer to a quality is ticked and kept as its comment.</li>
     * </ul>
     *
     * <p>Like {@link #initialPatches}, only for a map that is still empty.
     */
    public static List<MapPatchInput> legacyPatches(String location, String link, String companyName,
                                                    List<LegacyItem> items) {
        List<MapPatchInput> patches = new ArrayList<>();
        fact(patches, "location", location);
        fact(patches, "link", link);
        if (text(companyName)) patches.add(set("/company/name", string(companyName)));

        ArrayNode skills = MAPPER.createArrayNode();
        ArrayNode qualities = MAPPER.createArrayNode();
        ArrayNode requirements = MAPPER.createArrayNode();
        ArrayNode additional = MAPPER.createArrayNode();
        for (LegacyItem item : items == null ? List.<LegacyItem>of() : items) {
            String category = item.category() == null ? "requirement" : item.category();
            boolean hasAnswer = text(item.answer());
            switch (category) {
                case "competence" -> {
                    ArrayNode comments = MAPPER.createArrayNode();
                    for (String q : item.quotes() == null ? List.<String>of() : item.quotes()) {
                        if (text(q) && !q.strip().equalsIgnoreCase(item.label() == null ? "" : item.label().strip())) {
                            comments.add(comment(q.strip()));
                        }
                    }
                    boolean partial = "partial".equals(item.verdict());
                    if (hasAnswer) comments.add(comment((partial ? "Partly: " : "") + item.answer().strip()));
                    ObjectNode skill = checkItem(item.label(), comments);
                    skill.put("checked", "yes".equals(item.verdict()) || partial);
                    skills.add(skill);
                }
                case "trait" -> {
                    ArrayNode comments = MAPPER.createArrayNode();
                    if (text(item.context())) comments.add(comment(item.context().strip()));
                    if (hasAnswer) comments.add(comment(item.answer().strip()));
                    ObjectNode quality = checkItem(item.label(), comments);
                    quality.put("checked", hasAnswer);
                    qualities.add(quality);
                }
                case "extra" -> additional.add(additionalItem(new CvApplicationService.Item(
                        "extra", item.label(), item.label(), null, null, null, item.extraUse(), List.of())));
                default -> {
                    String demand = text(item.demand()) ? item.demand() : item.label();
                    ObjectNode requirement = requirement(demand, "core".equals(category));
                    if (hasAnswer) {
                        ((ObjectNode) requirement.get("companies").get(0)).put("text", item.answer().strip());
                    }
                    requirements.add(requirement);
                }
            }
        }
        branch(patches, "/skills", skills);
        branch(patches, "/qualities", qualities);
        branch(patches, "/requirements", requirements);
        branch(patches, "/additional", additional);
        return patches;
    }

    // ── Items the coach adds ────────────────────────────────────────────────

    /**
     * An item the coach appends, given the shape the page expects.
     *
     * <p>A model writing "add a requirement" sends {@code {"text": "…"}} and nothing else. Stored like
     * that, the page would mint a fresh id on every read (so React re-mounted the row on each render)
     * and a requirement would have no employer columns to answer into. So a missing id is minted, and
     * a requirement gets its three blank company slots — the same defaults the page itself uses.
     *
     * @param kind what the item IS, which the caller reads off the path: {@code check} (a skill or a
     *             quality), {@code requirement}, {@code company} (one employer column),
     *             {@code additional}, {@code comment} (on a skill or quality) or {@code note} (the
     *             company block's comments). Not the top-level part: {@code /skills/0/comments/-}
     *             is in "skills" and is a comment.
     */
    public static JsonNode normaliseItem(String kind, JsonNode value) {
        if (value == null || !value.isObject()) return value;
        ObjectNode node = ((ObjectNode) value).deepCopy();
        if (!node.hasNonNull("id") || node.get("id").asText("").isBlank()) node.put("id", id());
        if (!node.has("text")) node.put("text", "");
        switch (kind) {
            case "check" -> {
                if (!node.has("checked")) node.put("checked", false);
                if (!node.path("comments").isArray()) node.set("comments", MAPPER.createArrayNode());
            }
            case "requirement" -> {
                if (!node.has("important")) node.put("important", false);
                if (!node.path("companies").isArray() || node.get("companies").isEmpty()) {
                    node.set("companies", requirement("", false).get("companies"));
                }
            }
            case "company" -> {
                if (!node.has("label")) node.put("label", "");
            }
            case "additional" -> {
                if (!node.has("detail")) node.put("detail", "");
                if (!node.has("tag")) node.put("tag", "none");
                if (!node.has("checked")) node.put("checked", false);
            }
            case "comment" -> {
                if (!node.has("at")) node.put("at", java.time.Instant.now().toString());
            }
            default -> {
            }
        }
        return node;
    }

    /** The employer's own sentence, falling back to the label when the item carries no demand. */
    private static String demandOf(CvApplicationService.Item item) {
        return text(item.demand()) ? item.demand() : item.label();
    }

    private static ObjectNode checkItem(String label, ArrayNode comments) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("id", id());
        node.put("text", label == null ? "" : label);
        node.put("checked", false);
        node.set("comments", comments);
        return node;
    }

    private static ObjectNode requirement(String demand, boolean important) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("id", id());
        node.put("text", demand == null ? "" : demand);
        node.put("important", important);
        ArrayNode companies = MAPPER.createArrayNode();
        for (int i = 0; i < COMPANY_SLOTS; i++) {
            ObjectNode company = MAPPER.createObjectNode();
            company.put("id", id());
            // Blank, so the page numbers it "Company 1", "Company 2" … by position.
            company.put("label", "");
            company.put("text", "");
            companies.add(company);
        }
        node.set("companies", companies);
        return node;
    }

    private static ObjectNode additionalItem(CvApplicationService.Item item) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("id", id());
        node.put("text", item.label() == null ? "" : item.label());
        node.put("detail", "");
        // `letter` is motivation for the covering letter; `flag` is a condition the writer raises
        // with the user at once and is deliberately left untagged — it is not letter material.
        node.put("tag", "letter".equals(item.extraUse()) ? "cover_letter" : "none");
        node.put("checked", false);
        return node;
    }

    /**
     * A competence's quotes, as comments — but only where the quote says more than the tag does.
     * "Java" quoted as "Java" is the same word twice.
     */
    private static ArrayNode quoteComments(CvApplicationService.Item item) {
        ArrayNode out = MAPPER.createArrayNode();
        if (item.quotes() == null) return out;
        String label = item.label() == null ? "" : item.label().strip();
        for (CvApplicationService.Quote quote : item.quotes()) {
            String value = quote == null || quote.text() == null ? "" : quote.text().strip();
            if (value.isEmpty() || value.equalsIgnoreCase(label)) continue;
            out.add(comment(value));
        }
        return out;
    }

    /** A quality's context line — the advert's sentence that implies it. */
    private static ArrayNode contextComments(CvApplicationService.Item item) {
        ArrayNode out = MAPPER.createArrayNode();
        String context = item.context();
        if (text(context) && !context.strip().equalsIgnoreCase(
                item.label() == null ? "" : item.label().strip())) {
            out.add(comment(context.strip()));
        }
        return out;
    }

    private static ObjectNode comment(String value) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("id", id());
        node.put("text", value);
        // The advert's line has no time of its own; the epoch keeps it first in the thread, before
        // anything the user adds later.
        node.put("at", java.time.Instant.EPOCH.toString());
        return node;
    }

    private static void fact(List<MapPatchInput> patches, String field, String value) {
        if (text(value)) patches.add(set("/facts/" + field, string(value.strip())));
    }

    /** An empty branch is not written at all — a map with no qualities simply has none. */
    private static void branch(List<MapPatchInput> patches, String path, ArrayNode node) {
        if (!node.isEmpty()) patches.add(set(path, write(node)));
    }

    private static MapPatchInput set(String path, String json) {
        return new MapPatchInput(path, json);
    }

    private static boolean text(String value) {
        return value != null && !value.isBlank();
    }

    private static String string(String value) {
        return write(MAPPER.getNodeFactory().textNode(value));
    }

    private static String write(com.fasterxml.jackson.databind.JsonNode node) {
        try {
            return MAPPER.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("vacancy map patch could not be written", e);
        }
    }

    /** Short, like the web's `mapItemId` — these identify items inside one JSON value, not rows. */
    private static String id() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
