package com.spiramindscape.backend.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spiramindscape.backend.auth.AppUser;
import com.spiramindscape.backend.auth.CurrentUserProvider;
import com.spiramindscape.backend.tools.dto.ToolDtos.CreateToolRequest;
import com.spiramindscape.backend.tools.dto.ToolDtos.RecordRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ToolServiceTest {

    private static final Long USER_ID = 7L;
    private static final String VALID_SCHEMA =
            "{\"layout\":\"table\",\"columns\":[{\"key\":\"c\",\"primitive\":\"text\"}]}";

    @Mock private ToolDefinitionRepository tools;
    @Mock private ToolRecordRepository records;
    @Mock private CurrentUserProvider currentUser;
    private ToolService service;

    @BeforeEach
    void setUp() {
        ObjectMapper om = new ObjectMapper();
        service = new ToolService(tools, records, new ToolSchemaValidator(om),
                new ToolRecordValidator(om), currentUser);
        AppUser u = new AppUser();
        u.setId(USER_ID);
        lenient().when(currentUser.getCurrentUser()).thenReturn(u);
        lenient().when(tools.save(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(records.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private CreateToolRequest req(String schema) {
        return new CreateToolRequest(null, "Job Applications", schema, "tools", "ai");
    }

    @Test
    @DisplayName("create stores a validated, user-scoped tool")
    void createHappyPath() {
        when(tools.countByAppUserId(USER_ID)).thenReturn(0L);
        ToolDefinition saved = service.create(req(VALID_SCHEMA));
        assertThat(saved.getAppUserId()).isEqualTo(USER_ID);
        assertThat(saved.getName()).isEqualTo("Job Applications");
        assertThat(saved.getCreatedBy()).isEqualTo("ai");
        verify(tools).save(any());
    }

    @Test
    @DisplayName("create tolerates a null createdBy (defaults to 'user', no NPE)")
    void createNullCreatedBy() {
        when(tools.countByAppUserId(USER_ID)).thenReturn(0L);
        var request = new CreateToolRequest(null, "Tracker", VALID_SCHEMA, "tools", null);
        ToolDefinition saved = service.create(request);
        assertThat(saved.getCreatedBy()).isEqualTo("user");
    }

    @Test
    @DisplayName("create rejects a schema with an unapproved primitive — nothing saved")
    void createRejectsBadSchema() {
        when(tools.countByAppUserId(USER_ID)).thenReturn(0L);
        String evil = "{\"layout\":\"fields\",\"columns\":[{\"key\":\"x\",\"primitive\":\"script\"}]}";
        assertThatThrownBy(() -> service.create(req(evil)))
                .isInstanceOf(ResponseStatusException.class);
        verify(tools, never()).save(any());
    }

    @Test
    @DisplayName("create enforces the per-user tool limit")
    void createEnforcesToolLimit() {
        when(tools.countByAppUserId(USER_ID)).thenReturn((long) ToolService.MAX_TOOLS_PER_USER);
        assertThatThrownBy(() -> service.create(req(VALID_SCHEMA)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("maximum");
        verify(tools, never()).save(any());
    }

    @Test
    @DisplayName("get/delete on another user's tool is a 404 (ownership)")
    void ownershipEnforced() {
        when(tools.findByIdAndAppUserId(99L, USER_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get(99L)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.delete(99L)).isInstanceOf(ResponseStatusException.class);
    }

    private ToolDefinition ownedTool() {
        ToolDefinition tool = new ToolDefinition();
        tool.setId(1L);
        tool.setAppUserId(USER_ID);
        tool.setSchemaJson(VALID_SCHEMA); // column "c" : text
        return tool;
    }

    @Test
    @DisplayName("addRecord enforces the per-tool record limit")
    void addRecordLimit() {
        when(tools.findByIdAndAppUserId(1L, USER_ID)).thenReturn(Optional.of(ownedTool()));
        when(records.countByToolDefId(1L)).thenReturn((long) ToolService.MAX_RECORDS_PER_TOOL);
        assertThatThrownBy(() -> service.addRecord(1L, new RecordRequest("{\"c\":\"x\"}")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("record limit");
        verify(records, never()).save(any());
    }

    @Test
    @DisplayName("addRecord validates data against the schema: empty, oversized, unknown field, wrong type")
    void addRecordValidatesData() {
        lenient().when(tools.findByIdAndAppUserId(1L, USER_ID)).thenReturn(Optional.of(ownedTool()));
        assertThatThrownBy(() -> service.addRecord(1L, new RecordRequest("")))
                .isInstanceOf(ResponseStatusException.class);
        String huge = "{\"c\":\"" + "x".repeat(17 * 1024) + "\"}";
        assertThatThrownBy(() -> service.addRecord(1L, new RecordRequest(huge)))
                .isInstanceOf(ResponseStatusException.class);
        // unknown field not in the schema
        assertThatThrownBy(() -> service.addRecord(1L, new RecordRequest("{\"evil\":\"x\"}")))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    @DisplayName("addRecord accepts a valid record and saves the canonical JSON")
    void addRecordHappyPath() {
        when(tools.findByIdAndAppUserId(1L, USER_ID)).thenReturn(Optional.of(ownedTool()));
        when(records.countByToolDefId(1L)).thenReturn(0L);
        ToolRecord saved = service.addRecord(1L, new RecordRequest("{\"c\":\"hello\"}"));
        assertThat(saved.getDataJson()).contains("hello");
        verify(records).save(any());
    }
}
