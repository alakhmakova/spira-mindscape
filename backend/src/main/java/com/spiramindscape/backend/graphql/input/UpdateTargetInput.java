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
        Boolean progressLocked,
        List<ChecklistItemInput> items
) {
    /** Without a lock choice — the common case, and what every caller predating the lock means. */
    public UpdateTargetInput(String title, Instant deadline, Instant achievedAt, Double start,
                             Double current, Double total, String unit, Boolean done,
                             List<ChecklistItemInput> items) {
        this(title, deadline, achievedAt, start, current, total, unit, done, null, items);
    }
}
