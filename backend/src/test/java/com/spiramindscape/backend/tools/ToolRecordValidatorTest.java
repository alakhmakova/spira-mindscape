package com.spiramindscape.backend.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spiramindscape.backend.tools.ToolRecordValidator.InvalidRecordException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The testable answer to "how do we validate free-form tool data": every record
 * is checked against its tool's approved schema.
 */
class ToolRecordValidatorTest {

    private final ToolRecordValidator validator = new ToolRecordValidator(new ObjectMapper());

    // company:text, applied:date, status:select(applied|interview|offer), count:number, done:checkbox
    private static final String SCHEMA = """
            {"layout":"table","columns":[
              {"key":"company","primitive":"text"},
              {"key":"applied","primitive":"date"},
              {"key":"status","primitive":"select","options":["applied","interview","offer"]},
              {"key":"count","primitive":"number"},
              {"key":"done","primitive":"checkbox"}
            ]}""";

    @Test
    @DisplayName("a well-typed record passes and is canonicalized")
    void valid() {
        String out = validator.validate(SCHEMA,
                "{\"company\":\"Acme\",\"applied\":\"2026-06-14\",\"status\":\"interview\",\"count\":3,\"done\":true}");
        assertThat(out).contains("Acme").contains("interview");
    }

    @Test
    @DisplayName("null cells are allowed (empty fields)")
    void nullsAllowed() {
        assertThatCode(() -> validator.validate(SCHEMA, "{\"company\":null,\"count\":null}"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("an unknown field (not in the schema) is rejected")
    void rejectsUnknownKey() {
        assertThatThrownBy(() -> validator.validate(SCHEMA, "{\"company\":\"Acme\",\"evil\":\"x\"}"))
                .isInstanceOf(InvalidRecordException.class)
                .hasMessageContaining("evil");
    }

    @Test
    @DisplayName("type mismatches are rejected (number, date, checkbox, select option)")
    void rejectsWrongTypes() {
        assertThatThrownBy(() -> validator.validate(SCHEMA, "{\"count\":\"five\"}"))
                .isInstanceOf(InvalidRecordException.class);
        assertThatThrownBy(() -> validator.validate(SCHEMA, "{\"applied\":\"14/06/2026\"}"))
                .isInstanceOf(InvalidRecordException.class);
        assertThatThrownBy(() -> validator.validate(SCHEMA, "{\"done\":\"yes\"}"))
                .isInstanceOf(InvalidRecordException.class);
        assertThatThrownBy(() -> validator.validate(SCHEMA, "{\"status\":\"hired\"}"))
                .isInstanceOf(InvalidRecordException.class)
                .hasMessageContaining("options");
    }

    @Test
    @DisplayName("malformed JSON, non-object, blank, and oversized records are rejected")
    void rejectsJunk() {
        assertThatThrownBy(() -> validator.validate(SCHEMA, "not json"))
                .isInstanceOf(InvalidRecordException.class);
        assertThatThrownBy(() -> validator.validate(SCHEMA, "[1,2,3]"))
                .isInstanceOf(InvalidRecordException.class);
        assertThatThrownBy(() -> validator.validate(SCHEMA, ""))
                .isInstanceOf(InvalidRecordException.class);
        String huge = "{\"company\":\"" + "x".repeat(17 * 1024) + "\"}";
        assertThatThrownBy(() -> validator.validate(SCHEMA, huge))
                .isInstanceOf(InvalidRecordException.class);
    }

    // company:text, applied:date, status:select, count:number, done:checkbox (above)
    // catalog extras: rating, time, tags, url, textarea
    private static final String EXTRAS = """
            {"layout":"table","columns":[
              {"key":"stars","primitive":"rating"},
              {"key":"at","primitive":"time"},
              {"key":"labels","primitive":"tags"},
              {"key":"link","primitive":"url"},
              {"key":"notes","primitive":"textarea"}
            ]}""";

    @Test
    @DisplayName("new catalog primitives accept well-typed values")
    void newPrimitivesValid() {
        assertThatCode(() -> validator.validate(EXTRAS,
                "{\"stars\":4,\"at\":\"09:30\",\"labels\":[\"remote\",\"urgent\"],"
                + "\"link\":\"https://x.com\",\"notes\":\"a long note\"}"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("new catalog primitives reject wrong values")
    void newPrimitivesReject() {
        assertThatThrownBy(() -> validator.validate(EXTRAS, "{\"stars\":9}"))     // >5
                .isInstanceOf(InvalidRecordException.class);
        assertThatThrownBy(() -> validator.validate(EXTRAS, "{\"at\":\"9am\"}"))   // not HH:MM
                .isInstanceOf(InvalidRecordException.class);
        assertThatThrownBy(() -> validator.validate(EXTRAS, "{\"labels\":\"remote\"}")) // not array
                .isInstanceOf(InvalidRecordException.class);
        assertThatThrownBy(() -> validator.validate(EXTRAS, "{\"labels\":[1,2]}"))  // non-text tags
                .isInstanceOf(InvalidRecordException.class);
    }

    @Test
    @DisplayName("a stored '<script>' value is kept as inert text (no HTML/injection handling needed)")
    void scriptValueIsInertText() {
        // It's just a string in a text column — valid data; React escapes it on render.
        String out = validator.validate(SCHEMA, "{\"company\":\"<script>alert(1)</script>\"}");
        assertThat(out).contains("script");
    }
}
