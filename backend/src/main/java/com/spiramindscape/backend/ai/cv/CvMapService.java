package com.spiramindscape.backend.ai.cv;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spiramindscape.backend.graphql.input.CreateResourceInput;
import com.spiramindscape.backend.graphql.input.MapPatchInput;
import com.spiramindscape.backend.resource.Resource;
import com.spiramindscape.backend.resource.ResourceService;
import com.spiramindscape.backend.resource.VacancyMapPatch;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Creates and writes an application's vacancy map — see specs/2026-09-17-vacancy-map/.
 *
 * <p><b>The rule this class exists for:</b> the map is the user's page. The coach fills it in with
 * her, a field at a time, and a field she has already filled is never changed or deleted unless
 * the coach says it has asked her ({@code replace}). That is enforced here, on the server, against
 * the document as it stands at the moment of writing — not asked of the model in its prompt, where
 * it would be a hope rather than a property (owner, 2026-09-17: "если пользователь что-то
 * заполнил, AI не должен это перезаписывать без спроса").
 */
@Service
public class CvMapService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** The parts of the map a write may touch. Anything else — the root, `v` — is refused. */
    private static final Set<String> PARTS =
            Set.of("facts", "skills", "qualities", "requirements", "additional", "company");

    private final CvApplicationService apps;
    private final ResourceService resourceService;

    public CvMapService(CvApplicationService apps, ResourceService resourceService) {
        this.apps = apps;
        this.resourceService = resourceService;
    }

    // ── Creating it ─────────────────────────────────────────────────────────

    /**
     * Writes what the advert said into a new vacancy map, and records it on the application.
     *
     * <p><b>Only ever into a map that does not exist yet.</b> {@link VacancyMapBuild} sets whole
     * parts, which is right for the first fill (the analysis is the only writer) and wrong
     * afterwards: re-reading an advert into a map she has answered would discard her work.
     */
    public Resource createFromAnalysis(Long appId, Long goalId, CvApplicationService.AnalysisMeta meta,
                                       List<CvApplicationService.Item> items) {
        CvApplication app = apps.get(appId);
        Optional<Resource> existing = apps.mapOnGoal(app, app.getMapResourceId());
        if (existing.isPresent()) return existing.get();

        // **A map she made herself is the one this application works on** (owner, 2026-09-23).
        // The writer used to create a second map beside hers and then work only from its own,
        // which reads as ignoring her work outright. An unclaimed map on the goal is adopted
        // instead: if she has already filled it, nothing is written into it at all — those are
        // her answers, and the advert has nothing to add to them that is not already there.
        Optional<Resource> hers = apps.unclaimedMapOnGoal(app);
        if (hers.isPresent()) {
            Resource map = hers.get();
            apps.setMapResource(appId, map.getId());
            if (isBlankMap(map)) resourceService.patchMap(map.getId(), analysisPatches(app, meta, items));
            return map;
        }
        return create(app, goalId, analysisPatches(app, meta, items));
    }

    /** What the advert writes into an empty map. */
    private static List<MapPatchInput> analysisPatches(CvApplication app,
                                                       CvApplicationService.AnalysisMeta meta,
                                                       List<CvApplicationService.Item> items) {
        // **The advert's own link, when the application has none of its own.** An application
        // started from a pasted advert has no URL, and the link inside the text was the only one
        // there was (owner, 2026-09-22).
        String link = app.getVacancyUrl();
        if (link == null || link.isBlank()) link = meta == null ? null : meta.applyUrl();
        return VacancyMapBuild.initialPatches(
                meta == null ? null : meta.role(),
                meta == null ? null : meta.location(),
                link,
                meta == null ? null : meta.education(),
                meta == null ? null : meta.experience(),
                meta == null ? null : meta.requiredLanguages(),
                meta == null ? null : meta.company(),
                meta == null ? null : meta.companyUrl(),
                items);
    }

    /** Whether a map holds nothing the user (or anyone) has written into it yet. */
    private static boolean isBlankMap(Resource map) {
        String data = map.getMapData();
        if (data == null || data.isBlank()) return true;
        VacancyMapDocument.Counts c = VacancyMapDocument.counts(VacancyMapPatch.tree(data));
        return c.skills() == 0 && c.qualities() == 0 && c.requirements() == 0 && c.additional() == 0;
    }

    /**
     * The application's map, creating it when it has none.
     *
     * <p>Three ways an application can reach the map step without one: it was started under the
     * six-step process and holds its answers in {@code cv_requirement} rows (converted once, so
     * none of her answers is lost); she deleted the map (built afresh from what the analysis
     * recorded); or the advert stated nothing to extract (an empty map with the facts it did give).
     */
    public Optional<Resource> ensure(Long appId, Long goalId) {
        CvApplication app = apps.pruned(appId);
        Optional<Resource> existing = apps.mapOnGoal(app, app.getMapResourceId());
        if (existing.isPresent()) return existing;
        if (goalId == null || app.getPhase() == CvPhase.ANALYSIS) return Optional.empty();
        List<CvRequirement> rows = apps.legacyRows(appId);
        List<MapPatchInput> patches = rows.isEmpty()
                ? VacancyMapBuild.initialPatches(app.getRoleTitle(), app.getLocation(),
                        app.getVacancyUrl(), null, null, null, app.getCompanyName(), null, List.of())
                : VacancyMapBuild.legacyPatches(app.getLocation(), app.getVacancyUrl(),
                        app.getCompanyName(), legacyItems(rows));
        return Optional.of(create(app, goalId, patches));
    }

    private Resource create(CvApplication app, Long goalId, List<MapPatchInput> patches) {
        // **The map's name IS the vacancy** (owner, 2026-09-17) — not "Requirement map — …", which
        // was the old NOTE's title. What she looks for in her resources is the job.
        String title = app.getTitle() == null || app.getTitle().isBlank()
                ? "Vacancy" : clip(app.getTitle(), ResourceService.MAX_RESOURCE_LABEL_LENGTH);
        Resource map = resourceService.create(goalId, new CreateResourceInput(
                title, "vacancy", null, null, null, null, null, null, null, null));
        if (!patches.isEmpty()) resourceService.patchMap(map.getId(), patches);
        apps.setMapResource(app.getId(), map.getId());
        return map;
    }

    /** The old rows, one item per cluster — the shape the six-step process kept them in. */
    static List<VacancyMapBuild.LegacyItem> legacyItems(List<CvRequirement> rows) {
        Map<String, List<CvRequirement>> groups = new LinkedHashMap<>();
        for (CvRequirement r : rows) {
            groups.computeIfAbsent(r.getCluster() != null ? r.getCluster() : "id:" + r.getId(),
                    k -> new ArrayList<>()).add(r);
        }
        List<VacancyMapBuild.LegacyItem> out = new ArrayList<>();
        for (List<CvRequirement> group : groups.values()) {
            CvRequirement head = group.get(0);
            out.add(new VacancyMapBuild.LegacyItem(
                    head.getCategory() == null ? "requirement" : head.getCategory(),
                    head.getTopic() != null ? head.getTopic() : head.getText(),
                    head.getDemand(), head.getContext(), head.getExtraUse(),
                    group.stream().map(CvRequirement::getText).toList(),
                    head.getUserVerdict(), head.getUserAnswer()));
        }
        return out;
    }

    // ── Writing to it ───────────────────────────────────────────────────────

    /**
     * One field the coach wants to write.
     *
     * @param value   the new value as JSON; {@code null} deletes the field or the item
     * @param replace the coach has asked her and she agreed to replace what she wrote there
     */
    public record Write(String path, JsonNode value, boolean replace) {
    }

    /** What was written, and what was refused with the reason — both go back to the coach. */
    public record WriteResult(List<String> written, List<String> refused) {
    }

    /**
     * Applies the coach's writes, refusing any that would change or delete something she already
     * wrote without her agreement.
     *
     * <p>"Something she wrote" is judged from the value itself, not from who wrote it: a non-blank
     * text, a tick, an item that holds either. Filling an empty field, ticking an unticked box and
     * appending ({@code /skills/-}) are always allowed; turning her tick off, or replacing her
     * answer, needs {@code replace}. The accepted writes go to the server in ONE patch.
     */
    public WriteResult write(Long appId, Long goalId, List<Write> writes) {
        Resource map = ensure(appId, goalId).orElseThrow(() -> new IllegalStateException(
                "this application has no vacancy map yet — the analysis has not finished"));
        String document = map.getMapData();
        List<MapPatchInput> accepted = new ArrayList<>();
        List<String> written = new ArrayList<>();
        List<String> refused = new ArrayList<>();
        for (Write w : writes) {
            String path = w.path() == null ? "" : w.path().strip();
            String problem = pathProblem(path);
            if (problem != null) {
                refused.add(path + " — " + problem);
                continue;
            }
            boolean append = path.endsWith("/-");
            JsonNode current;
            try {
                current = append ? null : VacancyMapPatch.read(document, path);
            } catch (IllegalArgumentException e) {
                refused.add(path + " — " + e.getMessage());
                continue;
            }
            JsonNode value = w.value() == null || w.value().isNull() ? null : w.value();
            if (!w.replace() && holdsHerWork(current) && !current.equals(value)) {
                refused.add(path + " — she already wrote " + preview(current)
                        + ". Ask her before replacing it; if she agrees, send it again with replace=true.");
                continue;
            }
            if (value != null) value = VacancyMapBuild.normaliseItem(kindOf(path), value);
            accepted.add(new MapPatchInput(path, value == null ? null : json(value)));
            written.add(path);
        }
        if (!accepted.isEmpty()) resourceService.patchMap(map.getId(), accepted);
        return new WriteResult(written, refused);
    }

    /** Why a path is not one the coach may write, or null when it is. */
    private static String pathProblem(String path) {
        if (!path.startsWith("/") || path.length() < 2) return "a path starts with '/', e.g. /skills/0/checked";
        String part = path.substring(1).split("/", 2)[0];
        if (!PARTS.contains(part)) {
            return "not a part of the map (facts, skills, qualities, requirements, additional, company)";
        }
        return null;
    }

    /**
     * Does this value hold something she put there? Ids and timestamps are bookkeeping, not her
     * work, so an item with nothing but an id and an empty text holds nothing.
     */
    static boolean holdsHerWork(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) return false;
        if (node.isTextual()) return !node.asText().isBlank();
        if (node.isBoolean()) return node.asBoolean();
        if (node.isNumber()) return true;
        if (node.isArray()) {
            for (JsonNode child : node) if (holdsHerWork(child)) return true;
            return false;
        }
        if (node.isObject()) {
            var fields = node.fields();
            while (fields.hasNext()) {
                var entry = fields.next();
                if ("id".equals(entry.getKey()) || "at".equals(entry.getKey())) continue;
                if (holdsHerWork(entry.getValue())) return true;
            }
        }
        return false;
    }

    /** What an item at this path IS, so an appended one gets the shape the page expects. */
    static String kindOf(String path) {
        String[] t = path.substring(1).split("/");
        String part = t[0];
        if (t.length == 2) {
            return switch (part) {
                case "skills", "qualities" -> "check";
                case "requirements" -> "requirement";
                case "additional" -> "additional";
                default -> "";
            };
        }
        if (t.length == 4 && ("skills".equals(part) || "qualities".equals(part)) && "comments".equals(t[2])) {
            return "comment";
        }
        if (t.length == 4 && "requirements".equals(part) && "companies".equals(t[2])) return "company";
        if (t.length == 3 && "company".equals(part) && "comments".equals(t[1])) return "note";
        return "";
    }

    private static String preview(JsonNode node) {
        String text = node.isTextual() ? node.asText() : node.toString();
        text = text.strip();
        return "\"" + (text.length() <= 160 ? text : text.substring(0, 159) + "…") + "\"";
    }

    private static String json(JsonNode node) {
        try {
            return MAPPER.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("that value could not be written as JSON");
        }
    }

    private static String clip(String value, int max) {
        String v = value.strip();
        return v.length() <= max ? v : v.substring(0, max - 1) + "…";
    }
}
