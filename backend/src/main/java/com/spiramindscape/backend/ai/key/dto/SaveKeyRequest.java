package com.spiramindscape.backend.ai.key.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SaveKeyRequest(

        @NotBlank
        @Pattern(
                regexp = "ANTHROPIC|OPENAI|MISTRAL|OLLAMA|TAVILY|anthropic|openai|mistral|ollama|tavily",
                message = "provider must be one of ANTHROPIC, OPENAI, MISTRAL, OLLAMA, TAVILY")
        String provider,

        @NotBlank
        @Size(min = 8, max = 512, message = "apiKey must be between 8 and 512 characters")
        String apiKey,

        /** Optional model override. If omitted the provider's default model is used. */
        String model
) {}
