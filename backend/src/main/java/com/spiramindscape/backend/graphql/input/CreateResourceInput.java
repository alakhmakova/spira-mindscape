package com.spiramindscape.backend.graphql.input;

public record CreateResourceInput(
        String title,
        String type,
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
