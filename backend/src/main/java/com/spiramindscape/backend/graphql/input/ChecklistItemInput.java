package com.spiramindscape.backend.graphql.input;

import java.time.Instant;

public record ChecklistItemInput(
        String id,
        String text,
        Boolean done,
        Instant deadline,
        Instant achievedAt
) {
}
