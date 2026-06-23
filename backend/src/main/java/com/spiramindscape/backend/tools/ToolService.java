package com.spiramindscape.backend.tools;

import com.spiramindscape.backend.auth.CurrentUserProvider;
import com.spiramindscape.backend.tools.dto.ToolDtos.CreateToolRequest;
import com.spiramindscape.backend.tools.dto.ToolDtos.RecordRequest;
import com.spiramindscape.backend.tools.dto.ToolDtos.UpdateToolRequest;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;

/**
 * Personal Tools CRUD. Every operation is scoped to the signed-in user
 * ({@link CurrentUserProvider}); schemas are validated and limits enforced so a
 * tool (AI- or user-created) can't define unapproved UI or balloon storage.
 */
@Service
@Transactional
public class ToolService {

    static final int MAX_TOOLS_PER_USER = 20;
    static final int MAX_RECORDS_PER_TOOL = 500;
    // Record size is enforced by ToolRecordValidator (16 KB).
    static final Set<String> ALLOWED_PLACEMENTS = Set.of("goal", "all_goals", "tools");
    static final Set<String> ALLOWED_CREATORS = Set.of("ai", "user");

    private final ToolDefinitionRepository tools;
    private final ToolRecordRepository records;
    private final ToolSchemaValidator validator;
    private final ToolRecordValidator recordValidator;
    private final CurrentUserProvider currentUser;

    public ToolService(ToolDefinitionRepository tools, ToolRecordRepository records,
                       ToolSchemaValidator validator, ToolRecordValidator recordValidator,
                       CurrentUserProvider currentUser) {
        this.tools = tools;
        this.records = records;
        this.validator = validator;
        this.recordValidator = recordValidator;
        this.currentUser = currentUser;
    }

    // ── Definitions ─────────────────────────────────────────────────────────

    public ToolDefinition create(CreateToolRequest req) {
        Long userId = currentUserId();
        if (tools.countByAppUserId(userId) >= MAX_TOOLS_PER_USER) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "You've reached the maximum of " + MAX_TOOLS_PER_USER + " tools.");
        }
        if (req.name() == null || req.name().isBlank()) {
            throw badRequest("A tool needs a name.");
        }
        if (req.name().length() > ToolSchemaValidator.MAX_NAME_CHARS) {
            throw badRequest("Tool name is too long.");
        }
        String placement = req.placement() == null ? "tools" : req.placement();
        if (!ALLOWED_PLACEMENTS.contains(placement)) {
            throw badRequest("Unknown placement '" + placement + "'.");
        }
        // Null-safe: an immutable Set's contains(null) throws NPE.
        String createdBy = req.createdBy() != null && ALLOWED_CREATORS.contains(req.createdBy())
                ? req.createdBy() : "user";

        // The security boundary: only approved primitives, within limits.
        String canonicalSchema;
        try {
            canonicalSchema = validator.validate(req.schemaJson());
        } catch (ToolSchemaValidator.InvalidSchemaException e) {
            throw badRequest(e.getMessage());
        }

        ToolDefinition tool = new ToolDefinition();
        tool.setAppUserId(userId);
        tool.setGoalId(req.goalId());
        tool.setName(req.name().trim());
        tool.setSchemaJson(canonicalSchema);
        tool.setPlacement(placement);
        tool.setCreatedBy(createdBy);
        return tools.save(tool);
    }

    public List<ToolDefinition> list(Long goalId) {
        Long userId = currentUserId();
        return goalId == null
                ? tools.findByAppUserIdOrderByCreatedAtDesc(userId)
                : tools.findByAppUserIdAndGoalIdOrderByCreatedAtDesc(userId, goalId);
    }

    public ToolDefinition get(Long id) {
        return owned(id);
    }

    public ToolDefinition update(Long id, UpdateToolRequest req) {
        ToolDefinition tool = owned(id);
        if (req.name() != null && !req.name().isBlank()) {
            if (req.name().length() > ToolSchemaValidator.MAX_NAME_CHARS) {
                throw badRequest("Tool name is too long.");
            }
            tool.setName(req.name().trim());
        }
        if (req.placement() != null) {
            if (!ALLOWED_PLACEMENTS.contains(req.placement())) {
                throw badRequest("Unknown placement '" + req.placement() + "'.");
            }
            tool.setPlacement(req.placement());
        }
        if (req.schemaJson() != null) {
            // Same security boundary as create: only approved primitives, within
            // limits. Existing records are kept; the renderer reads by current
            // column key (extra keys ignored, missing keys show empty).
            try {
                tool.setSchemaJson(validator.validate(req.schemaJson()));
            } catch (ToolSchemaValidator.InvalidSchemaException e) {
                throw badRequest(e.getMessage());
            }
        }
        return tools.save(tool);
    }

    public void delete(Long id) {
        ToolDefinition tool = owned(id); // ownership check
        tools.delete(tool); // tool_records cascade-deleted by FK
    }

    // ── Records ───────────────────────────────────────────────────────────────

    public List<ToolRecord> listRecords(Long toolId) {
        owned(toolId);
        return records.findByToolDefIdOrderByCreatedAtAsc(toolId);
    }

    public ToolRecord addRecord(Long toolId, RecordRequest req) {
        ToolDefinition tool = owned(toolId);
        String data = validatedRecord(tool, req.dataJson());
        if (records.countByToolDefId(toolId) >= MAX_RECORDS_PER_TOOL) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "This tool has reached its record limit (" + MAX_RECORDS_PER_TOOL + ").");
        }
        ToolRecord record = new ToolRecord();
        record.setToolDefId(toolId);
        record.setDataJson(data);
        return records.save(record);
    }

    public ToolRecord updateRecord(Long toolId, Long recordId, RecordRequest req) {
        ToolDefinition tool = owned(toolId);
        String data = validatedRecord(tool, req.dataJson());
        ToolRecord record = records.findByIdAndToolDefId(recordId, toolId)
                .orElseThrow(() -> notFound("Record " + recordId + " not found."));
        record.setDataJson(data);
        return records.save(record);
    }

    public void deleteRecord(Long toolId, Long recordId) {
        owned(toolId);
        ToolRecord record = records.findByIdAndToolDefId(recordId, toolId)
                .orElseThrow(() -> notFound("Record " + recordId + " not found."));
        records.delete(record);
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private ToolDefinition owned(Long id) {
        return tools.findByIdAndAppUserId(id, currentUserId())
                .orElseThrow(() -> notFound("Tool " + id + " not found."));
    }

    /** Validates record data against the tool's approved schema; returns the
     *  canonical JSON to store. Used by BOTH the user and the AI write paths. */
    private String validatedRecord(ToolDefinition tool, String dataJson) {
        try {
            return recordValidator.validate(tool.getSchemaJson(), dataJson);
        } catch (ToolRecordValidator.InvalidRecordException e) {
            throw badRequest(e.getMessage());
        }
    }

    private Long currentUserId() {
        return currentUser.getCurrentUser().getId();
    }

    private static ResponseStatusException badRequest(String msg) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
    }

    private static ResponseStatusException notFound(String msg) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, msg);
    }
}
