package com.spiramindscape.backend.tools.dto;

import com.spiramindscape.backend.tools.ToolDefinition;
import com.spiramindscape.backend.tools.ToolRecord;

import java.time.Instant;

/** Request/response shapes for the Personal Tools API. */
public final class ToolDtos {

    private ToolDtos() {}

    /** Create a tool (from an approved AI proposal or manually). {@code renderCode}
     *  is optional AI-written sandbox render code (null = schema-rendered). */
    public record CreateToolRequest(Long goalId, String name, String schemaJson,
                                    String placement, String createdBy, String renderCode) {}

    /** Nullable fields leave that attribute untouched (partial update). A
     *  non-null {@code schemaJson} replaces the structure; a non-null
     *  {@code renderCode} replaces the render code (empty string clears it). */
    public record UpdateToolRequest(String name, String placement, String schemaJson,
                                    String renderCode) {}

    public record ToolResponse(Long id, Long goalId, String name, String schemaJson,
                               String placement, String createdBy, String renderCode,
                               Instant createdAt) {
        public static ToolResponse from(ToolDefinition d) {
            return new ToolResponse(d.getId(), d.getGoalId(), d.getName(), d.getSchemaJson(),
                    d.getPlacement(), d.getCreatedBy(), d.getRenderCode(), d.getCreatedAt());
        }
    }

    public record RecordRequest(String dataJson) {}

    public record RecordResponse(Long id, Long toolDefId, String dataJson,
                                 Instant createdAt, Instant updatedAt) {
        public static RecordResponse from(ToolRecord r) {
            return new RecordResponse(r.getId(), r.getToolDefId(), r.getDataJson(),
                    r.getCreatedAt(), r.getUpdatedAt());
        }
    }
}
