package com.spiramindscape.backend.graphql.input;

import java.time.Instant;

/**
 * {@code kind = important} takes {@code title} and {@code date}; {@code goal} takes {@code goalId},
 * {@code target} takes {@code targetId}, {@code task} takes {@code taskId} (a checklist item).
 */
public record CreateUpcomingEventInput(
        String kind,
        String title,
        Instant date,
        Long goalId,
        Long targetId,
        Long taskId
) {
}
