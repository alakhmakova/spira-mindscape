package com.spiramindscape.backend.ai.key.dto;

/** Safe representation of a stored API key — never exposes the key itself. */
public record KeyInfoResponse(
        String provider,
        String hint,
        String model
) {}
