package com.spiramindscape.backend.ai.provider;

/**
 * An image to show the model (vision). Carried on an {@link LlmMessage} so a
 * provider can serialize it into that provider's multimodal wire format.
 *
 * @param mediaType the image MIME type (e.g. {@code image/png}, {@code image/jpeg})
 * @param base64Data the raw base64 payload (NO {@code data:...;base64,} prefix)
 */
public record LlmImage(String mediaType, String base64Data) {}
