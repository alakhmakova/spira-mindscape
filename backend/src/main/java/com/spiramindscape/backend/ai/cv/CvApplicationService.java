package com.spiramindscape.backend.ai.cv;

import com.fasterxml.jackson.databind.JsonNode;
import com.spiramindscape.backend.ai.chat.UrlReadService;
import com.spiramindscape.backend.ai.cv.dto.CvApplicationDto;
import com.spiramindscape.backend.auth.CurrentUserProvider;
import com.spiramindscape.backend.goal.GoalRepository;
import com.spiramindscape.backend.resource.Resource;
import com.spiramindscape.backend.resource.ResourceRepository;
import com.spiramindscape.backend.resource.ResourceView;
import com.spiramindscape.backend.resource.VacancyMapPatch;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Owns the state of a CV application: its phase, the documents it produced, and the pointer to its
 * vacancy map.
 *
 * <p>The map itself is a resource (specs/2026-09-17-vacancy-map/) — the user's own page, edited by
 * her and by the coach a field at a time. This class reads it (for the progress the clients show,
 * the facts the step messages mention, and the corpus the fact gates check against); creating and
 * writing it belongs to {@link CvMapService}.
 *
 * <p>It used to own a requirement map of its own, as {@code cv_requirement} rows handed to the
 * model one item per turn. That conveyor is retired (2026-09-18): the order is now hers, and the
 * rows are read only once, to convert an old application into a map.
 *
 * <p>Every entry point is scoped to the authenticated user and re-checks that the goal is theirs: a
 * job application holds a person's employment history, and an id from the client is not a
 * permission.
 */
@Service
@Transactional
public class CvApplicationService {

    private final CvApplicationRepository applications;
    private final CvRequirementRepository requirements;
    private final GoalRepository goalRepository;
    private final CurrentUserProvider currentUserProvider;
    private final UrlReadService urlReadService;
    private final ResourceRepository resources;
    private final CvSourceReadRepository sourceReads;

    public CvApplicationService(
            CvApplicationRepository applications,
            CvRequirementRepository requirements,
            GoalRepository goalRepository,
            CurrentUserProvider currentUserProvider,
            UrlReadService urlReadService,
            ResourceRepository resources,
            CvSourceReadRepository sourceReads) {
        this.applications = applications;
        this.requirements = requirements;
        this.goalRepository = goalRepository;
        this.currentUserProvider = currentUserProvider;
        this.urlReadService = urlReadService;
        this.resources = resources;
        this.sourceReads = sourceReads;
    }

    // ── Applications ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<CvApplication> list(Long goalId) {
        Long userId = ownedGoal(goalId);
        return applications.findByAppUserIdAndGoalIdOrderByUpdatedAtDesc(userId, goalId);
    }

    /**
     * Start a new application from <b>a link or the advert's text</b>, whichever the user gave.
     *
     * <p>With only a link, the page is fetched here and now. That is deliberate: reading a job
     * board fails often, and the moment to find out is while the user is still looking at the start
     * card and can paste the advert instead.
     */
    public CvApplication create(Long goalId, String title, String vacancyUrl,
                                String vacancyText, String vacancyLanguage) {
        Long userId = ownedGoal(goalId);
        String url = vacancyUrl == null || vacancyUrl.isBlank() ? null : vacancyUrl.strip();
        String text = vacancyText == null || vacancyText.isBlank() ? null : vacancyText;

        if (text == null) {
            if (url == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "An application needs either a link to the vacancy or its text");
            }
            UrlReadService.Fetched fetched = urlReadService.fetch(url);
            if (!fetched.ok()) {
                // The reason comes from the fetch itself ("HTTP 403", "rendered by JavaScript") so
                // the user is told what to do about THIS page rather than handed a generic failure.
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "That link could not be read — paste the advert instead. (" + fetched.text() + ")");
            }
            text = fetched.text();
        }

        CvApplication app = new CvApplication();
        app.setAppUserId(userId);
        app.setGoalId(goalId);
        app.setTitle(applicationTitle(title, text, url));
        app.setVacancyUrl(url);
        app.setVacancyText(text);
        app.setVacancyLanguage(vacancyLanguage);
        // The advert is read first: the work starts in the analysis, not on the user's profile.
        app.setPhase(CvPhase.ANALYSIS);
        // **Her details belong to the person, not to one vacancy.** Every application used to
        // start with nothing, so the writer built another profile each time.
        applications.findByAppUserIdAndGoalIdOrderByUpdatedAtDesc(userId, goalId).stream()
                .filter(prev -> prev.getProfileNoteId() != null)
                .findFirst()
                .ifPresent(prev -> {
                    List<Long> alive = resources.findExistingIds(goalId,
                            Stream.of(prev.getProfileNoteId(), prev.getStoriesNoteId())
                                    .filter(java.util.Objects::nonNull).toList());
                    if (alive.contains(prev.getProfileNoteId())) app.setProfileNoteId(prev.getProfileNoteId());
                    if (prev.getStoriesNoteId() != null && alive.contains(prev.getStoriesNoteId())) {
                        app.setStoriesNoteId(prev.getStoriesNoteId());
                    }
                });
        return applications.save(app);
    }

    /**
     * The title an advert would produce, without creating anything — so a duplicate can be spotted
     * before a row exists, where there is no URL to compare.
     */
    public static String titleFor(String given, String text, String url) {
        return applicationTitle(given, text == null ? "" : text, url);
    }

    /**
     * What to call this application in the list of them. The user is not asked: a job advert opens
     * with the role and the employer, so it names itself.
     */
    private static String applicationTitle(String given, String text, String url) {
        if (given != null && !given.isBlank()) return clip(given, 300);
        for (String line : text.split("\\R")) {
            String candidate = line.strip();
            // Long enough to be a heading rather than a stray word, short enough not to be the
            // advert's first paragraph.
            if (candidate.length() >= 3 && candidate.length() <= 120) return clip(candidate, 300);
        }
        if (url != null) {
            try {
                return clip(java.net.URI.create(url).getHost(), 300);
            } catch (RuntimeException ignored) {
                // A URL we could not parse is no worse than none for naming purposes.
            }
        }
        return "Untitled application";
    }

    /**
     * An application on this goal already covering the same advert, if there is one. Matched on the
     * URL when there is one, and on the derived title otherwise. Neither is a database constraint:
     * the same address can carry a different job a year later, so the caller asks the user.
     */
    @Transactional(readOnly = true)
    public Optional<CvApplication> findDuplicate(Long goalId, String vacancyUrl, String title) {
        Long userId = ownedGoal(goalId);
        String url = vacancyUrl == null || vacancyUrl.isBlank() ? null : vacancyUrl.strip();
        return applications.findByAppUserIdAndGoalIdOrderByUpdatedAtDesc(userId, goalId).stream()
                .filter(a -> url != null
                        ? url.equalsIgnoreCase(a.getVacancyUrl())
                        : title != null && title.equalsIgnoreCase(a.getTitle()))
                .findFirst();
    }

    /** An application of the current user's, or 404. */
    @Transactional(readOnly = true)
    public CvApplication get(Long applicationId) {
        return owned(applicationId);
    }

    /**
     * Discard an application. Its vacancy map, like the notes it produced, is a goal resource and is
     * deliberately NOT deleted: it is her page, and outlives the working session that filled it.
     */
    public void delete(Long applicationId) {
        CvApplication app = owned(applicationId);
        requirements.deleteByApplicationId(app.getId());
        sourceReads.deleteByApplicationId(app.getId());
        applications.delete(app);
    }

    /**
     * How much stored conversation is accepted.
     *
     * <p>A cap because this grows with every turn and nothing else bounds it. The <b>client</b> is
     * what drops the oldest messages — only it knows where one message ends — so reaching this is a
     * bug on that side, and it is refused rather than silently truncated: half a JSON document is
     * not a transcript, and storing one would lose the whole conversation on the next read.
     */
    public static final int TRANSCRIPT_MAX_CHARS = 500_000;

    /** The stored conversation for one application, or {@code null} when there is none. */
    @Transactional(readOnly = true)
    public String transcript(Long applicationId) {
        return owned(applicationId).getTranscript();
    }

    public CvApplication saveTranscript(Long applicationId, String content) {
        return saveTranscript(applicationId, content, null);
    }

    /**
     * Store the conversation, refusing a write based on an older copy.
     *
     * <p>Last-write-wins let a device that had not seen the latest turns overwrite them: the owner
     * took turns on her phone and the laptop, reloaded, and never saw them (2026-09-15). A client
     * that names the revision it last read is refused when another device has saved since, and
     * reloads instead. A client that names none keeps the old behaviour.
     */
    public CvApplication saveTranscript(Long applicationId, String content, Long baseRevision) {
        CvApplication app = owned(applicationId);
        if (baseRevision != null && baseRevision != app.getTranscriptRevision()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This conversation was continued on another device — reload it to see the latest.");
        }
        if (content != null && content.length() > TRANSCRIPT_MAX_CHARS) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "That conversation is too long to store (" + content.length()
                            + " characters, limit " + TRANSCRIPT_MAX_CHARS + ")");
        }
        // Blank is the same thing as absent: a cleared conversation should read back as "nothing
        // stored here", not as an empty string the client has to special-case.
        app.setTranscript(content == null || content.isBlank() ? null : content);
        app.setTranscriptRevision(app.getTranscriptRevision() + 1);
        return applications.save(app);
    }

    /**
     * Move to another phase. An invalid transition is rejected rather than tolerated: every phase
     * produces what the next one consumes, so a jump means the model decided it did not need the
     * material.
     *
     * <p>Leaving the map for the CV is a transition this method permits and the chat does not take
     * lightly: it is accepted there only on a turn the user wrote herself (see
     * {@code AiChatService.cvMove}), because only she can say the map is finished.
     */
    public CvApplication advancePhase(Long applicationId, CvPhase next) {
        CvApplication app = owned(applicationId);
        if (!app.getPhase().canAdvanceTo(next)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cannot go from " + app.getPhase().wireValue() + " to "
                            + (next == null ? "null" : next.wireValue()));
        }
        // **Nothing moves past the analysis before it has run.** The server runs it and moves the
        // work on by itself; a model that declared itself into the questions had nothing to ask
        // about and asked from memory off the advert (live, 2026-09-10 and 2026-09-15).
        if (app.getPhase() == CvPhase.ANALYSIS) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "the job analysis has not finished yet — the server runs it and moves the work "
                            + "on by itself");
        }
        app.setPhase(next);
        return applications.save(app);
    }

    /**
     * Record whether this vacancy wants a cover letter, and the addressee. It decides where the
     * candidate's motivation goes — into the letter, or into the summary when there is no letter to
     * carry it — so the summary cannot be written before it is known.
     */
    public CvApplication setLetterRequired(Long applicationId, boolean required, String contactName) {
        CvApplication app = owned(applicationId);
        app.setLetterRequired(required);
        if (contactName != null && !contactName.isBlank()) app.setContactName(contactName.strip());
        return applications.save(app);
    }

    /** Bind a note this application produced, by the document the proposal was for. */
    public CvApplication bindNote(Long applicationId, CvPhase phase, Long resourceId) {
        CvApplication app = owned(applicationId);
        switch (phase) {
            case MAP -> {
                if (app.getProfileNoteId() == null) app.setProfileNoteId(resourceId);
            }
            case DRAFT, CV_REVIEW -> app.setCvNoteId(resourceId);
            case LETTER -> app.setLetterNoteId(resourceId);
            case DONE -> app.setBriefingNoteId(resourceId);
            default -> throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Phase " + phase.wireValue() + " does not produce a note");
        }
        return applications.save(app);
    }

    public CvApplication bindApprovedNote(Long applicationId, Long resourceId) {
        return bindApprovedNote(applicationId, resourceId, null);
    }

    /**
     * Bind an approved note to the DOCUMENT its proposal was for.
     *
     * <p>The server stamps {@code cv_document} on every CV proposal, so a details note approved
     * after the work has moved on is still filed as her details, and a CV draft approved while the
     * map is being filled is still the CV. Binding by phase alone filed notes under the wrong
     * document.
     */
    public CvApplication bindApprovedNote(Long applicationId, Long resourceId, String document) {
        CvApplication app = owned(applicationId);
        if (resourceId == null) return app;
        if ((document == null || document.isBlank()) && !app.getPhase().producesNote()) return app;
        // **The id has to be a note on THIS goal.** It comes from a client, and nothing checked it:
        // a foreign id was stored and then quietly cleared again by `pruned()` on the next turn.
        if (resources.findExistingIds(app.getGoalId(), List.of(resourceId)).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Resource " + resourceId + " is not a resource of this application's goal");
        }
        if (document != null && !document.isBlank()) return bindDocument(app, document, resourceId);
        return bindNote(applicationId, app.getPhase(), resourceId);
    }

    private CvApplication bindDocument(CvApplication app, String document, Long resourceId) {
        switch (document.strip().toLowerCase(java.util.Locale.ROOT)) {
            case "profile", "intake" -> {
                if (app.getProfileNoteId() == null) app.setProfileNoteId(resourceId);
            }
            case "cv" -> app.setCvNoteId(resourceId);
            case "letter" -> app.setLetterNoteId(resourceId);
            case "briefing" -> app.setBriefingNoteId(resourceId);
            default -> {
                return app;
            }
        }
        return applications.save(app);
    }

    /**
     * The application with any pointer to a deleted resource cleared.
     *
     * <p>A note id is not a foreign key and deliberately so — a finished CV outlives the
     * application that wrote it. The cost is that a user who deletes a note leaves this row
     * pointing at nothing, and the prompt then announced a note the writer could not open. The map
     * pointer is cleared the same way, so a map she deleted is built afresh rather than announced.
     */
    public CvApplication pruned(Long applicationId) {
        CvApplication app = owned(applicationId);
        List<Long> held = Stream.of(app.getProfileNoteId(), app.getStoriesNoteId(), app.getCvNoteId(),
                        app.getLetterNoteId(), app.getBriefingNoteId(), app.getAnalysisNoteId(),
                        app.getMapResourceId())
                .filter(java.util.Objects::nonNull).distinct().toList();
        if (held.isEmpty()) return app;

        List<Long> alive = resources.findExistingIds(app.getGoalId(), held);
        if (alive.size() == held.size()) return app;

        if (gone(alive, app.getProfileNoteId())) app.setProfileNoteId(null);
        if (gone(alive, app.getStoriesNoteId())) app.setStoriesNoteId(null);
        if (gone(alive, app.getCvNoteId())) app.setCvNoteId(null);
        if (gone(alive, app.getLetterNoteId())) app.setLetterNoteId(null);
        if (gone(alive, app.getBriefingNoteId())) app.setBriefingNoteId(null);
        if (gone(alive, app.getAnalysisNoteId())) app.setAnalysisNoteId(null);
        if (gone(alive, app.getMapResourceId())) app.setMapResourceId(null);
        return applications.save(app);
    }

    private static boolean gone(List<Long> alive, Long id) {
        return id != null && !alive.contains(id);
    }

    /**
     * Everything the user has actually given, as one block of text for {@link CvFactGate} and
     * {@link CvTermGate}.
     *
     * <p><b>The job advert is deliberately not in here.</b> Including it would let an employer's own
     * numbers and technologies pass as the candidate's, which is the fabrication the gates exist to
     * catch — and copying the posting's words is the failure the whole feature was built to prevent.
     *
     * <p>What is in here: what she wrote on her vacancy map, her details note, and whatever was read
     * from her own sources.
     */
    @Transactional(readOnly = true)
    public String evidenceCorpus(Long applicationId) {
        CvApplication app = owned(applicationId);
        StringBuilder sb = new StringBuilder();
        append(sb, VacancyMapDocument.evidenceText(mapDocument(app)));
        for (Long noteId : new Long[] {app.getProfileNoteId(), app.getStoriesNoteId()}) {
            if (noteId == null) continue;
            resources.findById(noteId)
                    .filter(res -> res.getGoal() != null && app.getGoalId().equals(res.getGoal().getId()))
                    .ifPresent(res -> {
                        append(sb, res.getTitle());
                        append(sb, res.getBody());
                    });
        }
        for (CvSourceRead read : sourceReads.findByApplicationIdOrderByIdAsc(app.getId())) {
            if (read.isOk()) append(sb, read.getContent());
        }
        return sb.toString();
    }

    private static void append(StringBuilder sb, String text) {
        if (text != null && !text.isBlank()) sb.append(text).append('\n');
    }

    // ── The vacancy map, read ────────────────────────────────────────────────

    /**
     * The application's vacancy map as a tree — an empty object when it has none yet. Writing it is
     * {@link CvMapService}'s; this is the read the prompt, the progress and the gates share.
     */
    @Transactional(readOnly = true)
    public JsonNode mapDocument(CvApplication app) {
        return VacancyMapPatch.tree(mapOnGoal(app, app.getMapResourceId())
                .map(Resource::getMapData).orElse(null));
    }

    // ── For the clients ──────────────────────────────────────────────────────

    /**
     * The applications on a goal, newest first, with their progress.
     *
     * <p>A query each. That is fine and deliberately not optimised: a goal holds a handful of
     * applications, and a join to save two round-trips on a list of four rows is complexity bought
     * with nothing.
     */
    @Transactional(readOnly = true)
    public List<CvApplicationDto> listForClient(Long goalId) {
        return list(goalId).stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public CvApplicationDto getForClient(Long applicationId) {
        return toDto(owned(applicationId));
    }

    /**
     * Progress is the map's requirements: how many have an answer under at least one employer. That
     * is the one count that decides whether a CV can be written, and it is what "N open" on the
     * step pill means.
     */
    private CvApplicationDto toDto(CvApplication app) {
        VacancyMapDocument.Counts c = VacancyMapDocument.counts(mapDocument(app));
        String status = intakeStatus(app);
        // **The client does not re-derive which note was meant.** It used to match the title with
        // a regex of its own, which had drifted from this one — so the server said "candidate" and
        // said in words "press Use as my details", and the client, finding nothing, drew no
        // button at all (owner's live run, 2026-09-16). The id travels with the answer.
        Optional<ResourceView> candidate = "candidate".equals(status)
                ? intakeCandidate(app) : Optional.empty();
        return CvApplicationDto.from(app, c.requirementsAnswered(), c.requirements(), status,
                candidate.map(ResourceView::id).orElse(null),
                candidate.map(ResourceView::title).orElse(null),
                app.getPhase() == CvPhase.MAP ? c.requirementsOpen() : 0,
                c.skills(), c.requirements(), c.qualities());
    }

    // ── The analysis ─────────────────────────────────────────────────────────

    /** What the analysis read out of the advert itself. */
    public record AnalysisMeta(String role, List<String> roleTitles, String company, String location,
                               String language, String letter, String contactName, String coreMessage,
                               // The advert's stated CONDITIONS, which go on the map's facts card.
                               // Not competences: these are what the candidate checks herself
                               // against before applying. `language` above is a different thing —
                               // the language the advert is WRITTEN in.
                               String education, String experience, String requiredLanguages,
                               // The two links the advert itself carries. `applyUrl` only fills the
                               // map's own link field when the application was started from pasted
                               // text and so has no URL of its own (owner, 2026-09-22).
                               String applyUrl, String companyUrl) {

        /** The eight-field form, for every caller that predates the map's facts card. */
        public AnalysisMeta(String role, List<String> roleTitles, String company, String location,
                            String language, String letter, String contactName, String coreMessage) {
            this(role, roleTitles, company, location, language, letter, contactName, coreMessage,
                    null, null, null, null, null);
        }

        /** The eleven-field form, for callers that predate the advert's links. */
        public AnalysisMeta(String role, List<String> roleTitles, String company, String location,
                            String language, String letter, String contactName, String coreMessage,
                            String education, String experience, String requiredLanguages) {
            this(role, roleTitles, company, location, language, letter, contactName, coreMessage,
                    education, experience, requiredLanguages, null, null);
        }
    }

    /**
     * One thing the employer asks for, in one of five categories. A competence is a tag, a
     * requirement is the employer's own sentence, a trait carries the line that implies it, and an
     * extra note is context — what {@link VacancyMapBuild} turns into the map's parts.
     */
    public record Item(String category, String key, String label, String demand, String context,
                       CvRequirement.Weight weight, String extraUse, List<Quote> quotes) {}

    /** One of the advert's own lines, and which line of the advert it is (1-based). */
    public record Quote(String text, Integer line) {}

    /**
     * A note title that might be the user's own details.
     *
     * <p><b>{@code cv} is deliberately not one of them.</b> The writer's own output is titled
     * "CV — <vacancy>", so once one existed on the goal it became a candidate for "are these your
     * details?", and a second application would have offered the previous vacancy's finished CV
     * as the intake form (owner's live run, 2026-09-16).
     *
     * <p>The pattern is only half the guard. Provenance is the other half and it is the real one:
     * see {@link #ourDocuments} — what the app wrote is known at the moment it was written, and a
     * name can never be made to tell the two apart.
     */
    private static final java.util.regex.Pattern INTAKE_TITLE =
            java.util.regex.Pattern.compile("(?iu)profil|профил|мои данные|uppgifter|details|анкет");

    /**
     * Store what the analysis read about the advert itself — the role, the employer, the language,
     * the letter, the hard conditions. The advert's DEMANDS go onto the vacancy map, not here.
     *
     * @param state      {@code done}, or {@code empty} when the advert states no requirements
     * @param conditions the advert's hard conditions, raised with the user before anything else
     */
    public CvApplication recordAnalysis(Long applicationId, AnalysisMeta meta, String state,
                                        List<String> conditions) {
        CvApplication app = owned(applicationId);
        if (app.getPhase() != CvPhase.ANALYSIS) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "the advert is only analysed in step 1 — this application is in "
                            + app.getPhase().wireValue());
        }
        if (meta != null) {
            if (meta.role() != null) app.setRoleTitle(clip(meta.role(), 300));
            if (meta.company() != null) app.setCompanyName(clip(meta.company(), 300));
            if (meta.location() != null) app.setLocation(clip(meta.location(), 300));
            app.setCoreMessage(meta.coreMessage());
            if (app.getVacancyLanguage() == null && meta.language() != null) {
                app.setVacancyLanguage(clip(meta.language(), 16));
            }
            // A Swedish application goes with a personligt brev unless the advert says otherwise;
            // the user can always say no, and the letter step asks.
            if (app.getLetterRequired() == null) app.setLetterRequired(!"no".equalsIgnoreCase(meta.letter()));
            if (meta.contactName() != null && !meta.contactName().isBlank()) {
                app.setContactName(clip(meta.contactName(), 300));
            }
        }
        List<String> kept = conditions == null ? List.of()
                : conditions.stream().filter(c -> c != null && !c.isBlank()).map(String::strip).toList();
        app.setAdvertConditions(kept.isEmpty() ? null : String.join("\n", kept));
        app.setCursorIndex(0);
        app.setAnalysisState(state == null ? "done" : state);
        return applications.save(app);
    }

    /** The analysis is complete: on to the vacancy map. */
    public CvApplication finishAnalysis(Long applicationId) {
        CvApplication app = owned(applicationId);
        if (!"empty".equals(app.getAnalysisState())) app.setAnalysisState("done");
        if (app.getPhase() == CvPhase.ANALYSIS) app.setPhase(CvPhase.MAP);
        return applications.save(app);
    }

    public CvApplication setAnalysisState(Long applicationId, String state) {
        CvApplication app = owned(applicationId);
        app.setAnalysisState(state);
        return applications.save(app);
    }

    /** The advert's hard conditions, one per stored line. */
    public static List<String> conditionsOf(CvApplication app) {
        String raw = app.getAdvertConditions();
        if (raw == null || raw.isBlank()) return List.of();
        return Arrays.stream(raw.split("\\R")).map(String::strip).filter(s -> !s.isEmpty()).toList();
    }

    /**
     * The rows of an application started before the map, oldest first — for the one-time
     * conversion into a map ({@link CvMapService#ensure}). Nothing else reads them.
     */
    @Transactional(readOnly = true)
    public List<CvRequirement> legacyRows(Long applicationId) {
        return requirements.findByApplicationIdOrderByQueueIndexAsc(owned(applicationId).getId());
    }

    // ── Her details ──────────────────────────────────────────────────────────

    /** {@code exists}, {@code candidate} (a note that looks like one) or {@code none}. */
    @Transactional(readOnly = true)
    public String intakeStatus(CvApplication app) {
        if (app.getProfileNoteId() != null && noteOnGoal(app, app.getProfileNoteId()).isPresent()) {
            return "exists";
        }
        return intakeCandidate(app).isPresent() ? "candidate" : "none";
    }

    /**
     * A vacancy map already on this goal that no application has claimed.
     *
     * <p><b>The user may have made and filled one herself</b> — that is what the page is for —
     * and the writer used to ignore it and create a second, then work only from its own
     * (owner, 2026-09-23). Hers is the one the work belongs to; this is how it is found.
     *
     * <p>"Unclaimed" means no application of hers records it as its map, so re-reading one advert
     * never steals the map of another vacancy.
     */
    @Transactional(readOnly = true)
    public Optional<Resource> unclaimedMapOnGoal(CvApplication app) {
        Set<Long> claimed = list(app.getGoalId()).stream()
                .map(CvApplication::getMapResourceId)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        return resources.findViewsByGoalId(app.getGoalId()).stream()
                .filter(v -> "vacancy".equals(v.type()) && !claimed.contains(v.id()))
                .findFirst()
                .flatMap(v -> resources.findById(v.id()));
    }

    /** A note on the goal that looks like the user's details, offered when none is bound. */
    @Transactional(readOnly = true)
    public Optional<ResourceView> intakeCandidate(CvApplication app) {
        Set<Long> ours = ourDocuments(app);
        return resources.findViewsByGoalId(app.getGoalId()).stream()
                .filter(v -> "note".equals(v.type()) && v.title() != null && !ours.contains(v.id()))
                .filter(v -> INTAKE_TITLE.matcher(v.title()).find())
                .findFirst();
    }

    /**
     * Every note the app itself produced on this goal — for <b>any</b> of the user's applications
     * on it, not just this one.
     *
     * <p>Provenance is recorded when a document is written and never inferred when one is read.
     * These pointers already are that record; the omission was only that they were consulted for the
     * current application alone, so last vacancy's finished CV was still offered back as "are these
     * your details?" (owner's live run, 2026-09-16).
     *
     * <p>The intake note is deliberately NOT in here: it is the user's own note, shared across her
     * applications, and being offered it again is the point of the question.
     */
    private Set<Long> ourDocuments(CvApplication app) {
        Set<Long> ours = new java.util.HashSet<>();
        List<CvApplication> siblings = new ArrayList<>(applications
                .findByAppUserIdAndGoalIdOrderByUpdatedAtDesc(app.getAppUserId(), app.getGoalId()));
        // The caller's own copy may hold a pointer the stored row has not got yet.
        siblings.add(app);
        for (CvApplication other : siblings) {
            Stream.of(other.getCvNoteId(), other.getLetterNoteId(), other.getBriefingNoteId(),
                            other.getAnalysisNoteId(), other.getStoriesNoteId())
                    .filter(java.util.Objects::nonNull).forEach(ours::add);
        }
        return ours;
    }

    /** "Use as my details": bind an existing note of this goal as her details. */
    public CvApplication useAsIntake(Long applicationId, Long resourceId) {
        CvApplication app = owned(applicationId);
        Resource note = noteOnGoal(app, resourceId).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Note " + resourceId + " is not a note of this application's goal"));
        app.setProfileNoteId(note.getId());
        return applications.save(app);
    }

    /**
     * The analysis has been reported to the user — what was read, and the map.
     *
     * <p>Separate from {@link #markAnnounced} because the two are not one event: an advert carrying
     * a condition she has to answer now is reported and then waits for her, so the map step is
     * announced on a later turn and the summary must not be said twice.
     */
    public CvApplication markAnalysisReported(Long applicationId) {
        CvApplication app = owned(applicationId);
        if (app.getAnalysisReportedAt() == null) app.setAnalysisReportedAt(java.time.Instant.now());
        return applications.save(app);
    }

    /** The step the user has now been told about. */
    public CvApplication markAnnounced(Long applicationId, int step) {
        CvApplication app = owned(applicationId);
        app.setAnnouncedStep(step);
        return applications.save(app);
    }

    public CvApplication setConversationLanguage(Long applicationId, String language) {
        CvApplication app = owned(applicationId);
        if (language == null || language.equals(app.getConversationLanguage())) return app;
        app.setConversationLanguage(clip(language, 16));
        return applications.save(app);
    }

    // ── Sources the writer has read ─────────────────────────────────────────

    /** Record (or refresh) one source that was read. Content is the user's material; never logged. */
    public CvSourceRead recordSourceRead(Long applicationId, String key, String label, String content,
                                         boolean ok, String origin) {
        CvApplication app = owned(applicationId);
        CvSourceRead row = sourceReads.findByApplicationIdAndSourceKey(app.getId(), clip(key, 600))
                .orElseGet(CvSourceRead::new);
        row.setApplicationId(app.getId());
        row.setSourceKey(clip(key, 600));
        row.setLabel(clip(label, 300));
        row.setContent(content);
        row.setChars(content == null ? 0 : content.length());
        row.setOk(ok);
        row.setOrigin(origin == null ? "analysis" : origin);
        return sourceReads.save(row);
    }

    @Transactional(readOnly = true)
    public List<CvSourceRead> sourceReads(Long applicationId) {
        return sourceReads.findByApplicationIdOrderByIdAsc(owned(applicationId).getId());
    }

    @Transactional(readOnly = true)
    public Optional<CvSourceRead> sourceRead(Long applicationId, String key) {
        return sourceReads.findByApplicationIdAndSourceKey(owned(applicationId).getId(), key);
    }

    /**
     * Fetch the advert again and start the analysis over — only while the application has no map.
     *
     * <p>For applications created before the advert was read from a job board's structured data:
     * their stored text is the whole page, cookie banner and footer included. Once a map exists it
     * is her page and may hold her answers, so re-reading would mean a second map beside it; she is
     * told to delete the map first if that is really what she wants.
     */
    public CvApplication rereadVacancy(Long applicationId) {
        CvApplication app = pruned(applicationId);
        if (app.getMapResourceId() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This vacancy already has a map — delete the map first if you want it read again");
        }
        if (app.getVacancyUrl() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This application has no link to read again");
        }
        UrlReadService.Fetched fetched = urlReadService.fetch(app.getVacancyUrl());
        if (!fetched.ok()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "That link could not be read again. (" + fetched.text() + ")");
        }
        app.setVacancyText(fetched.text());
        requirements.deleteByApplicationId(app.getId());
        sourceReads.deleteByApplicationId(app.getId());
        app.setAnalysisState("none");
        app.setAdvertConditions(null);
        app.setCursorIndex(0);
        app.setPhase(CvPhase.ANALYSIS);
        app.setAnnouncedStep(0);
        return applications.save(app);
    }

    /** Everything a step message may mention — see {@link CvTransitions}. */
    @Transactional(readOnly = true)
    public CvTransitions.Facts facts(Long applicationId) {
        CvApplication app = owned(applicationId);
        String status = intakeStatus(app);
        String intakeTitle = null;
        String candidateTitle = null;
        List<String> missing = List.of();
        if ("exists".equals(status)) {
            Resource intake = noteOnGoal(app, app.getProfileNoteId()).orElseThrow();
            intakeTitle = intake.getTitle();
            missing = CvIntakeCheck.check(intake.getBody()).missing();
        } else if ("candidate".equals(status)) {
            candidateTitle = intakeCandidate(app).map(ResourceView::title).orElse(null);
        }
        Optional<Resource> map = mapOnGoal(app, app.getMapResourceId());
        String mapLink = map.map(r -> "/goals/" + app.getGoalId() + "?resource=" + r.getId()).orElse(null);
        String mapTitle = map.map(Resource::getTitle).orElse(null);
        // A map older than the application is one she made herself and this application adopted
        // (owner, 2026-09-23) - the step message says so, so she is never left wondering whether
        // her own work is the one being used.
        boolean mapIsHers = map.map(Resource::getCreatedAt)
                .filter(created -> app.getCreatedAt() != null && created.isBefore(app.getCreatedAt()))
                .isPresent();
        String cvNote = app.getCvNoteId() == null ? null
                : noteOnGoal(app, app.getCvNoteId()).map(Resource::getTitle).orElse(null);
        VacancyMapDocument.Counts c = VacancyMapDocument.counts(
                VacancyMapPatch.tree(map.map(Resource::getMapData).orElse(null)));
        return new CvTransitions.Facts(
                app.getTitle(), app.getRoleTitle(), app.getCompanyName(), app.getLocation(),
                app.getCoreMessage(), status, intakeTitle, candidateTitle, missing,
                c.skills(), c.important(), c.requirements(), c.qualities(),
                conditionsOf(app), c.requirementsOpen(),
                mapTitle, cvNote, app.getLetterRequired(), mapLink, mapIsHers);
    }

    /**
     * The vacancy map of this application's goal, or empty.
     *
     * <p>Its own lookup rather than relaxing {@link #noteOnGoal}: that helper also guards which
     * resource may be bound as her details and as the finished CV, and widening it to accept any
     * type would let a vacancy map be taken for her details.
     */
    @Transactional(readOnly = true)
    public Optional<Resource> mapOnGoal(CvApplication app, Long resourceId) {
        if (resourceId == null) return Optional.empty();
        return resources.findById(resourceId)
                .filter(r -> r.getGoal() != null && app.getGoalId().equals(r.getGoal().getId()))
                .filter(r -> "vacancy".equals(r.getType()));
    }

    /** Records which vacancy map this application's advert was read into. */
    public CvApplication setMapResource(Long applicationId, Long resourceId) {
        CvApplication app = owned(applicationId);
        app.setMapResourceId(resourceId);
        return applications.save(app);
    }

    /** A note of this application's goal, or empty. */
    @Transactional(readOnly = true)
    public Optional<Resource> noteOnGoal(CvApplication app, Long resourceId) {
        if (resourceId == null) return Optional.empty();
        return resources.findById(resourceId)
                .filter(r -> r.getGoal() != null && app.getGoalId().equals(r.getGoal().getId()))
                .filter(r -> "note".equals(r.getType()));
    }

    // ── Ownership ────────────────────────────────────────────────────────────

    /** The application must exist and belong to the caller. */
    private CvApplication owned(Long applicationId) {
        if (applicationId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "An application id is required");
        }
        Long userId = currentUserProvider.getCurrentUser().getId();
        return applications.findByIdAndAppUserId(applicationId, userId).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Application " + applicationId + " not found"));
    }

    /** The goal must exist and be the caller's; returns the caller's id. */
    private Long ownedGoal(Long goalId) {
        Long userId = currentUserProvider.getCurrentUser().getId();
        if (goalId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "An application needs a goal");
        }
        goalRepository.findByIdAndUserId(goalId, userId).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Goal " + goalId + " not found"));
        return userId;
    }

    /**
     * Bound a string a MODEL supplied before it meets a {@code VARCHAR}.
     *
     * <p>Untruncated, a perfectly reasonable label longer than the column made Postgres reject the
     * insert, and the whole reading of the advert was lost with the model told only that it "could
     * not be stored". Clipping is right rather than rejecting: these are labels, not the user's own
     * words, and none of them is worth failing a session over.
     */
    private static String clip(String value, int max) {
        if (value == null) return null;
        String v = value.strip();
        return v.length() <= max ? v : v.substring(0, max);
    }
}
