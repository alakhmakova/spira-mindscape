package com.spiramindscape.backend.ai;

import com.spiramindscape.backend.ai.chat.AiChatService;
import com.spiramindscape.backend.ai.chat.dto.ChatRequest;
import com.spiramindscape.backend.ai.chat.transcript.AiChatTranscriptService;
import com.spiramindscape.backend.ai.chat.transcript.dto.SaveTranscriptRequest;
import com.spiramindscape.backend.ai.chat.transcript.dto.TranscriptDto;
import com.spiramindscape.backend.ai.cv.CvApplicationService;
import com.spiramindscape.backend.ai.cv.dto.CreateCvApplicationRequest;
import com.spiramindscape.backend.ai.cv.dto.CvApplicationDto;
import com.spiramindscape.backend.ai.grow.session.GrowSessionService;
import com.spiramindscape.backend.ai.grow.session.dto.GrowSessionDto;
import com.spiramindscape.backend.ai.grow.session.dto.SaveGrowSessionRequest;
import com.spiramindscape.backend.ai.chat.transcript.dto.TranscriptRevisionDto;
import com.spiramindscape.backend.ai.grow.GoalMemoryService;
import com.spiramindscape.backend.ai.key.AiKeyService;
import com.spiramindscape.backend.ai.key.dto.KeyInfoResponse;
import com.spiramindscape.backend.ai.key.dto.SaveKeyRequest;
import com.spiramindscape.backend.ai.model.AiModelService;
import com.spiramindscape.backend.ai.preference.AiPreferenceService;
import com.spiramindscape.backend.ai.proposal.AiProposalService;
import com.spiramindscape.backend.ai.proposal.dto.ProposalDto;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * REST controller for the AI sub-system.
 *
 * <h2>Endpoints</h2>
 * <pre>
 * POST   /api/ai/keys                    Save (or update) an API key
 * GET    /api/ai/keys                    List configured providers (masked)
 * DELETE /api/ai/keys/{provider}         Delete a provider key
 *
 * POST   /api/ai/chat                    Stream a chat response (SSE)
 *
 * GET    /api/ai/proposals               List pending proposals
 * GET    /api/ai/proposals/goal/{goalId} List pending proposals for a goal
 * POST   /api/ai/proposals/{id}/approve  Approve a proposal
 * POST   /api/ai/proposals/{id}/reject   Reject a proposal
 * </pre>
 *
 * <p>Authentication: all endpoints require a valid session once Google OAuth
 * is merged. In the {@code feature/ai} branch a dev stub user (id = 1) is
 * used automatically.
 */
@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final AiKeyService keyService;
    private final AiChatService chatService;
    private final GrowSessionService growSessionService;
    private final CvApplicationService cvApplicationService;
    private final AiModelService modelService;
    private final AiProposalService proposalService;
    private final GoalMemoryService goalMemoryService;
    private final AiChatTranscriptService transcriptService;
    private final AiPreferenceService preferenceService;

    public AiController(
            AiKeyService keyService,
            AiChatService chatService,
            GrowSessionService growSessionService,
            CvApplicationService cvApplicationService,
            AiModelService modelService,
            AiProposalService proposalService,
            GoalMemoryService goalMemoryService,
            AiChatTranscriptService transcriptService,
            AiPreferenceService preferenceService) {
        this.keyService = keyService;
        this.chatService = chatService;
        this.growSessionService = growSessionService;
        this.cvApplicationService = cvApplicationService;
        this.modelService = modelService;
        this.proposalService = proposalService;
        this.goalMemoryService = goalMemoryService;
        this.transcriptService = transcriptService;
        this.preferenceService = preferenceService;
    }

    // ── Key management ────────────────────────────────────────────────────────

    /**
     * Save or update an API key for a provider.
     * The raw key is never stored — only AES-256-GCM ciphertext.
     *
     * @return a safe representation of the saved key (provider, hint, model)
     */
    @PostMapping("/keys")
    public KeyInfoResponse saveKey(@RequestBody @Valid SaveKeyRequest request) {
        return keyService.saveKey(request);
    }

    /**
     * List all providers for which a key is configured.
     * Never returns the raw or encrypted key.
     */
    @GetMapping("/keys")
    public List<KeyInfoResponse> listKeys() {
        return keyService.listKeys();
    }

    /**
     * Fetch available models from the provider's API.
     * Requires a saved key for the given provider.
     */
    @GetMapping("/keys/{provider}/models")
    public List<String> listProviderModels(@PathVariable String provider) {
        return modelService.listModels(provider);
    }

    /**
     * Update the model preference for an existing key without re-supplying the key.
     */
    @PatchMapping("/keys/{provider}")
    public KeyInfoResponse updateKeyModel(
            @PathVariable String provider,
            @RequestBody Map<String, String> body) {
        return keyService.updateModel(provider, Objects.requireNonNull(body.get("model"), "model is required"));
    }

    /**
     * Delete the stored key for the given provider.
     * Case-insensitive: {@code anthropic}, {@code ANTHROPIC}, etc. all work.
     */
    @DeleteMapping("/keys/{provider}")
    public ResponseEntity<Map<String, String>> deleteKey(@PathVariable String provider) {
        keyService.deleteKey(provider);
        return ResponseEntity.ok(Map.of("status", "deleted", "provider", provider.toUpperCase()));
    }

    // ── Chat ─────────────────────────────────────────────────────────────────

    /**
     * Start a streaming chat request. Returns an SSE stream.
     *
     * <p>SSE events:
     * <ul>
     *   <li>{@code token} — a text chunk from the AI (may arrive many times)</li>
     *   <li>{@code done} — signals the end of the stream</li>
     *   <li>{@code error} — an error occurred; stream will end after this event</li>
     * </ul>
     *
     * <p>Example frontend usage (fetch + ReadableStream):
     * <pre>
     * const sse = new EventSource('/api/ai/chat', { ... });
     * sse.addEventListener('token', e => append(e.data));
     * sse.addEventListener('done', () => sse.close());
     * </pre>
     */
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@RequestBody @Valid ChatRequest request) {
        return chatService.chat(request);
    }

    // ── Proposals ─────────────────────────────────────────────────────────────

    /** List all pending proposals for the current user. */
    @GetMapping("/proposals")
    public List<ProposalDto> listProposals() {
        return proposalService.listPending();
    }

    /** List all pending proposals for a specific goal. */
    @GetMapping("/proposals/goal/{goalId}")
    public List<ProposalDto> listProposalsForGoal(@PathVariable Long goalId) {
        return proposalService.listPendingForGoal(goalId);
    }

    /**
     * Approve a proposal.
     *
     * <p>Approving marks the proposal as {@code APPROVED} but does NOT
     * automatically apply the change — the frontend is responsible for
     * calling the appropriate GraphQL mutation with the proposal payload.
     */
    @PostMapping("/proposals/{id}/approve")
    public ProposalDto approve(@PathVariable Long id) {
        return proposalService.approve(id);
    }

    /** Reject a proposal. The proposal payload is discarded. */
    @PostMapping("/proposals/{id}/reject")
    public ProposalDto reject(@PathVariable Long id) {
        return proposalService.reject(id);
    }

    // ── GROW session memory ───────────────────────────────────────────────────

    public record SaveMemoryRequest(String summary) {}

    /**
     * Append a GROW session summary to the goal's memory ("Save memory" on
     * the session end card). Later GROW sessions read it back into the system
     * prompt so the coach continues from where the last one ended.
     */
    @PostMapping("/goals/{goalId}/memory")
    public ResponseEntity<Map<String, String>> saveGoalMemory(
            @PathVariable Long goalId, @RequestBody SaveMemoryRequest request) {
        goalMemoryService.append(goalId, request.summary());
        return ResponseEntity.ok(Map.of("status", "saved"));
    }

    // ── Chat transcript sync (cross-device) ───────────────────────────────────

    /**
     * The current user's stored chat transcript for a scope ({@code goalId}
     * omitted = the global chat). Returned so a device can pick up a
     * conversation started on another device. Empty {@code "[]"} when none.
     */
    @GetMapping("/chat/transcript")
    public TranscriptDto getTranscript(@RequestParam(required = false) Long goalId) {
        return transcriptService.get(goalId);
    }

    /**
     * When the stored transcript for a scope last changed — the cheap poll target. A client asks
     * for this every few seconds and only calls {@link #getTranscript} when the timestamp moved,
     * so an open chat panel no longer transfers the whole conversation on every tick.
     */
    @GetMapping("/chat/transcript/revision")
    public TranscriptRevisionDto getTranscriptRevision(@RequestParam(required = false) Long goalId) {
        return transcriptService.revision(goalId);
    }

    /** Upsert (last write wins) the current user's transcript for a scope. */
    @PutMapping("/chat/transcript")
    public TranscriptDto saveTranscript(@RequestBody @Valid SaveTranscriptRequest request) {
        return transcriptService.save(request.goalId(), request.content());
    }

    /** Clear the current user's transcript for a scope ("New chat"). */
    @DeleteMapping("/chat/transcript")
    public ResponseEntity<Map<String, String>> clearTranscript(
            @RequestParam(required = false) Long goalId) {
        transcriptService.clear(goalId);
        return ResponseEntity.ok(Map.of("status", "cleared"));
    }

    // ── The live GROW session (cross-device) ─────────────────────────────────
    //
    // The transcript has synced since BUG-018; the session running inside it did not, so one
    // begun on the phone did not exist on the laptop (owner, 2026-09-08). Same three verbs, and
    // the same "last write wins" rule — except that a session is DELETED when it ends, because
    // what outlives it is the record the user keeps and whatever they approved into the goal.

    /** The current user's live session for a goal ({@code content} null when there is none). */
    @GetMapping("/grow/session")
    public GrowSessionDto growSession(@RequestParam Long goalId) {
        return growSessionService.get(goalId);
    }

    /** Store the live session after a settled turn. */
    @PutMapping("/grow/session")
    public GrowSessionDto saveGrowSession(@RequestBody @Valid SaveGrowSessionRequest request) {
        return growSessionService.save(request.goalId(), request.content());
    }

    /** The session ended — drop it. */
    @DeleteMapping("/grow/session")
    public ResponseEntity<Map<String, String>> clearGrowSession(@RequestParam Long goalId) {
        growSessionService.clear(goalId);
        return ResponseEntity.ok(Map.of("status", "cleared"));
    }

    // ── CV applications ──────────────────────────────────────────────────────
    //
    // One row per VACANCY, not per goal: "find a QA job" holds many applications, so
    // unlike a GROW session the goal id is not enough to say what is being worked on.
    // Every method is owner-scoped inside the service — an id from the client is not a
    // permission, and this one addresses somebody's employment history.

    /** The applications on a goal, newest first, with how far each interview has got. */
    @GetMapping("/cv/applications")
    public List<CvApplicationDto> cvApplications(@RequestParam Long goalId) {
        return cvApplicationService.listForClient(goalId);
    }

    /**
     * Start an application from a job advert.
     *
     * <p><b>409 with the EXISTING application</b> when this goal already has one for the
     * same advert, unless {@code force} says to make a second anyway. Three rows for one
     * vacancy is what happens without this (owner, 2026-09-09) — and a plain refusal would
     * be wrong too, because applying twice to the same posting after a rewrite is a real
     * thing. The user is asked; the body is what they are asked about.
     */
    @PostMapping("/cv/applications")
    public ResponseEntity<CvApplicationDto> createCvApplication(
            @RequestBody @Valid CreateCvApplicationRequest request,
            @RequestParam(defaultValue = "false") boolean force) {
        if (!force) {
            String title = com.spiramindscape.backend.ai.cv.CvApplicationService.titleFor(
                    request.title(), request.vacancyText(), request.vacancyUrl());
            var existing = cvApplicationService.findDuplicate(
                    request.goalId(), request.vacancyUrl(), title);
            if (existing.isPresent()) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(cvApplicationService.getForClient(existing.get().getId()));
            }
        }
        var created = cvApplicationService.create(request.goalId(), request.title(),
                request.vacancyUrl(), request.vacancyText(), request.vacancyLanguage());
        return ResponseEntity.ok(cvApplicationService.getForClient(created.getId()));
    }

    /** One application — what the panel reads when it resumes an unfinished one. */
    @GetMapping("/cv/applications/{id}")
    public CvApplicationDto cvApplication(@PathVariable Long id) {
        return cvApplicationService.getForClient(id);
    }

    /** What the client sends when the user approves a note the writer proposed. */
    public record BindCvNoteRequest(
            @jakarta.validation.constraints.NotNull Long resourceId,
            @jakarta.validation.constraints.Size(max = 16) String document) {}

    /**
     * Tell an application which note it just produced.
     *
     * <p>The PHASE is the server's own — the client holds whatever it loaded the
     * application with, and a session that has moved on since would file the finished CV
     * as the profile. Best-effort from the caller's point of view: the note exists either
     * way, and an application that does not know about it is a smaller problem than an
     * approval that appears to fail.
     */
    @PostMapping("/cv/applications/{id}/note")
    public CvApplicationDto bindCvNote(
            @PathVariable Long id, @RequestBody @Valid BindCvNoteRequest request) {
        cvApplicationService.bindApprovedNote(id, request.resourceId(), request.document());
        return cvApplicationService.getForClient(id);
    }

    /** What "Use as my details" sends: an existing note of the goal. */
    public record UseCvIntakeRequest(@jakarta.validation.constraints.NotNull Long resourceId) {}

    @PostMapping("/cv/applications/{id}/intake/use")
    public CvApplicationDto useCvIntake(
            @PathVariable Long id, @RequestBody @Valid UseCvIntakeRequest request) {
        cvApplicationService.useAsIntake(id, request.resourceId());
        return cvApplicationService.getForClient(id);
    }

    /**
     * The words on the panel's own CV controls, in the language this conversation runs in.
     *
     * <p>Copy belongs to the server like every other word the process says: a control carrying an
     * English literal under a localised announcement read as two different applications (owner's
     * live run, 2026-09-16).
     */
    @GetMapping("/cv/applications/{id}/copy")
    public com.spiramindscape.backend.ai.cv.CvCardText cvCardText(@PathVariable Long id) {
        return com.spiramindscape.backend.ai.cv.CvCardText.of(
                cvApplicationService.get(id).getConversationLanguage());
    }

    /** Fetch the advert again (only while the application has no map yet) and redo the analysis. */
    @PostMapping("/cv/applications/{id}/vacancy/reread")
    public CvApplicationDto rereadCvVacancy(@PathVariable Long id) {
        cvApplicationService.rereadVacancy(id);
        return cvApplicationService.getForClient(id);
    }

    /** One application's stored conversation ({@code content} null when there is none). */
    public record CvTranscriptDto(Long applicationId, String content, long revision) {}

    /** The body of a transcript save. */
    public record SaveCvTranscriptRequest(String content, Long baseRevision) {}

    /**
     * The conversation for one application.
     *
     * <p>Its own endpoint rather than a field on {@link CvApplicationDto}: that DTO is
     * what the Continue list is built from, and shipping every application's whole
     * conversation to draw a list of titles would be absurd.
     */
    @GetMapping("/cv/applications/{id}/transcript")
    public CvTranscriptDto cvTranscript(@PathVariable Long id) {
        return new CvTranscriptDto(id, cvApplicationService.transcript(id),
                cvApplicationService.get(id).getTranscriptRevision());
    }

    /**
     * Store the conversation after a settled turn. 409 when {@code baseRevision} is older
     * than the stored one — another device has saved since, and the client reloads.
     */
    @PutMapping("/cv/applications/{id}/transcript")
    public CvTranscriptDto saveCvTranscript(
            @PathVariable Long id, @RequestBody SaveCvTranscriptRequest request) {
        var saved = cvApplicationService.saveTranscript(id, request.content(), request.baseRevision());
        return new CvTranscriptDto(id, saved.getTranscript(), saved.getTranscriptRevision());
    }

    /**
     * Discard an application and everything extracted from its advert.
     *
     * <p>The notes it produced are goal resources and are deliberately NOT touched:
     * a finished CV outlives the working session that wrote it, and deleting a
     * person's CV because they tidied away the application would be its own defect.
     */
    @DeleteMapping("/cv/applications/{id}")
    public ResponseEntity<Map<String, String>> deleteCvApplication(@PathVariable Long id) {
        cvApplicationService.delete(id);
        return ResponseEntity.ok(Map.of("status", "deleted"));
    }

    // ── Preferences (cross-device) ────────────────────────────────────────────

    public record AiPreferencesDto(String provider) {}

    public record SavePreferencesRequest(
            @jakarta.validation.constraints.Pattern(
                    regexp = "ANTHROPIC|OPENAI|MISTRAL|GEMINI|COHERE"
                            + "|anthropic|openai|mistral|gemini|cohere")
            String provider) {}

    /** The current user's saved chat provider (null if none chosen yet). */
    @GetMapping("/preferences")
    public AiPreferencesDto getPreferences() {
        return new AiPreferencesDto(preferenceService.getProvider());
    }

    /** Persist the current user's selected chat provider so it follows them. */
    @PutMapping("/preferences")
    public AiPreferencesDto savePreferences(@RequestBody @Valid SavePreferencesRequest request) {
        return new AiPreferencesDto(preferenceService.setProvider(request.provider()));
    }
}
