package com.spiramindscape.backend.resource;

import com.spiramindscape.backend.goal.Goal;
import com.spiramindscape.backend.goal.GoalRepository;
import com.spiramindscape.backend.graphql.input.CreateResourceInput;
import com.spiramindscape.backend.graphql.input.UpdateResourceInput;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResourceServiceTest {

    private static final String PNG_DATA_URL = "data:image/png;base64,aGVsbG8=";
    private static final String PDF_DATA_URL = "data:application/pdf;base64,JVBERi0xLjQ=";

    @Mock
    private ResourceRepository resourceRepository;

    @Mock
    private GoalRepository goalRepository;

    @InjectMocks
    private ResourceService resourceService;

    @Test
    void createsNoteResourceWithRequiredFieldsOnly() {
        Goal goal = goal(1L);
        when(goalRepository.findById(1L)).thenReturn(Optional.of(goal));
        when(resourceRepository.save(any(Resource.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Resource resource = resourceService.create(1L, new CreateResourceInput(
                "Research notes",
                "note",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        ));

        assertThat(resource.getType()).isEqualTo("note");
        assertThat(resource.getTitle()).isEqualTo("Research notes");
        assertThat(resource.getBody()).isNull();
    }

    @Test
    void createsNoteResourceWithRequiredAndOptionalFields() {
        Goal goal = goal(1L);
        when(goalRepository.findById(1L)).thenReturn(Optional.of(goal));
        when(resourceRepository.save(any(Resource.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Resource resource = resourceService.create(1L, new CreateResourceInput(
                "Research notes",
                "note",
                "<p>Remember this.</p>",
                null,
                null,
                null,
                null,
                null,
                null,
                null
        ));

        assertThat(resource.getType()).isEqualTo("note");
        assertThat(resource.getTitle()).isEqualTo("Research notes");
        assertThat(resource.getBody()).isEqualTo("<p>Remember this.</p>");
    }

    @Test
    void createsNoteResourceWithBodyAtMaximumLength() {
        Goal goal = goal(1L);
        when(goalRepository.findById(1L)).thenReturn(Optional.of(goal));
        when(resourceRepository.save(any(Resource.class))).thenAnswer(invocation -> invocation.getArgument(0));
        String body = noteBodyAtLimit();

        Resource resource = resourceService.create(1L, new CreateResourceInput(
                "Research notes",
                "note",
                body,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        ));

        assertThat(resource.getBody()).isEqualTo(body);
    }

    @Test
    void rejectsNoteResourceWithOversizedBody() {
        Goal goal = goal(1L);
        when(goalRepository.findById(1L)).thenReturn(Optional.of(goal));

        assertThatThrownBy(() -> resourceService.create(1L, new CreateResourceInput(
                "Research notes",
                "note",
                oversizedNoteBody(),
                null,
                null,
                null,
                null,
                null,
                null,
                null
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(maxNoteBodyLengthMessage());

        verify(resourceRepository, never()).save(any(Resource.class));
    }

    @Test
    void trimsWhitespaceFromNoteTitleOnCreate() {
        Goal goal = goal(1L);
        when(goalRepository.findById(1L)).thenReturn(Optional.of(goal));
        when(resourceRepository.save(any(Resource.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Resource resource = resourceService.create(1L, new CreateResourceInput(
                "   Research notes   ",
                "note",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        ));

        assertThat(resource.getType()).isEqualTo("note");
        assertThat(resource.getTitle()).isEqualTo("Research notes");
    }

    @Test
    void trimsWhitespaceFromNoteTitleWithOnlySpacesOnCreate() {
        Goal goal = goal(1L);
        when(goalRepository.findById(1L)).thenReturn(Optional.of(goal));

        assertThatThrownBy(() -> resourceService.create(1L, new CreateResourceInput(
                "   ",
                "note",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Note resource requires title");

        verify(resourceRepository, never()).save(any(Resource.class));
    }

    @Test
    void trimsWhitespaceFromNoteTitleWithEmptyStringOnCreate() {
        Goal goal = goal(1L);
        when(goalRepository.findById(1L)).thenReturn(Optional.of(goal));

        assertThatThrownBy(() -> resourceService.create(1L, new CreateResourceInput(
                "",
                "note",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Note resource requires title");

        verify(resourceRepository, never()).save(any(Resource.class));
    }

    @Test
    void trimsWhitespaceFromNoteTitleWithNewlineOnCreate() {
        Goal goal = goal(1L);
        when(goalRepository.findById(1L)).thenReturn(Optional.of(goal));

        assertThatThrownBy(() -> resourceService.create(1L, new CreateResourceInput(
                "\n",
                "note",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Note resource requires title");

        verify(resourceRepository, never()).save(any(Resource.class));
    }

    @Test
    void rejectsNoteResourceWithoutTitle() {
        Goal goal = goal(1L);
        when(goalRepository.findById(1L)).thenReturn(Optional.of(goal));

        assertThatThrownBy(() -> resourceService.create(1L, new CreateResourceInput(
                null,
                "note",
                "<p>Remember this.</p>",
                null,
                null,
                null,
                null,
                null,
                null,
                null
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Note resource requires title");

        verify(resourceRepository, never()).save(any(Resource.class));
    }

    @Test
    void createsLinkResourceWithRequiredFieldsOnly() {
        Goal goal = goal(1L);
        when(goalRepository.findById(1L)).thenReturn(Optional.of(goal));
        when(resourceRepository.save(any(Resource.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Resource resource = resourceService.create(1L, new CreateResourceInput(
                null,
                "link",
                null,
                "https://example.com/docs",
                null,
                null,
                null,
                null,
                null,
                null
        ));

        assertThat(resource.getType()).isEqualTo("link");
        assertThat(resource.getTitle()).isEqualTo("example");
        assertThat(resource.getUrl()).isEqualTo("https://example.com/docs");
    }

    @Test
    void createsLinkResourceWithGeneratedTitleFromDomain() {
        Goal goal = goal(1L);
        when(goalRepository.findById(1L)).thenReturn(Optional.of(goal));
        when(resourceRepository.save(any(Resource.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Resource resource = resourceService.create(1L, new CreateResourceInput(
                null,
                "link",
                null,
                "https://chatgpt.com",
                null,
                null,
                null,
                null,
                null,
                null
        ));

        assertThat(resource.getTitle()).isEqualTo("chatgpt");
    }

    @Test
    void createsLinkResourceWithRequiredAndOptionalFields() {
        Goal goal = goal(1L);
        when(goalRepository.findById(1L)).thenReturn(Optional.of(goal));
        when(resourceRepository.save(any(Resource.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Resource resource = resourceService.create(1L, new CreateResourceInput(
                "Docs",
                "link",
                null,
                "https://example.com/docs",
                null,
                null,
                null,
                null,
                null,
                null
        ));

        assertThat(resource.getType()).isEqualTo("link");
        assertThat(resource.getTitle()).isEqualTo("Docs");
        assertThat(resource.getUrl()).isEqualTo("https://example.com/docs");
    }

    @Test
    void rejectsLinkResourceWithoutUrl() {
        Goal goal = goal(1L);
        when(goalRepository.findById(1L)).thenReturn(Optional.of(goal));

        assertThatThrownBy(() -> resourceService.create(1L, new CreateResourceInput(
                "Docs",
                "link",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Link resource requires URL");

        verify(resourceRepository, never()).save(any(Resource.class));
    }

    @Test
    void rejectsLinkResourceWithInvalidUrl() {
        Goal goal = goal(1L);
        when(goalRepository.findById(1L)).thenReturn(Optional.of(goal));

        assertThatThrownBy(() -> resourceService.create(1L, new CreateResourceInput(
                "Docs",
                "link",
                null,
                "file:///tmp/readme.txt",
                null,
                null,
                null,
                null,
                null,
                null
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Link resource URL must be a valid http(s) URL");

        verify(resourceRepository, never()).save(any(Resource.class));
    }

    @Test
    void createsImageFileResourceWithRequiredFields() {
        Goal goal = goal(1L);
        when(goalRepository.findById(1L)).thenReturn(Optional.of(goal));
        when(resourceRepository.save(any(Resource.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Resource resource = resourceService.create(1L, new CreateResourceInput(
                "Screenshot",
                "file",
                null,
                null,
                "image/png",
                PNG_DATA_URL,
                null,
                null,
                null,
                null
        ));

        assertThat(resource.getType()).isEqualTo("file");
        assertThat(resource.getTitle()).isEqualTo("Screenshot");
        assertThat(resource.getMime()).isEqualTo("image/png");
        assertThat(resource.getDataUrl()).isEqualTo(PNG_DATA_URL);
    }

    @Test
    void rejectsImageFileResourceWithoutTitle() {
        Goal goal = goal(1L);
        when(goalRepository.findById(1L)).thenReturn(Optional.of(goal));

        assertThatThrownBy(() -> resourceService.create(1L, new CreateResourceInput(
                null,
                "file",
                null,
                null,
                "image/png",
                PNG_DATA_URL,
                null,
                null,
                null,
                null
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("File resource requires title");

        verify(resourceRepository, never()).save(any(Resource.class));
    }

    @Test
    void rejectsImageFileResourceWithoutMime() {
        Goal goal = goal(1L);
        when(goalRepository.findById(1L)).thenReturn(Optional.of(goal));

        assertThatThrownBy(() -> resourceService.create(1L, new CreateResourceInput(
                "Screenshot",
                "file",
                null,
                null,
                null,
                PNG_DATA_URL,
                null,
                null,
                null,
                null
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("File resource requires MIME type");

        verify(resourceRepository, never()).save(any(Resource.class));
    }

    @Test
    void rejectsImageFileResourceWithoutDataUrl() {
        Goal goal = goal(1L);
        when(goalRepository.findById(1L)).thenReturn(Optional.of(goal));

        assertThatThrownBy(() -> resourceService.create(1L, new CreateResourceInput(
                "Screenshot",
                "file",
                null,
                null,
                "image/png",
                null,
                null,
                null,
                null,
                null
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("File resource requires data URL");

        verify(resourceRepository, never()).save(any(Resource.class));
    }

    @Test
    void createsDocumentFileResourceWithRequiredFields() {
        Goal goal = goal(1L);
        when(goalRepository.findById(1L)).thenReturn(Optional.of(goal));
        when(resourceRepository.save(any(Resource.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Resource resource = resourceService.create(1L, new CreateResourceInput(
                "Brief",
                "file",
                null,
                null,
                "application/pdf",
                PDF_DATA_URL,
                null,
                null,
                null,
                null
        ));

        assertThat(resource.getType()).isEqualTo("file");
        assertThat(resource.getTitle()).isEqualTo("Brief");
        assertThat(resource.getMime()).isEqualTo("application/pdf");
        assertThat(resource.getDataUrl()).isEqualTo(PDF_DATA_URL);
    }

    @Test
    void rejectsFileResourceWithoutTitle() {
        Goal goal = goal(1L);
        when(goalRepository.findById(1L)).thenReturn(Optional.of(goal));

        assertThatThrownBy(() -> resourceService.create(1L, new CreateResourceInput(
                null,
                "file",
                null,
                null,
                "application/pdf",
                PDF_DATA_URL,
                null,
                null,
                null,
                null
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("File resource requires title");

        verify(resourceRepository, never()).save(any(Resource.class));
    }

    @Test
    void rejectsFileResourceWithoutMime() {
        Goal goal = goal(1L);
        when(goalRepository.findById(1L)).thenReturn(Optional.of(goal));

        assertThatThrownBy(() -> resourceService.create(1L, new CreateResourceInput(
                "Brief",
                "file",
                null,
                null,
                null,
                PDF_DATA_URL,
                null,
                null,
                null,
                null
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("File resource requires MIME type");

        verify(resourceRepository, never()).save(any(Resource.class));
    }

    @Test
    void rejectsFileResourceWithoutDataUrl() {
        Goal goal = goal(1L);
        when(goalRepository.findById(1L)).thenReturn(Optional.of(goal));

        assertThatThrownBy(() -> resourceService.create(1L, new CreateResourceInput(
                "Brief",
                "file",
                null,
                null,
                "application/pdf",
                null,
                null,
                null,
                null,
                null
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("File resource requires data URL");

        verify(resourceRepository, never()).save(any(Resource.class));
    }

    @Test
    void rejectsFileResourceWithUnsupportedMime() {
        Goal goal = goal(1L);
        when(goalRepository.findById(1L)).thenReturn(Optional.of(goal));

        assertThatThrownBy(() -> resourceService.create(1L, new CreateResourceInput(
                "Archive",
                "file",
                null,
                null,
                "application/zip",
                "data:application/zip;base64,aGVsbG8=",
                null,
                null,
                null,
                null
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("File resource MIME type must be an image or PDF");

        verify(resourceRepository, never()).save(any(Resource.class));
    }

    @Test
    void rejectsFileResourceWhenDataUrlMimeDoesNotMatch() {
        Goal goal = goal(1L);
        when(goalRepository.findById(1L)).thenReturn(Optional.of(goal));

        assertThatThrownBy(() -> resourceService.create(1L, new CreateResourceInput(
                "Screenshot",
                "file",
                null,
                null,
                "image/png",
                PDF_DATA_URL,
                null,
                null,
                null,
                null
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("File resource data URL must match MIME type");

        verify(resourceRepository, never()).save(any(Resource.class));
    }

    @Test
    void rejectsFileResourceWhenFileIsTooLarge() {
        Goal goal = goal(1L);
        when(goalRepository.findById(1L)).thenReturn(Optional.of(goal));

        assertThatThrownBy(() -> resourceService.create(1L, new CreateResourceInput(
                "Huge image",
                "file",
                null,
                null,
                "image/png",
                oversizedPngDataUrl(),
                null,
                null,
                null,
                null
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("File resource must be 5 MB or smaller");

        verify(resourceRepository, never()).save(any(Resource.class));
    }

    @Test
    void createsEmailResourceWithLowercaseType() {
        Goal goal = goal(1L);
        when(goalRepository.findById(1L)).thenReturn(Optional.of(goal));
        when(resourceRepository.save(any(Resource.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Resource resource = resourceService.create(1L, new CreateResourceInput(
                null,
                "EMAIL",
                null,
                null,
                null,
                null,
                "Ada Lovelace",
                "Mentor",
                "ada@example.com",
                null
        ));

        assertThat(resource.getType()).isEqualTo("email");
        assertThat(resource.getName()).isEqualTo("Ada Lovelace");
        assertThat(resource.getEmail()).isEqualTo("ada@example.com");
    }

    @Test
    void createsEmailResourceWithGeneratedNameFromEmail() {
        Goal goal = goal(1L);
        when(goalRepository.findById(1L)).thenReturn(Optional.of(goal));
        when(resourceRepository.save(any(Resource.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Resource resource = resourceService.create(1L, new CreateResourceInput(
                null,
                "email",
                null,
                null,
                null,
                null,
                null,
                null,
                "ada@example.com",
                null
        ));

        assertThat(resource.getName()).isEqualTo("ada@example.com");
        assertThat(resource.getEmail()).isEqualTo("ada@example.com");
    }

    @Test
    void rejectsResourceTitleLongerThanLabelLimit() {
        Goal goal = goal(1L);
        when(goalRepository.findById(1L)).thenReturn(Optional.of(goal));

        assertThatThrownBy(() -> resourceService.create(1L, new CreateResourceInput(
                "A".repeat(ResourceService.MAX_RESOURCE_LABEL_LENGTH + 1),
                "note",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Note resource title must be "
                        + ResourceService.MAX_RESOURCE_LABEL_LENGTH + " characters or fewer");

        verify(resourceRepository, never()).save(any(Resource.class));
    }

    @Test
    void rejectsEmailNameLongerThanLabelLimit() {
        Goal goal = goal(1L);
        when(goalRepository.findById(1L)).thenReturn(Optional.of(goal));

        assertThatThrownBy(() -> resourceService.create(1L, new CreateResourceInput(
                null,
                "email",
                null,
                null,
                null,
                null,
                "A".repeat(ResourceService.MAX_RESOURCE_LABEL_LENGTH + 1),
                null,
                "ada@example.com",
                null
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Email resource name must be "
                        + ResourceService.MAX_RESOURCE_LABEL_LENGTH + " characters or fewer");

        verify(resourceRepository, never()).save(any(Resource.class));
    }

    @Test
    void rejectsEmailResourceWithoutEmail() {
        Goal goal = goal(1L);
        when(goalRepository.findById(1L)).thenReturn(Optional.of(goal));

        assertThatThrownBy(() -> resourceService.create(1L, new CreateResourceInput(
                null,
                "email",
                null,
                null,
                null,
                null,
                "Ada Lovelace",
                "Mentor",
                null,
                null
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Email resource requires email");

        verify(resourceRepository, never()).save(any(Resource.class));
    }

    @Test
    void rejectsEmailResourceWithInvalidEmail() {
        Goal goal = goal(1L);
        when(goalRepository.findById(1L)).thenReturn(Optional.of(goal));

        assertThatThrownBy(() -> resourceService.create(1L, new CreateResourceInput(
                null,
                "email",
                null,
                null,
                null,
                null,
                "Ada Lovelace",
                "Mentor",
                "not-an-email",
                null
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Email resource email must be valid");

        verify(resourceRepository, never()).save(any(Resource.class));
    }

    @Test
    void trimsWhitespaceFromEmailNameOnCreate() {
        Goal goal = goal(1L);
        when(goalRepository.findById(1L)).thenReturn(Optional.of(goal));
        when(resourceRepository.save(any(Resource.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Resource resource = resourceService.create(1L, new CreateResourceInput(
                null,
                "email",
                null,
                null,
                null,
                null,
                "   Ada Lovelace   ",
                "Mentor",
                "ada@example.com",
                null
        ));

        assertThat(resource.getName()).isEqualTo("Ada Lovelace");
        assertThat(resource.getRole()).isEqualTo("Mentor");
    }

    @Test
    void trimsWhitespaceFromEmailEmailOnCreate() {
        Goal goal = goal(1L);
        when(goalRepository.findById(1L)).thenReturn(Optional.of(goal));
        when(resourceRepository.save(any(Resource.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Resource resource = resourceService.create(1L, new CreateResourceInput(
                null,
                "email",
                null,
                null,
                null,
                null,
                "Ada Lovelace",
                "Mentor",
                "  ada@example.com  ",
                null
        ));

        assertThat(resource.getEmail()).isEqualTo("ada@example.com");
    }

    @Test
    void trimsWhitespaceFromEmailPhoneOnCreate() {
        Goal goal = goal(1L);
        when(goalRepository.findById(1L)).thenReturn(Optional.of(goal));
        when(resourceRepository.save(any(Resource.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Resource resource = resourceService.create(1L, new CreateResourceInput(
                null,
                "email",
                null,
                null,
                null,
                null,
                "Ada Lovelace",
                "Mentor",
                "ada@example.com",
                "  +46 70 123 45 67  "
        ));

        assertThat(resource.getPhone()).isEqualTo("+46 70 123 45 67");
    }

    @Test
    void trimsWhitespaceFromNoteTitleOnUpdate() {
        Resource resource = new Resource();
        resource.setId(1L);
        resource.setType("note");
        resource.setTitle("Old title");
        when(resourceRepository.findById(1L)).thenReturn(Optional.of(resource));
        when(resourceRepository.save(any(Resource.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Resource updated = resourceService.update(1L, new UpdateResourceInput(
                "   New title   ",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        ));

        assertThat(updated.getTitle()).isEqualTo("New title");
    }

    @Test
    void updatesNoteResourceBodyToMaximumLength() {
        Resource resource = new Resource();
        resource.setId(1L);
        resource.setType("note");
        resource.setTitle("Research notes");
        resource.setBody("<p>Draft note.</p>");
        when(resourceRepository.findById(1L)).thenReturn(Optional.of(resource));
        when(resourceRepository.save(any(Resource.class))).thenAnswer(invocation -> invocation.getArgument(0));
        String body = noteBodyAtLimit();

        Resource updated = resourceService.update(1L, new UpdateResourceInput(
                null,
                body,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        ));

        assertThat(updated.getBody()).isEqualTo(body);
    }

    @Test
    void rejectsNoteResourceUpdateWithOversizedBody() {
        Resource resource = new Resource();
        resource.setId(1L);
        resource.setType("note");
        resource.setTitle("Research notes");
        resource.setBody("<p>Draft note.</p>");
        when(resourceRepository.findById(1L)).thenReturn(Optional.of(resource));

        assertThatThrownBy(() -> resourceService.update(1L, new UpdateResourceInput(
                null,
                oversizedNoteBody(),
                null,
                null,
                null,
                null,
                null,
                null,
                null
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(maxNoteBodyLengthMessage());

        verify(resourceRepository, never()).save(any(Resource.class));
    }

    @Test
    void trimsWhitespaceFromEmailNameOnUpdate() {
        Resource resource = new Resource();
        resource.setId(1L);
        resource.setType("email");
        resource.setEmail("ada@example.com");
        when(resourceRepository.findById(1L)).thenReturn(Optional.of(resource));
        when(resourceRepository.save(any(Resource.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Resource updated = resourceService.update(1L, new UpdateResourceInput(
                null,
                null,
                null,
                null,
                null,
                "   Ada Lovelace   ",
                "Mentor",
                null,
                null
        ));

        assertThat(updated.getName()).isEqualTo("Ada Lovelace");
        assertThat(updated.getRole()).isEqualTo("Mentor");
    }

    @Test
    void trimsWhitespaceFromEmailEmailOnUpdate() {
        Resource resource = new Resource();
        resource.setId(1L);
        resource.setType("email");
        resource.setName("Ada Lovelace");
        when(resourceRepository.findById(1L)).thenReturn(Optional.of(resource));
        when(resourceRepository.save(any(Resource.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Resource updated = resourceService.update(1L, new UpdateResourceInput(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "  ada.updated@example.com  ",
                null
        ));

        assertThat(updated.getEmail()).isEqualTo("ada.updated@example.com");
    }

    @Test
    void trimsWhitespaceFromEmailPhoneOnUpdate() {
        Resource resource = new Resource();
        resource.setId(1L);
        resource.setType("email");
        resource.setName("Ada Lovelace");
        resource.setEmail("ada@example.com");
        when(resourceRepository.findById(1L)).thenReturn(Optional.of(resource));
        when(resourceRepository.save(any(Resource.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Resource updated = resourceService.update(1L, new UpdateResourceInput(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "  +46 70 123 45 67  "
        ));

        assertThat(updated.getPhone()).isEqualTo("+46 70 123 45 67");
    }

    @Test
    void trimsWhitespaceFromLinkUrlOnUpdate() {
        Resource resource = new Resource();
        resource.setId(1L);
        resource.setType("link");
        resource.setTitle("Docs");
        resource.setUrl("https://example.com/old");
        when(resourceRepository.findById(1L)).thenReturn(Optional.of(resource));
        when(resourceRepository.save(any(Resource.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Resource updated = resourceService.update(1L, new UpdateResourceInput(
                null,
                null,
                "  https://example.com/new  ",
                null,
                null,
                null,
                null,
                null,
                null
        ));

        assertThat(updated.getUrl()).isEqualTo("https://example.com/new");
    }

    @Test
    void rejectsOldContactAliasBecauseBackendModelUsesEmail() {
        Goal goal = goal(1L);
        when(goalRepository.findById(1L)).thenReturn(Optional.of(goal));

        assertThatThrownBy(() -> resourceService.create(1L, new CreateResourceInput(
                null,
                "contact",
                null,
                null,
                null,
                null,
                "Ada Lovelace",
                null,
                "ada@example.com",
                null
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown resource type: contact");

        verify(resourceRepository, never()).save(any(Resource.class));
    }

    @Test
    void createsLinkResourceWithUrlAtMaximumLength() {
        Goal goal = goal(1L);
        when(goalRepository.findById(1L)).thenReturn(Optional.of(goal));
        when(resourceRepository.save(any(Resource.class))).thenAnswer(invocation -> invocation.getArgument(0));
        String url = "https://example.com/" + "a".repeat(ResourceService.MAX_LINK_URL_LENGTH - "https://example.com/".length());

        Resource resource = resourceService.create(1L, new CreateResourceInput(
                null,
                "link",
                null,
                url,
                null,
                null,
                null,
                null,
                null,
                null
        ));

        assertThat(resource.getUrl()).isEqualTo(url);
    }

    @Test
    void rejectsLinkResourceWithUrlExceedingMaximumLength() {
        Goal goal = goal(1L);
        when(goalRepository.findById(1L)).thenReturn(Optional.of(goal));
        String url = "https://example.com/" + "a".repeat(ResourceService.MAX_LINK_URL_LENGTH);

        assertThatThrownBy(() -> resourceService.create(1L, new CreateResourceInput(
                null,
                "link",
                null,
                url,
                null,
                null,
                null,
                null,
                null,
                null
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Link resource URL must be " + ResourceService.MAX_LINK_URL_LENGTH + " characters or fewer");

        verify(resourceRepository, never()).save(any(Resource.class));
    }

    @Test
    void deletesResourceById() {
        Resource resource = new Resource();
        resource.setId(42L);
        resource.setType("note");
        resource.setTitle("Research notes");
        when(resourceRepository.findById(42L)).thenReturn(Optional.of(resource));

        resourceService.delete(42L);

        verify(resourceRepository).delete(resource);
    }

    private static Goal goal(Long id) {
        Goal goal = new Goal();
        goal.setId(id);
        goal.setTitle("Goal " + id);
        goal.setDescription("");
        goal.setConfidence(7);
        return goal;
    }

    private static String oversizedPngDataUrl() {
        int base64Length = ((ResourceService.MAX_FILE_BYTES + 1 + 2) / 3) * 4;
        return "data:image/png;base64," + "A".repeat(base64Length);
    }

    private static String noteBodyAtLimit() {
        return "A".repeat(ResourceService.MAX_NOTE_BODY_LENGTH);
    }

    private static String oversizedNoteBody() {
        return "A".repeat(ResourceService.MAX_NOTE_BODY_LENGTH + 1);
    }

    private static String maxNoteBodyLengthMessage() {
        return "Note resource body must be " + ResourceService.MAX_NOTE_BODY_LENGTH + " characters or fewer";
    }
}
