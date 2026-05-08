package com.spiramindscape.backend.graphql.input;

import java.time.Instant;
import java.util.List;

public record CreateTargetInput(
        String title,
        String type,
        Instant deadline,
        Double start,
        Double current,
        Double total,
        String unit,
        Boolean done,
        List<ChecklistItemInput> items
) {
}
