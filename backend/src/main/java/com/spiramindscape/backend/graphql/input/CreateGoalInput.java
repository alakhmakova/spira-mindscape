package com.spiramindscape.backend.graphql.input;

import java.time.Instant;

public record CreateGoalInput(
        String title,
        String description,
        Integer confidence,
        Instant deadline
) {
}
