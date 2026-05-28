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
     */
    void streamChat(
            List<LlmMessage> messages,
            String systemPrompt,
            Consumer<String> onToken,
            Runnable onComplete,
            Consumer<Throwable> onError
    );

    ProviderType providerType();
}
