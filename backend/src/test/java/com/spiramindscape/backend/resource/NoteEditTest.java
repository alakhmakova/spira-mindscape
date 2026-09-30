package com.spiramindscape.backend.resource;

import com.spiramindscape.backend.resource.NoteEdit.Mode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NoteEditTest {

    private static final String PROFILE = "<h2>Contact</h2><p>Anna, Malmö</p>"
            + "<h2>Experience</h2><p>Spira — Java developer</p><ul><li>Spring Boot</li><li>PostgreSQL</li></ul>"
            + "<h3>Details</h3><p>GraphQL API</p>"
            + "<h2>Languages</h2><p>Swedish, English</p>";

    @Test
    @DisplayName("append keeps the note byte-for-byte and adds only what is new")
    void appendKeepsTheNote() {
        String hand = "<p>My own <strong>edit</strong>&nbsp;here</p>";
        String result = NoteEdit.apply(hand, Mode.APPEND, null, "<p>My own edit here</p><p>New line</p>");

        assertThat(result).startsWith(hand).endsWith("<p>New line</p>");
        assertThat(result.split("My own", -1)).hasSize(2);
    }

    @Test
    @DisplayName("a model sending the whole note back with one new line adds that one line")
    void appendOfTheWholeNoteAddsOnlyTheDifference() {
        String result = NoteEdit.apply(PROFILE, Mode.APPEND, null, PROFILE + "<p>Kotlin</p>");

        assertThat(result).isEqualTo(PROFILE + "<p>Kotlin</p>");
    }

    @Test
    @DisplayName("nothing new means nothing changes")
    void appendOfKnownContentIsANoOp() {
        assertThat(NoteEdit.apply(PROFILE, Mode.APPEND, null, "<p>Swedish,   English</p>")).isEqualTo(PROFILE);
    }

    @Test
    @DisplayName("append_to_section lands before the next heading of the same level, after sub-sections")
    void appendToSection() {
        String result = NoteEdit.apply(PROFILE, Mode.APPEND_TO_SECTION, "Experience:", "<p>Kotlin on Android</p>");

        assertThat(result.indexOf("Kotlin on Android"))
                .isGreaterThan(result.indexOf("GraphQL API"))
                .isLessThan(result.indexOf("<h2>Languages</h2>"));
        assertThat(result).contains("<p>Anna, Malmö</p>");
    }

    @Test
    @DisplayName("list items already in the section are not repeated")
    void listItemsDeduplicated() {
        String result = NoteEdit.apply(PROFILE, Mode.APPEND_TO_SECTION, "Experience",
                "<h2>Experience</h2><ul><li>Spring Boot</li><li>Flyway</li></ul>");

        assertThat(result).contains("<ul><li>Flyway</li></ul>");
        assertThat(result.split("Spring Boot", -1)).hasSize(2);
        assertThat(result.split("<h2>Experience</h2>", -1)).hasSize(2);
    }

    @Test
    @DisplayName("a missing section is created at the end")
    void missingSectionIsCreated() {
        String result = NoteEdit.apply(PROFILE, Mode.APPEND_TO_SECTION, "Projects", "<p>spira-mindscape</p>");

        assertThat(result).startsWith(PROFILE).endsWith("<h2>Projects</h2><p>spira-mindscape</p>");
    }

    @Test
    @DisplayName("replace_section changes that section only")
    void replaceSection() {
        String result = NoteEdit.apply(PROFILE, Mode.REPLACE_SECTION, "Languages", "<p>Swedish (C1), English (C2)</p>");

        assertThat(result).contains("<h2>Languages</h2><p>Swedish (C1), English (C2)</p>")
                .doesNotContain("<p>Swedish, English</p>")
                .contains("<p>Spira — Java developer</p>", "<p>GraphQL API</p>");
    }

    @Test
    @DisplayName("merge_sections adds each part to its own section")
    void mergeSections() {
        String result = NoteEdit.apply(PROFILE, Mode.MERGE_SECTIONS, null,
                "<h2>Languages</h2><p>Russian</p><h2>Courses</h2><p>AWS basics</p>");

        assertThat(result.indexOf("<p>Russian</p>")).isGreaterThan(result.indexOf("<h2>Languages</h2>"));
        assertThat(result).endsWith("<h2>Courses</h2><p>AWS basics</p>");
        assertThat(result).contains("<p>Anna, Malmö</p>", "<p>Swedish, English</p>");
    }

    @Test
    @DisplayName("the diff names what is added and what is removed")
    void diff() {
        String after = NoteEdit.apply(PROFILE, Mode.REPLACE_SECTION, "Languages", "<p>Swedish (C1)</p>");
        NoteEdit.Diff d = NoteEdit.diff(PROFILE, after);

        assertThat(d.added()).containsExactly("swedish (c1)");
        assertThat(d.removed()).containsExactly("swedish, english");
        assertThat(NoteEdit.diff(PROFILE, PROFILE).isEmpty()).isTrue();
    }

    @Test
    @DisplayName("sections are listed as written")
    void sections() {
        assertThat(NoteEdit.sections(PROFILE)).containsExactly("Contact", "Experience", "Details", "Languages");
    }

    @Test
    @DisplayName("modes parse from the wire form; blank is append; unknown is refused")
    void modeParsing() {
        assertThat(Mode.parse(null)).isEqualTo(Mode.APPEND);
        assertThat(Mode.parse("append-to-section")).isEqualTo(Mode.APPEND_TO_SECTION);
        assertThat(Mode.parse("REPLACE_ALL").rewritesExisting()).isTrue();
        assertThat(Mode.APPEND_TO_SECTION.rewritesExisting()).isFalse();
        assertThatThrownBy(() -> Mode.parse("overwrite")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("malformed HTML does not throw")
    void malformed() {
        String result = NoteEdit.apply("<p>open <b>bold", Mode.APPEND, null, "<li>stray");
        assertThat(result).contains("open").contains("stray");
    }
}
