package com.spiramindscape.backend.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spiramindscape.backend.tools.ToolSchemaValidator.InvalidSchemaException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

class ToolSchemaValidatorTest {

    private final ToolSchemaValidator validator = new ToolSchemaValidator(new ObjectMapper());

    private static final String JOB_TRACKER = """
            {"layout":"table","columns":[
              {"key":"company","label":"Company","primitive":"text"},
              {"key":"applied","label":"Applied","primitive":"date"},
              {"key":"status","label":"Status","primitive":"select",
               "options":["applied","interview","offer","rejected"]}
            ]}""";

    @Test
    @DisplayName("a valid job-tracker schema passes and is re-serialized")
    void validSchema() {
        String out = validator.validate(JOB_TRACKER);
        assertThat(out).contains("company").contains("select");
    }

    @Test
    @DisplayName("each approved primitive is accepted")
    void approvedPrimitives() {
        for (String p : ToolSchemaValidator.ALLOWED_PRIMITIVES) {
            String opts = p.equals("select") ? ",\"options\":[\"a\",\"b\"]" : "";
            String schema = "{\"layout\":\"fields\",\"columns\":[{\"key\":\"f\",\"primitive\":\""
                    + p + "\"" + opts + "}]}";
            assertThatCode(() -> validator.validate(schema))
                    .as("primitive " + p).doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("an unknown primitive is rejected (the security boundary)")
    void rejectsUnknownPrimitive() {
        String schema = "{\"layout\":\"fields\",\"columns\":[{\"key\":\"x\",\"primitive\":\"iframe\"}]}";
        assertThatThrownBy(() -> validator.validate(schema))
                .isInstanceOf(InvalidSchemaException.class)
                .hasMessageContaining("iframe");
    }

    @Test
    @DisplayName("an unknown layout is rejected")
    void rejectsBadLayout() {
        String schema = "{\"layout\":\"webgl\",\"columns\":[{\"key\":\"x\",\"primitive\":\"text\"}]}";
        assertThatThrownBy(() -> validator.validate(schema)).isInstanceOf(InvalidSchemaException.class);
    }

    @Test
    @DisplayName("empty or missing columns are rejected")
    void rejectsNoColumns() {
        assertThatThrownBy(() -> validator.validate("{\"layout\":\"table\",\"columns\":[]}"))
                .isInstanceOf(InvalidSchemaException.class);
        assertThatThrownBy(() -> validator.validate("{\"layout\":\"table\"}"))
                .isInstanceOf(InvalidSchemaException.class);
    }

    @Test
    @DisplayName("a select field without options is rejected")
    void rejectsSelectWithoutOptions() {
        String schema = "{\"layout\":\"fields\",\"columns\":[{\"key\":\"s\",\"primitive\":\"select\"}]}";
        assertThatThrownBy(() -> validator.validate(schema))
                .isInstanceOf(InvalidSchemaException.class)
                .hasMessageContaining("options");
    }

    @Test
    @DisplayName("too many fields is rejected")
    void rejectsTooManyFields() {
        StringBuilder cols = new StringBuilder();
        for (int i = 0; i <= ToolSchemaValidator.MAX_FIELDS; i++) {
            if (i > 0) cols.append(',');
            cols.append("{\"key\":\"f").append(i).append("\",\"primitive\":\"text\"}");
        }
        String schema = "{\"layout\":\"table\",\"columns\":[" + cols + "]}";
        assertThatThrownBy(() -> validator.validate(schema))
                .isInstanceOf(InvalidSchemaException.class)
                .hasMessageContaining("Too many fields");
    }

    @Test
    @DisplayName("malformed JSON, blank, and oversized schemas are rejected")
    void rejectsJunk() {
        assertThatThrownBy(() -> validator.validate("not json")).isInstanceOf(InvalidSchemaException.class);
        assertThatThrownBy(() -> validator.validate("")).isInstanceOf(InvalidSchemaException.class);
        assertThatThrownBy(() -> validator.validate(null)).isInstanceOf(InvalidSchemaException.class);
        String huge = "{\"layout\":\"table\",\"columns\":[{\"key\":\"a\",\"primitive\":\"text\",\"label\":\""
                + "x".repeat(ToolSchemaValidator.MAX_SCHEMA_BYTES) + "\"}]}";
        assertThatThrownBy(() -> validator.validate(huge)).isInstanceOf(InvalidSchemaException.class);
    }
}
