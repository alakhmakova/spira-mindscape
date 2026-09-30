package com.spiramindscape.backend.ai.cv;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spiramindscape.backend.ai.chat.ResourceReadService;
import com.spiramindscape.backend.ai.chat.UrlReadService;
import com.spiramindscape.backend.ai.prompt.PromptResources;
import com.spiramindscape.backend.ai.provider.LlmMessage;
import com.spiramindscape.backend.ai.provider.LlmProvider;
import com.spiramindscape.backend.ai.provider.ProviderType;
import com.spiramindscape.backend.graphql.input.CreateResourceInput;
import com.spiramindscape.backend.graphql.input.UpdateResourceInput;
import com.spiramindscape.backend.resource.Resource;
import com.spiramindscape.backend.resource.ResourceRepository;
import com.spiramindscape.backend.resource.ResourceService;
import com.spiramindscape.backend.resource.ResourceView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Step 1: the server reads the advert and builds the requirement map.
 *
 * <p><b>Why the server does it.</b> Extraction used to happen only if the model chose to call a
 * tool, and it did not: the owner's session reached the interview with zero requirements while the
 * writer claimed it had extracted them (2026-09-15). She then wrote the process out by hand
 * (2026-09-16): read the advert sentence by sentence, throw away the connective words and the
 * decoration, and keep six things — the title, the location, the competences as short tags, the
 * core message the whole advert is really about, the personal qualities, and the employer's fuller
 * requirements — plus the extra notes that belong to the letter or must be raised at once. Repeats
 * are merged wherever they sit in the text.
 *
 * <p>So the stage is code and the model is used only for the reading, as JSON the server validates:
 * a quote that is not in the advert is dropped, a requirement or trait without a quote is dropped,
 * and items quoting the same line are one item. The map is then rendered as a note she can open.
 *
 * <p>The helpers for reading a repository stay here but are not used by this stage: the writer reads
 * the user's own sources when she points at them, which is at the CV-writing step — not before the
 * questions, because the point of the questions is her own account of the work.
 *
 * <p>Nothing here logs content: the advert, her notes and her code are all user text.
 */
@Service
public class CvAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(CvAnalysisService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    static final int MAX_ITEMS_PER_CATEGORY = 25;
    static final int MAX_QUOTES = 8;
    static final int EXCERPT_CHARS = 8_000;
    static final int MAX_REPOS = 3;
    static final int MAX_BUILD_FILES = 6;

    public enum Outcome { DONE, CONTINUE, FAILED, BUSY, KEY_REJECTED }

    private final CvApplicationService apps;
    private final CvMapService maps;
    private final UrlReadService urls;
    private final ResourceReadService resourceReads;
    private final ResourceRepository resources;
    private final PromptResources prompts;

    public CvAnalysisService(CvApplicationService apps, CvMapService maps, UrlReadService urls,
                             ResourceReadService resourceReads, ResourceRepository resources,
                             PromptResources prompts) {
        this.apps = apps;
        this.maps = maps;
        this.urls = urls;
        this.resourceReads = resourceReads;
        this.resources = resources;
        this.prompts = prompts;
    }

    /** A stage that could not produce a usable result. {@code reason} is a fixed code. */
    static final class AnalysisFailure extends RuntimeException {
        final String stage;
        final String reason;

        AnalysisFailure(String stage, String reason, Throwable cause) {
            super(stage + ":" + reason, cause);
            this.stage = stage;
            this.reason = reason;
        }
    }

    /**
     * Runs (or resumes) the analysis of one application, ending with its vacancy map built and the
     * work moved on to the map.
     *
     * <p>An advert that states no requirements is no longer a dead end with its own tool: it gets a
     * map all the same — the facts the advert did give, and empty parts — and the coach fills the
     * requirements in with her from comparable postings or the role she names, on the map like
     * everything else. {@code analysis_state = "empty"} is what tells the step messages to say so.
     *
     * @param status        progress lines for the user ("Reading the advert sentence by sentence…")
     * @param cancelled     true once the stream has ended — stop spending the user's tokens
     * @param deadlineNanos {@link System#nanoTime()} past which no model call starts
     */
    public Outcome run(Long appId, Long goalId, LlmProvider provider, Consumer<String> status,
                       BooleanSupplier cancelled, long deadlineNanos) {
        try {
            CvApplication app = apps.get(appId);
            String lang = CvTransitions.lang(app.getConversationLanguage());
            String state = app.getAnalysisState() == null ? "none" : app.getAnalysisState();

            if (!state.equals("done") && !state.equals("empty")) {
                if (System.nanoTime() > deadlineNanos) return Outcome.CONTINUE;
                status.accept(CvTransitions.analysisStatus("extract", lang));
                Extraction ex = extract(provider, new Pacer(provider.providerType()), app, lang);
                if (cancelled.getAsBoolean()) return Outcome.CONTINUE;
                status.accept(CvTransitions.analysisStatus("map", lang));
                apps.recordAnalysis(appId, ex.meta(), ex.items().isEmpty() ? "empty" : "done",
                        conditions(ex.items()));
                maps.createFromAnalysis(appId, goalId, ex.meta(), ex.items());
            }
            // An application analysed before the map existed reaches here with the analysis done and
            // no map; so does one whose advert stated nothing. Either way it gets one now.
            apps.finishAnalysis(appId);
            maps.ensure(appId, goalId);
            return Outcome.DONE;
        } catch (AnalysisFailure e) {
            log.warn("cv_analysis_failed applicationId={} stage={} reason={}", appId, e.stage, e.reason, e.getCause());
            safeState(appId, "failed");
            // A provider outage is not the model answering badly, and the user is told which.
            // **A refused key is neither**, and telling her to wait a minute for one is worse than
            // saying nothing: waiting cannot fix it (owner, 2026-09-22 — her key had expired and
            // the writer reported an overloaded service).
            if (!"provider_error".equals(e.reason)) return Outcome.FAILED;
            return keyRejected(e.getCause()) ? Outcome.KEY_REJECTED : Outcome.BUSY;
        } catch (RuntimeException e) {
            log.warn("cv_analysis_error applicationId={}", appId, e);
            return Outcome.FAILED;
        }
    }

    /**
     * Whether the provider refused the KEY rather than the request: an expired, revoked or wrong
     * key, or a plan that does not allow the model. Read off the provider's own status line, the
     * way the ordinary chat's {@code friendlyError} reads it — there is no typed error to match
     * on, because every provider words it differently.
     */
    private static boolean keyRejected(Throwable cause) {
        for (Throwable t = cause; t != null; t = t.getCause()) {
            String m = t.getMessage() == null ? "" : t.getMessage().toLowerCase();
            if (m.contains("401") || m.contains("403") || m.contains("unauthorized")
                    || m.contains("unauthenticated") || m.contains("invalid api key")
                    || m.contains("invalid_api_key") || m.contains("api key expired")
                    || m.contains("expired") || m.contains("permission denied")) {
                return true;
            }
        }
        return false;
    }

    private void safeState(Long appId, String state) {
        try {
            apps.setAnalysisState(appId, state);
        } catch (RuntimeException ignored) {
            // The next request re-reads the state; a failed write only means it starts again.
        }
    }

    // ── Reading the advert ───────────────────────────────────────────────────

    record Extraction(CvApplicationService.AnalysisMeta meta, List<CvApplicationService.Item> items) {
    }

    Extraction extract(LlmProvider provider, Pacer pacer, CvApplication app, String lang) {
        String advert = app.getVacancyText() == null ? "" : app.getVacancyText();
        String user = "The user's language: " + languageName(lang) + ".\n\nTHE ADVERT\n" + fence(advert);
        JsonNode json = structured(provider, pacer, prompts.cvAnalysisExtract(), user, "requirements", "extract");
        AdvertIndex index = new AdvertIndex(advert);

        List<CvApplicationService.Item> items = new ArrayList<>();
        Set<String> keys = new LinkedHashSet<>();
        Set<String> seenQuotes = new LinkedHashSet<>();
        // The core message first: it leads the summary and the letter, and it is one item.
        String core = text(json, "core_message");
        if (core != null) {
            items.add(new CvApplicationService.Item("core", uniqueKey("core", keys), clip(core, 120),
                    core, null, CvRequirement.Weight.MUST, null, quotesOf(json.path("core_quotes"), index, seenQuotes)));
        }
        items.addAll(parse(json.path("competencies"), "competence", index, keys, seenQuotes, "tag"));
        items.addAll(parse(json.path("requirements"), "requirement", index, keys, seenQuotes, "demand"));
        items.addAll(parse(json.path("traits"), "trait", index, keys, seenQuotes, "trait"));
        items.addAll(parse(json.path("extra"), "extra", index, keys, new LinkedHashSet<>(), "note"));

        CvApplicationService.AnalysisMeta meta = new CvApplicationService.AnalysisMeta(
                text(json, "role") != null ? text(json, "role") : text(json, "title"),
                strings(json.path("role_titles")), text(json, "company"), text(json, "location"),
                text(json, "language"), text(json, "letter"), text(json, "contact_name"), core,
                text(json, "education"), text(json, "years_of_experience"),
                // NOT `language`, which is the language the advert is written in.
                text(json, "languages_required"),
                text(json, "apply_url"), text(json, "company_url"));
        // Competences and requirements are what the collection is built on; an advert with neither
        // is the "no requirements" route.
        boolean anyDemand = items.stream().anyMatch(i -> i.category().equals("competence")
                || i.category().equals("requirement") || i.category().equals("core"));
        return new Extraction(meta, anyDemand ? items : List.of());
    }

    /**
     * One category of the model's answer, keeping only the items the advert really says.
     *
     * <p>A quote must be in the advert (word for word, or close enough that the advert's own line
     * can be found); a requirement or a trait with no quote left is dropped; and two items quoting
     * the same line are one item — that repetition is the employer's emphasis, not a duplicate.
     */
    static List<CvApplicationService.Item> parse(JsonNode array, String category, AdvertIndex index,
                                                 Set<String> keys, Set<String> seenQuotes, String labelField) {
        List<CvApplicationService.Item> out = new ArrayList<>();
        if (array == null || !array.isArray()) return out;
        for (JsonNode node : array) {
            if (out.size() >= MAX_ITEMS_PER_CATEGORY) break;
            String label = node.path(labelField).asText("").strip();
            if (label.isEmpty()) label = node.path("label").asText("").strip();
            if (label.isEmpty()) continue;
            List<CvApplicationService.Quote> quotes = quotesOf(node.path("quotes"), index, seenQuotes);
            // A competence tag or an extra note may be the analyst's own wording; a requirement and
            // a trait must stand on a line of the advert.
            if (quotes.isEmpty() && (category.equals("requirement") || category.equals("trait"))) continue;
            String context = node.path("context").asText("").strip();
            out.add(new CvApplicationService.Item(
                    category,
                    uniqueKey(slug(label), keys),
                    clip(label, 120),
                    category.equals("requirement") ? label : null,
                    context.isEmpty() ? (quotes.isEmpty() ? null : quotes.get(0).text()) : context,
                    CvRequirement.Weight.from(node.path("weight").asText(null)),
                    category.equals("extra")
                            ? ("flag".equalsIgnoreCase(node.path("use").asText("letter")) ? "flag" : "letter")
                            : null,
                    quotes));
        }
        return out;
    }

    /** The quotes of one item, dropped when the advert does not say them or another item has them. */
    private static List<CvApplicationService.Quote> quotesOf(JsonNode array, AdvertIndex index, Set<String> seen) {
        List<CvApplicationService.Quote> out = new ArrayList<>();
        if (array == null || !array.isArray()) return out;
        for (JsonNode q : array) {
            if (out.size() >= MAX_QUOTES) break;
            CvApplicationService.Quote located = index.locate(q.asText(""));
            if (located == null) continue;
            if (!seen.add(norm(located.text()))) continue;
            out.add(located);
        }
        return out;
    }

    /** The advert, line by line, for checking that a quote is really in it. */
    static final class AdvertIndex {
        final List<String> lines = new ArrayList<>();
        final List<String> normalised = new ArrayList<>();

        AdvertIndex(String advert) {
            for (String line : (advert == null ? "" : advert).split("\\R")) {
                String s = line.strip();
                if (s.isEmpty()) continue;
                lines.add(s);
                normalised.add(norm(s));
            }
        }

        /** The quote as a line of the advert, or null when the advert does not say it. */
        CvApplicationService.Quote locate(String quote) {
            String q = norm(quote);
            if (q.length() < 3) return null;
            for (int i = 0; i < normalised.size(); i++) {
                if (normalised.get(i).contains(q)) {
                    return new CvApplicationService.Quote(clip(quote.strip(), 1000), i + 1);
                }
            }
            int best = -1;
            double bestScore = 0;
            for (int i = 0; i < normalised.size(); i++) {
                double score = overlap(q, normalised.get(i));
                if (score > bestScore) {
                    bestScore = score;
                    best = i;
                }
            }
            return bestScore >= 0.8 ? new CvApplicationService.Quote(clip(lines.get(best), 1000), best + 1) : null;
        }

        /**
         * Lines that read as requirements: the items under a line ending in a colon ("Vi söker dig
         * som har:"), and bulleted lines. Used to notice what the extraction left out.
         */
        List<String> requirementLines() {
            List<String> out = new ArrayList<>();
            boolean inList = false;
            for (String line : lines) {
                if (line.endsWith(":")) {
                    inList = true;
                    continue;
                }
                if (line.length() > 160) {
                    inList = false;
                    continue;
                }
                boolean bullet = line.matches("^[•\\-–*·▪◦].*");
                if ((inList || bullet) && line.length() >= 3) out.add(line);
            }
            return out;
        }
    }

    /** The advert's hard conditions: the extra notes the reading marked to be raised at once. */
    static List<String> conditions(List<CvApplicationService.Item> items) {
        return items.stream()
                .filter(i -> "extra".equals(i.category()) && "flag".equals(i.extraUse()))
                .map(CvApplicationService.Item::label)
                .filter(l -> l != null && !l.isBlank())
                .toList();
    }

    // ── The user's own sources, read when she points at them ────────────────

    record RepoRef(String owner, String name) {
        String key() {
            return owner + "/" + name;
        }
    }

    private static final Pattern GITHUB = Pattern.compile("github\\.com/([A-Za-z0-9-]+)/([A-Za-z0-9._-]+)");
    private static final Set<String> NOT_OWNERS =
            Set.of("orgs", "settings", "features", "topics", "marketplace", "about");
    /** A link to an ACCOUNT rather than a repository: "github.com/alakhmakova". */
    private static final Pattern GITHUB_ACCOUNT =
            Pattern.compile("github\\.com/([A-Za-z0-9-]+)/?(?=$|[\\s\"'<)#?,])");

    /**
     * Reads what the user has pointed at — her repositories and the resources she named — into the
     * ledger, so the CV is written from the sources rather than from a summary of them. Called at
     * the writing step: before the questions it would be answering for her.
     *
     * @return how many files were read
     */
    public int readSourcesForCv(Long appId, Long goalId, int maxFiles) {
        CvApplication app = apps.get(appId);
        List<ResourceView> views = resources.findViewsByGoalId(goalId);
        StringBuilder text = new StringBuilder();
        // Everything she wrote on her vacancy map — where "look at my repo" is most likely to be.
        text.append(VacancyMapDocument.evidenceText(apps.mapDocument(app))).append('\n');
        for (Long noteId : new Long[] {app.getProfileNoteId(), app.getStoriesNoteId()}) {
            bodyOf(views, noteId).ifPresent(body -> text.append(body).append('\n'));
        }
        LinkedHashSet<RepoRef> repos = new LinkedHashSet<>();
        findRepos(text.toString(), repos);
        for (ResourceView v : views) {
            if ("link".equals(v.type())) findRepos(v.url(), repos);
        }
        if (repos.size() < MAX_REPOS) {
            for (String account : accounts(text.toString())) {
                if (repos.size() >= MAX_REPOS) break;
                UrlReadService.Fetched list = urls.fetchRaw("https://api.github.com/users/" + account
                        + "/repos?per_page=100&sort=pushed", "application/vnd.github+json", 2_000_000);
                if (list.ok()) repos.addAll(pickRepos(account, list.text(), text.toString(), MAX_REPOS - repos.size()));
            }
        }
        int read = 0;
        for (RepoRef repo : repos) {
            List<String> paths = treeOf(app, repo);
            for (String path : paths.stream().filter(CvAnalysisService::isBuildFile).limit(MAX_BUILD_FILES).toList()) {
                if (read >= maxFiles) break;
                if (readRepoFile(app, repo, path) != null) read++;
            }
        }
        return read;
    }

    private Optional<String> bodyOf(List<ResourceView> views, Long id) {
        if (id == null) return Optional.empty();
        return views.stream().filter(v -> id.equals(v.id()) && "note".equals(v.type()))
                .map(ResourceView::body).filter(b -> b != null && !b.isBlank()).findFirst();
    }

    private List<String> treeOf(CvApplication app, RepoRef repo) {
        String treeKey = "repo-tree:" + repo.key();
        Optional<CvSourceRead> cached = apps.sourceRead(app.getId(), treeKey).filter(CvSourceRead::isOk);
        if (cached.isPresent()) {
            return Arrays.stream(cached.get().getContent().split("\n")).filter(p -> !p.isBlank()).toList();
        }
        UrlReadService.Fetched tree = urls.fetchRaw("https://api.github.com/repos/" + repo.key()
                + "/git/trees/HEAD?recursive=1", "application/vnd.github+json", 6_000_000);
        List<String> paths = tree.ok() ? treePaths(tree.text()) : List.of();
        if (paths.isEmpty()) {
            apps.recordSourceRead(app.getId(), treeKey,
                    "Repository " + repo.key() + " (could not be read)", null, false, "cv");
            return List.of();
        }
        apps.recordSourceRead(app.getId(), treeKey, "Repository " + repo.key(),
                clip(String.join("\n", paths), 150_000), true, "cv");
        return paths;
    }

    private String readRepoFile(CvApplication app, RepoRef repo, String path) {
        String key = "repo:" + repo.key() + ":" + path;
        Optional<CvSourceRead> cached = apps.sourceRead(app.getId(), key);
        if (cached.isPresent()) return cached.get().isOk() ? cached.get().getContent() : null;
        String url = "https://raw.githubusercontent.com/" + repo.key() + "/HEAD/" + encodePath(path);
        UrlReadService.Fetched f = urls.fetchRaw(url, "text/plain,*/*;q=0.5", 400_000);
        String content = f.ok() ? clip(f.text(), EXCERPT_CHARS) : null;
        apps.recordSourceRead(app.getId(), key, path, content, f.ok(), "cv");
        return content;
    }

    /** One resource the writer asked to read, kept so the gates and the CV can use it. */
    public String readResourceForCv(Long appId, Long goalId, Long resourceId) {
        String text = resourceReads.read(goalId, resourceId);
        boolean ok = text != null && !text.equals("Resource not found.");
        String label = resources.findById(resourceId).map(Resource::getTitle).orElse("resource:" + resourceId);
        apps.recordSourceRead(appId, "resource:" + resourceId, label,
                ok ? clip(text, EXCERPT_CHARS) : null, ok, "cv");
        return ok ? text : null;
    }

    static void findRepos(String text, Set<RepoRef> into) {
        if (text == null) return;
        Matcher m = GITHUB.matcher(text);
        while (m.find()) {
            String owner = m.group(1);
            String name = m.group(2).replaceAll("\\.git$", "").replaceAll("[.]+$", "");
            if (NOT_OWNERS.contains(owner.toLowerCase(Locale.ROOT)) || name.isBlank()) continue;
            into.add(new RepoRef(owner, name));
        }
    }

    /** GitHub accounts linked in the text, without the ones that are really repository links. */
    static List<String> accounts(String text) {
        Set<String> out = new LinkedHashSet<>();
        if (text == null) return List.of();
        Matcher m = GITHUB_ACCOUNT.matcher(text);
        while (m.find()) {
            String owner = m.group(1);
            if (!NOT_OWNERS.contains(owner.toLowerCase(Locale.ROOT))) out.add(owner);
        }
        return new ArrayList<>(out);
    }

    /**
     * Which of an account's repositories to read: the ones the user names (newest push first), or —
     * when she names none — the two most recently pushed that are not forks.
     */
    static List<RepoRef> pickRepos(String account, String json, String text, int max) {
        List<RepoRef> named = new ArrayList<>();
        List<RepoRef> recent = new ArrayList<>();
        String haystack = (text == null ? "" : text).toLowerCase(Locale.ROOT);
        try {
            for (JsonNode r : MAPPER.readTree(json)) {
                if (r.path("fork").asBoolean(false)) continue;
                String name = r.path("name").asText("");
                if (name.isBlank()) continue;
                RepoRef ref = new RepoRef(r.path("owner").path("login").asText(account), name);
                String lower = name.toLowerCase(Locale.ROOT);
                if (haystack.contains(lower) || haystack.contains(lower.replaceAll("[-_.]+", " "))) {
                    named.add(ref);
                } else {
                    recent.add(ref);
                }
            }
        } catch (Exception e) {
            return List.of();
        }
        List<RepoRef> out = named.isEmpty() ? recent.subList(0, Math.min(2, recent.size())) : named;
        return out.subList(0, Math.min(max, out.size()));
    }

    static List<String> treePaths(String json) {
        try {
            List<String> out = new ArrayList<>();
            for (JsonNode n : MAPPER.readTree(json).path("tree")) {
                if ("blob".equals(n.path("type").asText())) out.add(n.path("path").asText());
            }
            return out;
        } catch (Exception e) {
            return List.of();
        }
    }

    static boolean isBuildFile(String path) {
        if (path.contains("node_modules/")) return false;
        String name = path.substring(path.lastIndexOf('/') + 1);
        return depth(path) <= 3 && (name.equals("pom.xml") || name.equals("package.json")
                || name.equals("build.gradle.kts") || name.equals("build.gradle"));
    }

    private static int depth(String path) {
        return (int) path.chars().filter(c -> c == '/').count();
    }

    private static final Pattern POM_ARTIFACT = Pattern.compile("<artifactId>\\s*([^<\\s]+)\\s*</artifactId>");
    private static final Pattern GRADLE_DEP = Pattern.compile(
            "(?:implementation|api|testImplementation|androidTestImplementation|debugImplementation|kapt|ksp)\\s*\\(\\s*\"([^\"]+)\"");
    private static final Pattern GRADLE_ALIAS = Pattern.compile(
            "(?:implementation|api|testImplementation|androidTestImplementation)\\s*\\(\\s*libs\\.([\\w.]+)");

    /** Dependency names from a build file — the facts a technology claim can be checked against. */
    static List<String> dependenciesOf(String path, String content) {
        Set<String> out = new LinkedHashSet<>();
        if (path.endsWith("pom.xml")) {
            Matcher m = POM_ARTIFACT.matcher(content);
            while (m.find()) out.add(m.group(1));
        } else if (path.endsWith("package.json")) {
            try {
                JsonNode json = MAPPER.readTree(content);
                for (String field : List.of("dependencies", "devDependencies")) {
                    json.path(field).fieldNames().forEachRemaining(out::add);
                }
            } catch (Exception ignored) {
                // A package.json that does not parse contributes nothing.
            }
        } else {
            Matcher m = GRADLE_DEP.matcher(content);
            while (m.find()) {
                String[] parts = m.group(1).split(":");
                out.add(parts.length >= 2 ? parts[1] : parts[0]);
            }
            Matcher a = GRADLE_ALIAS.matcher(content);
            while (a.find()) out.add(a.group(1).replace('.', '-'));
        }
        return new ArrayList<>(out);
    }

    static String repoFacts(RepoRef repo, List<String> paths, List<String> buildFiles, Set<String> dependencies) {
        long tests = paths.stream().filter(p -> p.matches("(?i).*(^|/)(src/test|src/androidtest|tests?|e2e|__tests__)/.*")
                || p.matches("(?i).*\\.(test|spec)\\.[jt]sx?$") || p.matches(".*Tests?\\.(java|kt)$")).count();
        List<String> ci = paths.stream().filter(p -> p.startsWith(".github/workflows/")
                || p.equals(".gitlab-ci.yml") || p.equals("Jenkinsfile") || p.equals("azure-pipelines.yml")).toList();
        StringBuilder sb = new StringBuilder("Repository ").append(repo.key())
                .append(" — https://github.com/").append(repo.key()).append('\n')
                .append("Files: ").append(paths.size()).append('\n')
                .append("Build files: ").append(buildFiles.isEmpty() ? "none" : String.join(", ", buildFiles)).append('\n')
                .append("Test files: ").append(tests).append('\n')
                .append("CI: ").append(ci.isEmpty() ? "none found" : String.join(", ", ci)).append('\n');
        for (String folder : List.of("docs", "specs", "backlog")) {
            long n = paths.stream().filter(p -> p.startsWith(folder + "/")).count();
            if (n > 0) sb.append(folder).append("/: ").append(n).append(" files\n");
        }
        List<String> deps = dependencies.stream().limit(150).toList();
        sb.append("Dependencies (from the build files): ").append(deps.isEmpty() ? "none read" : String.join(", ", deps));
        return sb.toString();
    }

    private static String encodePath(String path) {
        return Arrays.stream(path.split("/"))
                .map(seg -> URLEncoder.encode(seg, StandardCharsets.UTF_8).replace("+", "%20"))
                .collect(Collectors.joining("/"));
    }

    // ── Model calls ──────────────────────────────────────────────────────────

    /** Keeps Mistral under its one-request-per-second limit across the stages. */
    static final class Pacer {
        private final ProviderType type;
        private long last;

        Pacer(ProviderType type) {
            this.type = type;
        }

        void pace() {
            if (type == ProviderType.MISTRAL && last != 0) {
                long wait = 1_100 - (System.nanoTime() - last) / 1_000_000;
                if (wait > 0) {
                    try {
                        Thread.sleep(wait);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
            last = System.nanoTime();
        }
    }

    /**
     * One model call that must answer with a JSON object carrying {@code field}. One retry with the
     * reason; then the stage fails and the caller says so rather than pretending.
     */
    JsonNode structured(LlmProvider provider, Pacer pacer, String system, String user, String field, String stage) {
        String prompt = user;
        for (int attempt = 0; attempt < 2; attempt++) {
            pacer.pace();
            StringBuilder out = new StringBuilder();
            AtomicReference<Throwable> error = new AtomicReference<>();
            provider.streamChat(List.of(LlmMessage.user(prompt)), system, List.of(),
                    out::append, call -> { }, () -> { }, error::set);
            if (error.get() != null) throw new AnalysisFailure(stage, "provider_error", error.get());
            JsonNode node = parseJson(out.toString());
            if (node != null && node.path(field).isArray()) return node;
            prompt = user + "\n\nYour previous reply could not be used. Reply with ONE JSON object that has a \""
                    + field + "\" array, and nothing else.";
        }
        throw new AnalysisFailure(stage, "invalid_json", null);
    }

    static JsonNode parseJson(String raw) {
        if (raw == null) return null;
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start < 0 || end <= start) return null;
        try {
            return MAPPER.readTree(raw.substring(start, end + 1));
        } catch (Exception e) {
            return null;
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    static String norm(String s) {
        if (s == null) return "";
        return s.toLowerCase(Locale.ROOT)
                .replace(' ', ' ')
                .replaceAll("[“”\"'’‘«»]", "")
                .replaceAll("^[\\s•\\-–*·▪◦]+", "")
                .replaceAll("\\s+", " ")
                .replaceAll("[\\s.;:,!]+$", "")
                .strip();
    }

    /** Share of the shorter text's words (3+ letters) found in the longer one. */
    static double overlap(String a, String b) {
        Set<String> wa = words(a).stream().filter(w -> w.length() >= 3).collect(Collectors.toSet());
        Set<String> wb = words(b).stream().filter(w -> w.length() >= 3).collect(Collectors.toSet());
        if (wa.size() < 3 || wb.isEmpty()) return 0;
        long hits = wa.stream().filter(wb::contains).count();
        return (double) hits / wa.size();
    }

    /** Six words in a row from the advert: the line is the posting's prose, not the user's. */
    static boolean copiesAdvert(String line, String advert) {
        List<String> a = words(advert);
        Set<String> grams = new java.util.HashSet<>();
        for (int i = 0; i + 6 <= a.size(); i++) grams.add(String.join(" ", a.subList(i, i + 6)));
        List<String> w = words(line);
        for (int i = 0; i + 6 <= w.size(); i++) {
            if (grams.contains(String.join(" ", w.subList(i, i + 6)))) return true;
        }
        return false;
    }

    private static List<String> words(String s) {
        List<String> out = new ArrayList<>();
        if (s == null) return out;
        Matcher m = Pattern.compile("[\\p{L}\\p{N}]+").matcher(s.toLowerCase(Locale.ROOT));
        while (m.find()) out.add(m.group());
        return out;
    }

    private static String slug(String raw) {
        String s = (raw == null ? "" : raw).toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        if (s.isEmpty()) s = "item";
        return s.length() > 28 ? s.substring(0, 28) : s;
    }

    private static String uniqueKey(String base, Set<String> keys) {
        String key = base;
        int n = 2;
        while (keys.contains(key)) key = base + "-" + n++;
        keys.add(key);
        return key;
    }

    private static String fence(String content) {
        String safe = content == null ? "" : content
                .replace("<<UNTRUSTED_CONTENT>>", "<UNTRUSTED_CONTENT>")
                .replace("<<END_UNTRUSTED_CONTENT>>", "<END_UNTRUSTED_CONTENT>");
        return "<<UNTRUSTED_CONTENT>>\n" + safe + "\n<<END_UNTRUSTED_CONTENT>>";
    }

    static String clip(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, Math.max(0, max - 1)) + "…" : s;
    }

    private static String text(JsonNode json, String field) {
        JsonNode n = json.path(field);
        return n.isTextual() && !n.asText().isBlank() && !"null".equals(n.asText()) ? n.asText().strip() : null;
    }

    private static List<String> strings(JsonNode array) {
        List<String> out = new ArrayList<>();
        if (array != null && array.isArray()) {
            for (JsonNode n : array) {
                String v = n.asText("").strip();
                if (!v.isEmpty()) out.add(v);
            }
        }
        return out;
    }

    private static String languageName(String lang) {
        return switch (lang) {
            case "ru" -> "Russian";
            case "sv" -> "Swedish";
            default -> "English";
        };
    }
}
