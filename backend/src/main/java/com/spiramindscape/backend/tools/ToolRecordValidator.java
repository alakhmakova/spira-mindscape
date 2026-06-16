package com.spiramindscape.backend.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Validates a tool RECORD against its tool's already-approved SCHEMA.
 *
 * <p>Personal Tools store free-form, user-defined data, so we can't hand-write a
 * fixed DTO per tool. But the schema IS validated/approved
 * ({@link ToolSchemaValidator}), and it can drive validation of every record:
 * only known keys, each value typed to its column primitive, selects limited to
 * their options, bounded size. This makes "free-form" data fully checkable —
 * and unit-testable — closing the gap of unvalidated user-submitted data.
 *
 * <p>This is a data-integrity guard, not an injection guard: records are inert
 * data rendered only through React (auto-escaped) and typed inputs, never as
 * HTML, so there is no XSS/injection surface. Here we enforce shape, types, and
 * size on BOTH the user and the AI write paths.
 */
@Component
public class ToolRecordValidator {

    static final int MAX_RECORD_BYTES = 16 * 1024;

    private final ObjectMapper mapper;

    public ToolRecordValidator(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public static class InvalidRecordException extends RuntimeException {
        public InvalidRecordException(String message) { super(message); }
    }

    /**
     * Validates {@code recordJson} against {@code schemaJson}. Returns the
     * canonical re-serialized record (so we never store malformed text).
     *
     * @throws InvalidRecordException if the record is malformed, too large, has
     *         unknown keys, or a value's type doesn't match its column
     */
    public String validate(String schemaJson, String recordJson) {
        if (recordJson == null || recordJson.isBlank()) {
            throw new InvalidRecordException("Record is empty.");
        }
        if (recordJson.length() > MAX_RECORD_BYTES) {
            throw new InvalidRecordException("Record is too large.");
        }

        JsonNode record;
        try {
            record = mapper.readTree(recordJson);
        } catch (Exception e) {
            throw new InvalidRecordException("Record is not valid JSON.");
        }
        if (!record.isObject()) {
            throw new InvalidRecordException("Record must be a JSON object.");
        }

        Map<String, ColumnSpec> columns = columnsOf(schemaJson);

        record.fieldNames().forEachRemaining(key -> {
            if (!columns.containsKey(key)) {
                throw new InvalidRecordException("Unknown field '" + key + "'.");
            }
        });

        record.fields().forEachRemaining(entry -> {
            String key = entry.getKey();
            JsonNode value = entry.getValue();
            if (value.isNull()) return; // empty cell is fine
            checkType(key, columns.get(key), value);
        });

        try {
            return mapper.writeValueAsString(record);
        } catch (Exception e) {
            throw new InvalidRecordException("Record could not be processed.");
        }
    }

    private record ColumnSpec(String primitive, Set<String> options) {}

    private Map<String, ColumnSpec> columnsOf(String schemaJson) {
        Map<String, ColumnSpec> cols = new HashMap<>();
        try {
            JsonNode columns = mapper.readTree(schemaJson).path("columns");
            for (JsonNode col : columns) {
                String key = col.path("key").asText("");
                String primitive = col.path("primitive").asText("text");
                Set<String> options = new HashSet<>();
                if (col.path("options").isArray()) {
                    col.path("options").forEach(o -> options.add(o.asText()));
                }
                if (!key.isBlank()) cols.put(key, new ColumnSpec(primitive, options));
            }
        } catch (Exception e) {
            throw new InvalidRecordException("The tool's schema is unreadable.");
        }
        return cols;
    }

    private void checkType(String key, ColumnSpec col, JsonNode value) {
        switch (col.primitive()) {
            case "number", "progress" -> require(value.isNumber(), key, "a number");
            case "rating" -> require(
                    value.isNumber() && value.asInt() >= 0 && value.asInt() <= 5,
                    key, "a rating 0–5");
            case "checkbox" -> require(value.isBoolean(), key, "true/false");
            case "date" -> require(
                    value.isTextual() && value.asText().matches("\\d{4}-\\d{2}-\\d{2}"),
                    key, "a date (YYYY-MM-DD)");
            case "time" -> require(
                    value.isTextual() && value.asText().matches("([01]\\d|2[0-3]):[0-5]\\d"),
                    key, "a time (HH:MM)");
            case "url" -> require(value.isTextual(), key, "a link (text)");
            case "select" -> {
                require(value.isTextual(), key, "one of the options");
                if (!value.asText().isEmpty() && !col.options().contains(value.asText())) {
                    throw new InvalidRecordException(
                            "Field '" + key + "' must be one of its options.");
                }
            }
            case "tags" -> {
                require(value.isArray(), key, "a list of tags");
                for (JsonNode item : value) {
                    require(item.isTextual(), key, "text tags");
                }
            }
            case "checklist" -> {
                require(value.isArray(), key, "a checklist");
                for (JsonNode item : value) {
                    require(item.isObject() && item.has("label"), key,
                            "checklist items with a label");
                }
            }
            // text, textarea, table cell, chart → any string/primitive is acceptable
            default -> require(value.isTextual() || value.isNumber() || value.isBoolean(),
                    key, "text");
        }
    }

    private void require(boolean ok, String key, String expected) {
        if (!ok) throw new InvalidRecordException("Field '" + key + "' must be " + expected + ".");
    }
}
