package com.spiramindscape.backend.resource;

import com.spiramindscape.backend.goal.Goal;
import com.spiramindscape.backend.goal.GoalRepository;
import com.spiramindscape.backend.graphql.input.CreateResourceInput;
import com.spiramindscape.backend.graphql.input.UpdateResourceInput;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ResourceService {

    private final ResourceRepository resourceRepository;
    private final GoalRepository goalRepository;

    @Transactional(readOnly = true)
    public List<Resource> findByGoal(Long goalId) {
        goalRepository.findById(goalId)
                .orElseThrow(() -> new IllegalArgumentException("Goal not found: " + goalId));
        return resourceRepository.findByGoalIdOrderByCreatedAtAsc(goalId);
    }

    @Transactional(readOnly = true)
    public Map<Long, List<Resource>> findByGoalIds(List<Long> goalIds) {
        if (goalIds.isEmpty()) {
            return Map.of();
        }
        return resourceRepository.findByGoalIdInOrderByGoalIdAscCreatedAtAsc(goalIds)
                .stream()
                .collect(Collectors.groupingBy(resource -> resource.getGoal().getId()));
    }

    @Transactional(readOnly = true)
    public Resource findById(Long id) {
        return resourceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Resource not found: " + id));
    }

    @Transactional
    public Resource create(Long goalId, CreateResourceInput input) {
        Goal goal = goalRepository.findById(goalId)
                .orElseThrow(() -> new IllegalArgumentException("Goal not found: " + goalId));
        Resource resource = new Resource();
        resource.setGoal(goal);
        resource.setType(normalizeType(input.type()));
        applyFields(resource, input.title(), input.body(), input.url(), input.mime(),
                input.dataUrl(), input.name(), input.role(), input.email(), input.phone());
        return resourceRepository.save(resource);
    }

    @Transactional
    public Resource update(Long id, UpdateResourceInput input) {
        Resource resource = findById(id);
        applyFields(resource, input.title(), input.body(), input.url(), input.mime(),
                input.dataUrl(), input.name(), input.role(), input.email(), input.phone());
        return resourceRepository.save(resource);
    }

    @Transactional
    public void delete(Long id) {
        resourceRepository.delete(findById(id));
    }

    private void applyFields(Resource r, String title, String body, String url, String mime,
                              String dataUrl, String name, String role, String email, String phone) {
        if (title != null)   r.setTitle(title);
        if (body != null)    r.setBody(body);
        if (url != null)     r.setUrl(url);
        if (mime != null)    r.setMime(mime);
        if (dataUrl != null) r.setDataUrl(dataUrl);
        if (name != null)    r.setName(name);
        if (role != null)    r.setRole(role);
        if (email != null)   r.setEmail(email);
        if (phone != null)   r.setPhone(phone);
    }

    private String normalizeType(String type) {
        String normalized = Objects.requireNonNull(type).toLowerCase(Locale.ROOT);
        if (!List.of("note", "link", "file", "email", "contact").contains(normalized)) {
            throw new IllegalArgumentException("Unknown resource type: " + type);
        }
        return normalized;
    }
}
