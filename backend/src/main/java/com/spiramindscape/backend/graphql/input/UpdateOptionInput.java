package com.spiramindscape.backend.graphql.input;

public record UpdateOptionInput(
        String text,
        Boolean selected,
        // "none" | "active" | "good_idea" | "didnt_work" — mutually exclusive.
        String status
) {
}
