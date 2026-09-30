package com.spiramindscape.backend.graphql.input;

/**
 * A section-aware note edit — see {@link com.spiramindscape.backend.resource.NoteEdit}.
 *
 * @param mode              {@code append} (default), {@code append_to_section}, {@code merge_sections},
 *                          {@code replace_section} or {@code replace_all}
 * @param section           the heading an {@code *_section} mode targets
 * @param content           the HTML to add, or the replacement
 * @param title             an optional new title
 * @param expectedUpdatedAt the note's {@code updatedAt} the edit was based on; required by the
 *                          modes that rewrite existing text
 */
public record EditNoteInput(
        String mode,
        String section,
        String content,
        String title,
        String expectedUpdatedAt
) {
}
