package com.spiramindscape.backend.resource;

/**
 * A rewrite of a note was based on a version of it that is no longer current — the user (or
 * another device) changed the note after the suggestion was made. Applying it would silently
 * undo that change, so it is refused and the client asks the user to request it again.
 */
public class NoteChangedException extends RuntimeException {

    public NoteChangedException() {
        super("This note changed after the suggestion was made. Ask for the change again.");
    }
}
