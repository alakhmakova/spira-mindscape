package com.spiramindscape.backend.ai.chat.transcript.dto;

import com.spiramindscape.backend.ai.chat.transcript.AiChatTranscript;

import java.time.Instant;

/**
 * A stored chat transcript returned to the client. {@code content} is the JSON
 * array of messages (empty {@code "[]"} when nothing is stored yet).
 */
public record TranscriptDto(Long goalId, String content, Instant updatedAt) {

    public static TranscriptDto from(AiChatTranscript t) {
        return new TranscriptDto(t.getGoalId(), t.getContent(), t.getUpdatedAt());
    }

    /** An empty transcript for a scope that has nothing stored yet. */
    public static TranscriptDto empty(Long goalId) {
        return new TranscriptDto(goalId, "[]", null);
    }
}
