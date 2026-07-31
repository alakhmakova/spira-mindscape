package com.spiramindscape.backend.ai;

import com.spiramindscape.backend.ai.chat.AiChatService;
import com.spiramindscape.backend.ai.chat.dto.ChatRequest;
import com.spiramindscape.backend.ai.chat.transcript.AiChatTranscriptService;
import com.spiramindscape.backend.ai.chat.transcript.dto.SaveTranscriptRequest;
import com.spiramindscape.backend.ai.chat.transcript.dto.TranscriptDto;
import com.spiramindscape.backend.ai.grow.GoalMemoryService;
import com.spiramindscape.backend.ai.key.AiKeyService;
import com.spiramindscape.backend.ai.key.dto.KeyInfoResponse;
import com.spiramindscape.backend.ai.key.dto.SaveKeyRequest;
import com.spiramindscape.backend.ai.model.AiModelService;
import com.spiramindscape.backend.ai.preference.AiPreferenceService;
import com.spiramindscape.backend.ai.proposal.AiProposalService;
import com.spiramindscape.backend.ai.proposal.dto.ProposalDto;
import jakarta.validation.Valid;
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
    private final AiModelService modelService;
    private final AiProposalService proposalService;
    private final GoalMemoryService goalMemoryService;
    private final AiChatTranscriptService transcriptService;
    private final AiPreferenceService preferenceService;

    public AiController(
            AiKeyService keyService,
            AiChatService chatService,
            AiModelService modelService,
            AiProposalService proposalService,
            GoalMemoryService goalMemoryService,
            AiChatTranscriptService transcriptService,
            AiPreferenceService preferenceService) {
        this.keyService = keyService;
        this.chatService = chatService;
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

    // ── Preferences (cross-device) ────────────────────────────────────────────

    public record AiPreferencesDto(String provider) {}

    public record SavePreferencesRequest(
            @jakarta.validation.constraints.Pattern(
                    regexp = "ANTHROPIC|OPENAI|MISTRAL|GEMINI|anthropic|openai|mistral|gemini")
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
