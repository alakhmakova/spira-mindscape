package com.spiramindscape.backend.ai.grow.session.dto;

import com.spiramindscape.backend.ai.grow.session.GrowSession;

import java.time.Instant;

/**
 * A live session as the client sees it. {@code content} is null when there is nothing stored,
 * which is how a client tells "no session here" from "a session with an empty transcript".
 */
public record GrowSessionDto(Long goalId, String content, Instant updatedAt) {

    public static GrowSessionDto from(GrowSession session) {
        return new GrowSessionDto(session.getGoalId(), session.getContent(), session.getUpdatedAt());
    }

    public static GrowSessionDto empty(Long goalId) {
        return new GrowSessionDto(goalId, null, null);
    }
}
