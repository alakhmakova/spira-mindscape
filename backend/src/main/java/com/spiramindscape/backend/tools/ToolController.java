package com.spiramindscape.backend.tools;

import com.spiramindscape.backend.tools.dto.ToolDtos.CreateToolRequest;
import com.spiramindscape.backend.tools.dto.ToolDtos.RecordRequest;
import com.spiramindscape.backend.tools.dto.ToolDtos.RecordResponse;
import com.spiramindscape.backend.tools.dto.ToolDtos.ToolResponse;
import com.spiramindscape.backend.tools.dto.ToolDtos.UpdateToolRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST surface for Personal Tools (AI mini-apps). All endpoints are
 * user-scoped by {@link ToolService}; see docs/ai-mini-apps-plan.md §6.
 */
@RestController
@RequestMapping("/api/tools")
public class ToolController {

    private final ToolService service;

    public ToolController(ToolService service) {
        this.service = service;
    }

    @PostMapping
    public ToolResponse create(@RequestBody CreateToolRequest req) {
        return ToolResponse.from(service.create(req));
    }

    @GetMapping
    public List<ToolResponse> list(@RequestParam(required = false) Long goalId) {
        return service.list(goalId).stream().map(ToolResponse::from).toList();
    }

    @GetMapping("/{id}")
    public ToolResponse get(@PathVariable Long id) {
        return ToolResponse.from(service.get(id));
    }

    @PatchMapping("/{id}")
    public ToolResponse update(@PathVariable Long id, @RequestBody UpdateToolRequest req) {
        return ToolResponse.from(service.update(id, req));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.ok(Map.of("status", "deleted"));
    }

    @GetMapping("/{id}/records")
    public List<RecordResponse> listRecords(@PathVariable Long id) {
        return service.listRecords(id).stream().map(RecordResponse::from).toList();
    }

    @PostMapping("/{id}/records")
    public RecordResponse addRecord(@PathVariable Long id, @RequestBody RecordRequest req) {
        return RecordResponse.from(service.addRecord(id, req));
    }

    @PatchMapping("/{id}/records/{recordId}")
    public RecordResponse updateRecord(@PathVariable Long id, @PathVariable Long recordId,
                                       @RequestBody RecordRequest req) {
        return RecordResponse.from(service.updateRecord(id, recordId, req));
    }

    @DeleteMapping("/{id}/records/{recordId}")
    public ResponseEntity<Map<String, String>> deleteRecord(@PathVariable Long id,
                                                            @PathVariable Long recordId) {
        service.deleteRecord(id, recordId);
        return ResponseEntity.ok(Map.of("status", "deleted"));
    }
}
