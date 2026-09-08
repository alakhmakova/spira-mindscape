package com.spiramindscape.backend.ai.grow.session.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * One write of the live session.
 *
 * <p>The cap is an abuse stop rather than a real limit: a session's transcript is bounded by the
 * conversation itself, and the client trims what it replays to the model separately.
 */
public record SaveGrowSessionRequest(
        @NotNull Long goalId,
        @Size(max = 400_000, message = "That session is too large to store") String content) {}
