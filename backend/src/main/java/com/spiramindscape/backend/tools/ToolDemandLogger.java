package com.spiramindscape.backend.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Records "unmet demand" — when the AI tries to build a tool with a primitive
 * (or layout) that isn't in the curated catalog, so the owner can see what to
 * add next from real usage rather than guesswork.
 *
 * <p>Privacy-safe: logs only the unsupported primitive/layout name and the tool
 * name — never the user's message or data. A Cloud Logging filter/alert on
 * {@code tool_unmet_demand} surfaces the most-requested missing primitives.
 */
@Component
public class ToolDemandLogger {

    private static final Logger log = LoggerFactory.getLogger("tools.demand");

    private final ObjectMapper mapper;

    public ToolDemandLogger(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * Inspects a rejected tool schema and logs any primitives/layout that aren't
     * in the catalog. {@code toolName} is a short label only.
     */
    public void recordRejectedSchema(String schemaJson, String toolName, String reason) {
        try {
            JsonNode root = mapper.readTree(schemaJson);
            String layout = root.path("layout").asText("");
            if (!ToolSchemaValidator.ALLOWED_LAYOUTS.contains(layout) && !layout.isBlank()) {
                emit("layout", layout, toolName);
            }
            for (JsonNode col : root.path("columns")) {
                String primitive = col.path("primitive").asText("");
                if (!primitive.isBlank()
                        && !ToolSchemaValidator.ALLOWED_PRIMITIVES.contains(primitive)) {
                    emit("primitive", primitive, toolName);
                }
            }
        } catch (Exception e) {
            // Schema wasn't parseable — still record that a request was unmet.
            log.info("tool_unmet_demand kind=unparseable_schema tool=\"{}\" reason=\"{}\"",
                    safe(toolName), safe(reason));
        }
    }

    private void emit(String kind, String value, String toolName) {
        log.info("tool_unmet_demand kind={} value=\"{}\" tool=\"{}\"",
                kind, safe(value), safe(toolName));
    }

    /** Strip anything that could carry free-form content; keep it a short label. */
    private static String safe(String s) {
        if (s == null) return "";
        String t = s.replaceAll("[\\r\\n\"]", " ").trim();
        return t.length() > 64 ? t.substring(0, 64) : t;
    }
}
