package com.spiramindscape.backend.ai;

import com.spiramindscape.backend.ai.chat.AiChatService;
import com.spiramindscape.backend.ai.chat.dto.ChatRequest;
import com.spiramindscape.backend.ai.key.AiKeyService;
import com.spiramindscape.backend.ai.key.dto.KeyInfoResponse;
import com.spiramindscape.backend.ai.key.dto.SaveKeyRequest;
import com.spiramindscape.backend.ai.model.AiModelService;
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

    public AiController(
            AiKeyService keyService,
            AiChatService chatService,
            AiModelService modelService,
            AiProposalService proposalService) {
        this.keyService = keyService;
        this.chatService = chatService;
        this.modelService = modelService;
        this.proposalService = proposalService;
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
}
