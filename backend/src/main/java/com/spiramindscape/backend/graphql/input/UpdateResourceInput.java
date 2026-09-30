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
        String phone,
        /** vacancy only, and rarely: replacing a map WHOLESALE is what {@code patchVacancyMap}
         *  exists to avoid. Kept for duplicating a map, where the whole document is the point. */
        String mapData
) {

    /** The nine-field form, without {@code mapData} — see {@link CreateResourceInput}. */
    public UpdateResourceInput(String title, String body, String url, String mime, String dataUrl,
                               String name, String role, String email, String phone) {
        this(title, body, url, mime, dataUrl, name, role, email, phone, null);
    }
}
