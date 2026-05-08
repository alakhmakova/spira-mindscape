package com.spiramindscape.backend.graphql.input;

import java.time.Instant;
import java.util.List;

public record UpdateTargetInput(
        String title,
        Instant deadline,
        Instant achievedAt,
        Double start,
        Double current,
        Double total,
        String unit,
        Boolean done,
        List<ChecklistItemInput> items
) {
}
