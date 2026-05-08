package com.spiramindscape.backend.graphql.input;

import java.time.Instant;

public record UpdateGoalInput(
        String title,
        String description,
        Integer confidence,
        Instant deadline,
        Instant achievedAt
) {
}
