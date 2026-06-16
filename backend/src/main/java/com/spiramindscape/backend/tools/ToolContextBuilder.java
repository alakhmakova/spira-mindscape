package com.spiramindscape.backend.tools;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Builds the AI-context block describing the user's Personal Tools so the model
 * can fill and edit them. Lists each relevant tool (id, name, schema columns)
 * and its records (id + values), bounded to keep the prompt small.
 *
 * <p>Scope: the current goal's tools + the user's global tools. Records are
 * capped per tool. Runs on the request thread (current user resolved), reusing
 * {@link ToolService}'s ownership-scoped reads.
 */
@Component
public class ToolContextBuilder {

    /** Most recent records shown per tool (older ones omitted to bound the prompt). */
    static final int MAX_RECORDS_SHOWN = 40;

    private final ToolService toolService;

    public ToolContextBuilder(ToolService toolService) {
        this.toolService = toolService;
    }

    /** Returns a context block, or empty string if the user has no tools. */
    public String build(Long goalId) {
        List<ToolDefinition> tools;
        try {
            tools = toolService.list(null); // all the user's tools
        } catch (Exception e) {
            return ""; // tools are optional context — never break chat
        }
        if (tools.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("## PERSONAL TOOLS\n");
        sb.append("The user has these trackers/widgets. To add a row use add_tool_record "
                + "(applies immediately); to change or remove an existing row use "
                + "edit_tool_record / delete_tool_record (the user approves those). "
                + "Record 'data' keys MUST match the tool's column keys and types.\n");
        for (ToolDefinition tool : tools) {
            String scope = tool.getGoalId() == null
                    ? "global"
                    : (goalId != null && goalId.equals(tool.getGoalId()) ? "this goal" : "goal " + tool.getGoalId());
            sb.append("\n- tool id=").append(tool.getId())
              .append(" name=\"").append(tool.getName()).append("\" scope=").append(scope)
              .append("\n  schema: ").append(compact(tool.getSchemaJson()));

            List<ToolRecord> records;
            try {
                records = toolService.listRecords(tool.getId());
            } catch (Exception e) {
                continue;
            }
            int shown = 0;
            for (ToolRecord r : records) {
                if (shown++ >= MAX_RECORDS_SHOWN) {
                    sb.append("\n  …(").append(records.size() - MAX_RECORDS_SHOWN).append(" more rows)");
                    break;
                }
                sb.append("\n  record id=").append(r.getId()).append(": ").append(compact(r.getDataJson()));
            }
            if (records.isEmpty()) sb.append("\n  (no rows yet)");
        }
        return sb.toString();
    }

    /** Collapse JSON whitespace/newlines so each item stays on one line. */
    private static String compact(String json) {
        return json == null ? "" : json.replaceAll("\\s+", " ").trim();
    }
}
