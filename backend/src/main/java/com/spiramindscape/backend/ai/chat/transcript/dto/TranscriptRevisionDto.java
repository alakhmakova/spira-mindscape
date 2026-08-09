package com.spiramindscape.backend.ai.chat.transcript.dto;

import java.time.Instant;

/**
 * The change-signature of a stored chat transcript: when it last changed, and nothing else.
 * {@code updatedAt} is null when the scope has no transcript yet.
 *
 * <p>The chat panel polls this instead of {@link TranscriptDto} so an idle-but-open conversation
 * transfers a timestamp rather than its whole message history every tick — the transcript's
 * counterpart to the {@code goalsRevision} query.
 */
public record TranscriptRevisionDto(Long goalId, Instant updatedAt) {}
