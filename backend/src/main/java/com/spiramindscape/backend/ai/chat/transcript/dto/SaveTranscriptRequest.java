package com.spiramindscape.backend.ai.chat.transcript.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Upsert a scope's chat transcript. {@code goalId} null = the global chat.
 * {@code content} is the JSON array of messages the client renders; it is bounded
 * so a request can't store an unreasonably large blob (the client already strips
 * attachment file bytes and caps the message count).
 */
public record SaveTranscriptRequest(
        Long goalId,
        @NotNull @Size(max = 400_000, message = "Transcript is too large") String content) {}
