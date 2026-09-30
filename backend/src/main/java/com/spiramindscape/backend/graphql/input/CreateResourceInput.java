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
        String phone,
        /** vacancy only: the map's initial document as JSON. Usually null — a map starts empty
         *  and is filled a field at a time through {@code patchVacancyMap}. */
        String mapData
) {

    /**
     * The ten-field form, without {@code mapData} — every caller that predates the vacancy map.
     * A record's components are positional, so adding one would otherwise be a mechanical edit to
     * fifty call sites that have nothing to do with maps.
     */
    public CreateResourceInput(String title, String type, String body, String url, String mime,
                               String dataUrl, String name, String role, String email, String phone) {
        this(title, type, body, url, mime, dataUrl, name, role, email, phone, null);
    }
}
