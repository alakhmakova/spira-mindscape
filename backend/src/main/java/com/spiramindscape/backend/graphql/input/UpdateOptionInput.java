package com.spiramindscape.backend.graphql.input;

public record UpdateOptionInput(
        String text,
        // The "active" radio, independent of `status`.
        Boolean selected,
        // The thumb lean: "none" | "good_idea" | "didnt_work".
        String status
) {
}
