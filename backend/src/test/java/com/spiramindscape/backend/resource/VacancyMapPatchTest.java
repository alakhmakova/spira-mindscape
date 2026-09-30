package com.spiramindscape.backend.resource;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The guarantee this class exists for: a patch changes the field it names and NOTHING else. Every
 * test that asserts an untouched neighbour survives is testing the defect that made the
 * requirement-map note unusable — a second writer rewriting the whole document.
 */
class VacancyMapPatchTest {

    private static String apply(String document, VacancyMapPatch.Patch... patches) {
        return VacancyMapPatch.apply(document, List.of(patches));
    }

    private static VacancyMapPatch.Patch set(String path, String json) {
        return new VacancyMapPatch.Patch(path, json);
    }

    private static VacancyMapPatch.Patch remove(String path) {
        return new VacancyMapPatch.Patch(path, null);
    }

    @Test
    @DisplayName("an empty document normalises to an empty object")
    void normalisesBlankDocument() {
        assertThat(VacancyMapPatch.apply(null, List.of())).isEqualTo("{}");
        assertThat(VacancyMapPatch.apply("", List.of())).isEqualTo("{}");
        assertThat(VacancyMapPatch.apply("   ", List.of())).isEqualTo("{}");
    }

    @Test
    @DisplayName("writes one field into a document that has none")
    void createsMissingContainers() {
        String result = apply(null, set("/facts/location", "\"Stockholm\""));

        assertThat(result).isEqualTo("{\"facts\":{\"location\":\"Stockholm\"}}");
    }

    @Test
    @DisplayName("writing one field leaves every other field exactly as it was")
    void leavesSiblingsAlone() {
        String before = "{\"facts\":{\"location\":\"Stockholm\",\"language\":\"Swedish\"},"
                + "\"skills\":[{\"id\":\"a\",\"text\":\"Java\"}]}";

        String after = apply(before, set("/facts/location", "\"Malmö\""));

        assertThat(after).contains("\"location\":\"Malmö\"");
        // The two the patch did not name.
        assertThat(after).contains("\"language\":\"Swedish\"");
        assertThat(after).contains("\"text\":\"Java\"");
    }

    @Test
    @DisplayName("a trailing '-' appends to an array")
    void appendsToArray() {
        String after = apply("{\"skills\":[{\"id\":\"a\"}]}",
                set("/skills/-", "{\"id\":\"b\",\"text\":\"CI/CD\"}"));

        assertThat(after).isEqualTo("{\"skills\":[{\"id\":\"a\"},{\"id\":\"b\",\"text\":\"CI/CD\"}]}");
    }

    @Test
    @DisplayName("appending works on a document with no such array yet")
    void appendsCreatingArray() {
        String after = apply("{}", set("/skills/-", "{\"id\":\"a\"}"));

        assertThat(after).isEqualTo("{\"skills\":[{\"id\":\"a\"}]}");
    }

    @Test
    @DisplayName("a null value removes an object field")
    void removesObjectField() {
        String after = apply("{\"facts\":{\"location\":\"Stockholm\",\"link\":\"x\"}}",
                remove("/facts/link"));

        assertThat(after).isEqualTo("{\"facts\":{\"location\":\"Stockholm\"}}");
    }

    @Test
    @DisplayName("a null value splices an array element out — how an item is deleted")
    void removesArrayElement() {
        String after = apply("{\"skills\":[{\"id\":\"a\"},{\"id\":\"b\"},{\"id\":\"c\"}]}",
                remove("/skills/1"));

        assertThat(after).isEqualTo("{\"skills\":[{\"id\":\"a\"},{\"id\":\"c\"}]}");
    }

    @Test
    @DisplayName("writes deep into the requirement x company matrix")
    void writesNestedArrayField() {
        String before = "{\"requirements\":[{\"id\":\"r1\",\"companies\":"
                + "[{\"id\":\"c1\",\"text\":\"\"},{\"id\":\"c2\",\"text\":\"\"}]}]}";

        String after = apply(before,
                set("/requirements/0/companies/1/text", "\"Ran the migration\""));

        assertThat(after).contains("\"id\":\"c2\",\"text\":\"Ran the migration\"");
        assertThat(after).contains("\"id\":\"c1\",\"text\":\"\"");
    }

    @Test
    @DisplayName("several patches apply in order, in one write")
    void appliesPatchesInOrder() {
        String after = apply("{}",
                set("/facts/location", "\"Stockholm\""),
                set("/facts/language", "\"Swedish\""),
                set("/company/name", "\"Advania\""));

        assertThat(after).contains("\"location\":\"Stockholm\"");
        assertThat(after).contains("\"language\":\"Swedish\"");
        assertThat(after).contains("\"name\":\"Advania\"");
    }

    @Test
    @DisplayName("a boolean and a number are written as themselves, not as strings")
    void writesNonStringValues() {
        String after = apply("{\"requirements\":[{\"id\":\"r1\"}]}",
                set("/requirements/0/important", "true"));

        assertThat(after).isEqualTo("{\"requirements\":[{\"id\":\"r1\",\"important\":true}]}");
    }

    @Test
    @DisplayName("setting past the end of an array appends rather than leaving a hole")
    void setPastEndAppends() {
        String after = apply("{\"skills\":[{\"id\":\"a\"}]}", set("/skills/7", "{\"id\":\"b\"}"));

        assertThat(after).isEqualTo("{\"skills\":[{\"id\":\"a\"},{\"id\":\"b\"}]}");
    }

    @Test
    @DisplayName("JSON Pointer escapes are decoded, ~1 before ~0")
    void decodesPointerEscapes() {
        String after = apply("{}", set("/facts/a~1b", "\"slash\""));

        assertThat(after).isEqualTo("{\"facts\":{\"a/b\":\"slash\"}}");
    }

    @Test
    @DisplayName("a corrupt stored document is refused rather than silently replaced")
    void rejectsCorruptDocument() {
        assertThatThrownBy(() -> apply("{not json", set("/facts/location", "\"x\"")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not valid JSON");
    }

    @Test
    @DisplayName("a patch value that is not JSON is refused")
    void rejectsInvalidValue() {
        assertThatThrownBy(() -> apply("{}", set("/facts/location", "Stockholm")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not valid JSON");
    }

    @Test
    @DisplayName("a malformed path is refused")
    void rejectsMalformedPath() {
        assertThatThrownBy(() -> apply("{}", set("facts/location", "\"x\"")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must start with '/'");

        assertThatThrownBy(() -> apply("{}", set("/facts//location", "\"x\"")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("empty segment");
    }

    @Test
    @DisplayName("a path running through a scalar is refused, not silently ignored")
    void rejectsPathThroughValue() {
        assertThatThrownBy(() -> apply("{\"facts\":{\"location\":\"Stockholm\"}}",
                set("/facts/location/city", "\"x\"")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not a container");
    }
}
