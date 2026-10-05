package com.spiramindscape.backend.ai.provider;

import java.util.List;
import java.util.function.Consumer;

/**
 * Provider-agnostic interface for streaming chat completions.
 *
 * <p>Implementations must be stateless and thread-safe. A new instance is
 * constructed per request (via {@code LlmProviderFactory}) and carries only
 * the API key and model choice for that request.
 *
 * <p>Streaming contract:
 * <ul>
 *   <li>{@code onToken} — called once per text token/chunk as it arrives</li>
 *   <li>{@code onComplete} — called exactly once when the stream ends normally</li>
 *   <li>{@code onError} — called exactly once if a non-recoverable error occurs;
 *       {@code onComplete} is NOT called in that case</li>
 * </ul>
 */
public interface LlmProvider {

    /**
     * Stream a chat completion. Blocks the calling thread until the stream
     * finishes (or fails). Should be called from a virtual thread or task
     * executor, not from the request thread.
     *
     * @param tools      tools the model may call; pass an empty list to disable
     * @param onToolCall invoked once per completed tool call with the full
     *                   arguments JSON assembled from streamed deltas
     */
    void streamChat(
            List<LlmMessage> messages,
            String systemPrompt,
            List<ToolSpec> tools,
            Consumer<String> onToken,
            Consumer<ToolCall> onToolCall,
            Runnable onComplete,
            Consumer<Throwable> onError
    );

    ProviderType providerType();

    /**
     * The model this instance will actually call — the caller's choice when they made one,
     * the provider's own default when they did not.
     *
     * <p>It exists so a failure can be logged with the model that produced it. Without it a
     * provider error is a status and a sentence with no way to tell which model refused:
     * chasing "Rate limit exceeded" through Cloud Run on 30 Aug 2026 could establish the
     * provider and the hour but not the model, which was the one fact that mattered.
     */
    String model();
}
