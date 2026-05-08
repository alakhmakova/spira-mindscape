package com.spiramindscape.backend.graphql.input;

public record UpdateResourceInput(
        String title,
        String body,
        String url,
        String mime,
        String dataUrl,
        String name,
        String role,
        String email,
        String phone
) {
}
