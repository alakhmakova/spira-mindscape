package com.spiramindscape.backend.ai.chat;

import com.spiramindscape.backend.ai.chat.dto.ChatRequest;
import com.spiramindscape.backend.ai.key.AiKeyService;
import com.spiramindscape.backend.ai.provider.LlmMessage;
import com.spiramindscape.backend.ai.provider.LlmProvider;
import com.spiramindscape.backend.ai.provider.LlmProviderFactory;
import com.spiramindscape.backend.ai.provider.ProviderType;
import com.spiramindscape.backend.ai.safety.SafetyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Orchestrates an AI chat request:
 * <ol>
 *   <li>Safety check (pre-filter)</li>
 *   <li>Load and decrypt the user's API key</li>
 *   <li>Build system prompt (role + goal context)</li>
 *   <li>Reconstruct conversation history as {@link LlmMessage} list</li>
 *   <li>Stream tokens back to the caller via {@link SseEmitter}</li>
 * </ol>
 *
 * <p>Each token is emitted as an SSE event with event name {@code token}.
 * A final {@code done} event is sent when the stream completes.
 * On error, an {@code error} event is sent with a safe message.
 */
@Service
public class AiChatService {

    private static final Logger log = LoggerFactory.getLogger(AiChatService.class);

    /**
     * Role prompt injected at the top of every system prompt.
     * Grounded in the coaching philosophy from the source books.
     */
    private static final String ROLE_PROMPT = """
            You are a coaching intelligence embedded in Spira, a goal achievement platform.

            Your role is not to advise, instruct, or solve problems for the user.
            Your role is to raise awareness and responsibility through focused questioning.

            You listen carefully. You ask one good question at a time.
            You do not give unsolicited advice.
            You do not rush the user toward conclusions.
            You follow the user's thinking, not a predetermined agenda.

            The GROW framework (Goal, Reality, Options, Will) may naturally emerge from
            a session, but you do not announce phases or lead the user through a checklist.

            You communicate in the language the user writes in.
            You are warm, patient, and genuinely curious about this person's situation.

            You are not a therapist, a medical professional, a legal adviser, or a
            financial adviser. If a user's situation requires professional support,
            you acknowledge this honestly and encourage them to seek it.
            """;

    private final SafetyService safety;
    private final AiKeyService keyService;
    private final LlmProviderFactory providerFactory;
    private final GoalContextBuilder goalContextBuilder;

    // Cached thread pool for blocking SSE I/O. Threads are reused between requests.
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public AiChatService(
            SafetyService safety,
            AiKeyService keyService,
            LlmProviderFactory providerFactory,
            GoalContextBuilder goalContextBuilder) {
        this.safety = safety;
        this.keyService = keyService;
        this.providerFactory = providerFactory;
        this.goalContextBuilder = goalContextBuilder;
    }

    /**
     * Starts a streaming chat request and returns an {@link SseEmitter} that
     * the controller will write to the HTTP response.
     *
     * <p>The emitter is completed (or errored) asynchronously; the calling
     * thread returns immediately after submitting the task.
     *
     * @param request the chat request from the frontend
     * @return an SSE emitter that streams tokens as they arrive
     */
    public SseEmitter chat(ChatRequest request) {
        // Safety check runs synchronously before we touch the provider
        if (!safety.isSafe(request.message())) {
            SseEmitter blocked = new SseEmitter(0L);
            try {
                blocked.send(SseEmitter.event()
                        .name("token")
                        .data(safety.blockedMessage()));
                blocked.send(SseEmitter.event().name("done").data(""));
                blocked.complete();
            } catch (Exception ignored) {
                blocked.completeWithError(ignored);
            }
            return blocked;
        }

        // Determine provider
        ProviderType providerType = resolveProvider(request.provider());

        // Load the user's key (throws 422 if not configured)
        AiKeyService.StoredKey storedKey = keyService.getKey(providerType)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "No API key configured for provider " + providerType.name()
                        + ". Save your key at POST /api/ai/keys first."));

        // Build system prompt
        String systemPrompt = buildSystemPrompt(request.goalId());

        // Build message list
        List<LlmMessage> messages = buildMessages(request);

        // Create provider instance
        LlmProvider provider = providerFactory.create(providerType, storedKey.apiKey(), storedKey.model());

        // Create emitter with 3-minute timeout
        SseEmitter emitter = new SseEmitter(3 * 60 * 1000L);

        executor.submit(() -> provider.streamChat(
                messages,
                systemPrompt,
                token -> sendToken(emitter, token),
                () -> completeSse(emitter),
                error -> errorSse(emitter, error)
        ));

        return emitter;
    }

    // ── Internal ─────────────────────────────────────────────────────────────

    private String buildSystemPrompt(Long goalId) {
        String goalContext = goalContextBuilder.build(goalId);
        if (goalContext.isBlank()) return ROLE_PROMPT;
        return ROLE_PROMPT + "\n\n" + goalContext;
    }

    private List<LlmMessage> buildMessages(ChatRequest request) {
        List<LlmMessage> messages = new ArrayList<>();

        // Replay history
        if (request.history() != null) {
            for (ChatRequest.MessageEntry entry : request.history()) {
                messages.add(new LlmMessage(entry.role(), entry.content()));
            }
        }

        // Append current user message
        messages.add(LlmMessage.user(request.message()));

        return messages;
    }

    private ProviderType resolveProvider(String provider) {
        if (provider == null || provider.isBlank()) return ProviderType.ANTHROPIC;
        try {
            return ProviderType.fromString(provider);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unknown provider: " + provider);
        }
    }

    private void sendToken(SseEmitter emitter, String token) {
        try {
            emitter.send(SseEmitter.event().name("token").data(token));
        } catch (Exception e) {
            log.debug("SSE send failed (client likely disconnected): {}", e.getMessage());
            emitter.completeWithError(e);
        }
    }

    private void completeSse(SseEmitter emitter) {
        try {
            emitter.send(SseEmitter.event().name("done").data(""));
            emitter.complete();
        } catch (Exception e) {
            emitter.completeWithError(e);
        }
    }

    private void errorSse(SseEmitter emitter, Throwable error) {
        log.error("AI stream error", error);
        try {
            emitter.send(SseEmitter.event().name("error").data("AI service error. Please try again."));
            emitter.complete();
        } catch (Exception e) {
            emitter.completeWithError(error);
        }
    }
}
