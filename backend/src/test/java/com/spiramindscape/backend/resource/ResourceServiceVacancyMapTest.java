package com.spiramindscape.backend.resource;

import com.spiramindscape.backend.goal.Goal;
import com.spiramindscape.backend.goal.GoalService;
import com.spiramindscape.backend.graphql.input.CreateResourceInput;
import com.spiramindscape.backend.graphql.input.MapPatchInput;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** The vacancy map's own rules: how one is created, and that a patch only ever writes its field. */
@ExtendWith(MockitoExtension.class)
class ResourceServiceVacancyMapTest {

    @Mock
    private ResourceRepository resourceRepository;

    @Mock
    private GoalService goalService;

    @InjectMocks
    private ResourceService resourceService;

    @Test
    @DisplayName("a map is created from its name alone, with an empty document")
    void createsVacancyMapWithTitleOnly() {
        Goal goal = goal(1L);
        when(goalService.findById(1L)).thenReturn(goal);
        when(resourceRepository.save(any(Resource.class))).thenAnswer(call -> call.getArgument(0));

        Resource resource = resourceService.create(1L, new CreateResourceInput(
                "Backend developer, Advania", "vacancy",
                null, null, null, null, null, null, null, null));

        assertThat(resource.getType()).isEqualTo("vacancy");
        assertThat(resource.getTitle()).isEqualTo("Backend developer, Advania");
        // Normalised, not left null — so a patched map and a fresh one have the same shape.
        assertThat(resource.getMapData()).isEqualTo("{}");
    }

    @Test
    @DisplayName("a map must be named — its title is the vacancy")
    void rejectsVacancyMapWithoutTitle() {
        when(goalService.findById(1L)).thenReturn(goal(1L));

        assertThatThrownBy(() -> resourceService.create(1L, new CreateResourceInput(
                null, "vacancy", null, null, null, null, null, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("requires title");
    }

    @Test
    @DisplayName("a field belonging to another resource type is refused")
    void rejectsFieldThatIsNotAMapField() {
        when(goalService.findById(1L)).thenReturn(goal(1L));

        assertThatThrownBy(() -> resourceService.create(1L,
                new CreateResourceInput("A vacancy", "vacancy", "<p>notes</p>",
                        null, null, null, null, null, null, null),
                Map.of("type", "vacancy", "title", "A vacancy", "body", "<p>notes</p>")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not allowed for vacancy");
    }

    @Test
    @DisplayName("duplicating sends the whole document, which is the one time that is right")
    void createsVacancyMapFromAWholeDocument() {
        when(goalService.findById(1L)).thenReturn(goal(1L));
        when(resourceRepository.save(any(Resource.class))).thenAnswer(call -> call.getArgument(0));

        Resource resource = resourceService.create(1L, new CreateResourceInput(
                "Backend developer copy", "vacancy", null, null, null, null, null, null, null, null,
                "{\"facts\":{\"location\":\"Stockholm\"}}"));

        assertThat(resource.getMapData()).contains("\"location\":\"Stockholm\"");
    }

    @Test
    @DisplayName("a patch writes the field it names and leaves the rest of the map alone")
    void patchWritesOneFieldOnly() {
        Resource map = vacancy(5L, "{\"facts\":{\"location\":\"Stockholm\"},"
                + "\"skills\":[{\"id\":\"a\",\"text\":\"Java\"}]}");
        when(resourceRepository.findById(5L)).thenReturn(Optional.of(map));
        when(goalService.findById(1L)).thenReturn(map.getGoal());
        when(resourceRepository.save(any(Resource.class))).thenAnswer(call -> call.getArgument(0));

        Resource result = resourceService.patchMap(5L,
                List.of(new MapPatchInput("/facts/language", "\"Swedish\"")));

        assertThat(result.getMapData()).contains("\"language\":\"Swedish\"");
        // Neither of the two the patch did not name.
        assertThat(result.getMapData()).contains("\"location\":\"Stockholm\"");
        assertThat(result.getMapData()).contains("\"text\":\"Java\"");
    }

    @Test
    @DisplayName("only a vacancy map can be patched this way")
    void patchRefusesANote() {
        Resource note = new Resource();
        note.setId(5L);
        note.setType("note");
        note.setGoal(goal(1L));
        when(resourceRepository.findById(5L)).thenReturn(Optional.of(note));
        when(goalService.findById(1L)).thenReturn(note.getGoal());

        assertThatThrownBy(() -> resourceService.patchMap(5L,
                List.of(new MapPatchInput("/facts/location", "\"x\""))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Only a vacancy map");
    }

    @Test
    @DisplayName("a patch with no fields is refused rather than saved as a no-op")
    void patchRefusesAnEmptyList() {
        Resource map = vacancy(5L, "{}");
        when(resourceRepository.findById(5L)).thenReturn(Optional.of(map));
        when(goalService.findById(1L)).thenReturn(map.getGoal());

        assertThatThrownBy(() -> resourceService.patchMap(5L, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one field");
    }

    private Resource vacancy(Long id, String mapData) {
        Resource resource = new Resource();
        resource.setId(id);
        resource.setType("vacancy");
        resource.setTitle("Backend developer");
        resource.setMapData(mapData);
        resource.setGoal(goal(1L));
        return resource;
    }

    private Goal goal(Long id) {
        Goal goal = new Goal();
        goal.setId(id);
        return goal;
    }
}
