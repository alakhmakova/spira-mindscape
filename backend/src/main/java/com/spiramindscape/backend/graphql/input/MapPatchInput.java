package com.spiramindscape.backend.graphql.input;

/**
 * One field-level edit to a vacancy map — see
 * {@code com.spiramindscape.backend.resource.VacancyMapPatch} for the path syntax and for why the
 * map is written a field at a time rather than as a whole document.
 *
 * @param path  JSON Pointer to the field, e.g. {@code /facts/location} or {@code /skills/-}
 * @param value the new value as JSON; {@code null} removes the field
 */
public record MapPatchInput(String path, String value) {
}
