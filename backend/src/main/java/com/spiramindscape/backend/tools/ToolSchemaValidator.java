package com.spiramindscape.backend.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Validates a Personal Tool schema before it is stored. The AI (or a user)
 * never gets to define arbitrary UI — a schema may only compose the approved
 * primitives, and must stay within size/field limits that bound storage and
 * prompt size. This is the security boundary for the feature: untrusted schema
 * JSON in, a yes/no (with reason) out.
 *
 * <p>Schema shape (see docs/ai-mini-apps-plan.md §3):
 * <pre>
 * { "layout": "table" | "fields",
 *   "columns": [ { "key", "label", "primitive", "options"? } ] }
 * </pre>
 * ({@code fields} is a single-record form; {@code table} is many rows. Both use
 * the same {@code columns} field definitions.)
 */
@Component
public class ToolSchemaValidator {

    /** The ONLY primitives a tool may use — the curated catalog. Growing this
     *  set (with matching checks in ToolRecordValidator + a renderer case) is
     *  how the catalog expands; the AI can never use anything outside it. */
    public static final Set<String> ALLOWED_PRIMITIVES = Set.of(
            "number", "text", "textarea", "date", "time", "checkbox", "checklist",
            "select", "tags", "rating", "url", "table", "progress", "chart");

    static final Set<String> ALLOWED_LAYOUTS = Set.of("table", "fields");

    static final int MAX_NAME_CHARS = 120;
    static final int MAX_FIELDS = 12;
    static final int MAX_SCHEMA_BYTES = 8 * 1024;
    static final int MAX_OPTIONS = 30;

    private final ObjectMapper mapper;

    public ToolSchemaValidator(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /** Thrown when a schema is invalid; the message is safe to show the user. */
    public static class InvalidSchemaException extends RuntimeException {
        public InvalidSchemaException(String message) { super(message); }
    }

    /**
     * Validates the raw schema JSON. Returns the re-serialized, canonical JSON
     * (parsed and written back, so we never store malformed/oversized junk).
     *
     * @throws InvalidSchemaException if anything is off
     */
    public String validate(String schemaJson) {
        if (schemaJson == null || schemaJson.isBlank()) {
            throw new InvalidSchemaException("Tool schema is empty.");
        }
        if (schemaJson.length() > MAX_SCHEMA_BYTES) {
            throw new InvalidSchemaException("Tool schema is too large.");
        }
        JsonNode root;
        try {
            root = mapper.readTree(schemaJson);
        } catch (Exception e) {
            throw new InvalidSchemaException("Tool schema is not valid JSON.");
        }
        if (!root.isObject()) {
            throw new InvalidSchemaException("Tool schema must be a JSON object.");
        }

        String layout = root.path("layout").asText("");
        if (!ALLOWED_LAYOUTS.contains(layout)) {
            throw new InvalidSchemaException(
                    "Unsupported layout '" + layout + "'. Use 'table' or 'fields'.");
        }

        JsonNode columns = root.path("columns");
        if (!columns.isArray() || columns.isEmpty()) {
            throw new InvalidSchemaException("A tool needs at least one field.");
        }
        if (columns.size() > MAX_FIELDS) {
            throw new InvalidSchemaException(
                    "Too many fields (max " + MAX_FIELDS + ").");
        }

        for (JsonNode col : columns) {
            String key = col.path("key").asText("");
            String primitive = col.path("primitive").asText("");
            if (key.isBlank()) {
                throw new InvalidSchemaException("Every field needs a 'key'.");
            }
            if (!ALLOWED_PRIMITIVES.contains(primitive)) {
                throw new InvalidSchemaException(
                        "Unsupported field type '" + primitive + "'.");
            }
            if ("select".equals(primitive)) {
                JsonNode options = col.path("options");
                if (!options.isArray() || options.isEmpty()) {
                    throw new InvalidSchemaException(
                            "A 'select' field needs a non-empty 'options' list.");
                }
                if (options.size() > MAX_OPTIONS) {
                    throw new InvalidSchemaException(
                            "Too many options on field '" + key + "'.");
                }
            }
        }

        try {
            return mapper.writeValueAsString(root); // canonical re-serialization
        } catch (Exception e) {
            throw new InvalidSchemaException("Tool schema could not be processed.");
        }
    }
}
