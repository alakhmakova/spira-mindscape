package com.spiramindscape.backend.graphql;

import com.spiramindscape.backend.resource.ResourceService;
import com.spiramindscape.backend.support.BaseGraphQlIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.graphql.test.tester.GraphQlTester;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ResourceIntegrationTest extends BaseGraphQlIntegrationTest {

    private static final String NON_EXISTENT_ID = String.valueOf(Long.MAX_VALUE);
    private static final String PNG_DATA_URL = "data:image/png;base64,aGVsbG8=";
    private static final String UPDATED_PNG_DATA_URL = "data:image/png;base64,dXBkYXRlZA==";
    private static final String PDF_DATA_URL = "data:application/pdf;base64,JVBERi0xLjQ=";
    private static final String UPDATED_PDF_DATA_URL = "data:application/pdf;base64,JVBERi0xLjUK";

    private String goalId;

    @BeforeEach
    void createGoal() {
        goalId = graphQlTester.document("""
                        mutation {
                          createGoal(input: { title: "Test goal for resources", confidence: 5 }) {
                            id
                          }
                        }
                        """)
                .execute()
                .path("createGoal.id").entity(String.class).get();
    }

    @Test
    @DisplayName("Creates note resource with required fields only")
    void createsNoteResourceWithRequiredFieldsOnly() {
        graphQlTester.document("""
                        mutation($goalId: ID!) {
                          createResource(goalId: $goalId, input: {
                            type: "note"
                            title: "Research notes"
                          }) {
                            id type title body
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .execute()
                .path("createResource.id").hasValue()
                .path("createResource.type").entity(String.class).isEqualTo("note")
                .path("createResource.title").entity(String.class).isEqualTo("Research notes")
                .path("createResource.body").valueIsNull();
    }

    @Test
    @DisplayName("Creates note resource with minimal fields and body")
    void createsNoteResourceWithMinimalFields() {
        graphQlTester.document("""
                        mutation($goalId: ID!) {
                          createResource(goalId: $goalId, input: {
                            type: "note"
                            title: "Research notes"
                            body: "<p>Remember this.</p>"
                          }) {
                            id type title body
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .execute()
                .path("createResource.type").entity(String.class).isEqualTo("note")
                .path("createResource.title").entity(String.class).isEqualTo("Research notes")
                .path("createResource.body").entity(String.class).isEqualTo("<p>Remember this.</p>");
    }

    @Test
    @DisplayName("Creates note resource with required and optional fields")
    void createsNoteResourceWithRequiredAndOptionalFields() {
        graphQlTester.document("""
                        mutation($goalId: ID!) {
                          createResource(goalId: $goalId, input: {
                            type: "note"
                            title: "Research notes"
                            body: "<p>Remember this.</p>"
                          }) {
                            id type title body
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .execute()
                .path("createResource.type").entity(String.class).isEqualTo("note")
                .path("createResource.title").entity(String.class).isEqualTo("Research notes")
                .path("createResource.body").entity(String.class).isEqualTo("<p>Remember this.</p>");
    }

    @Test
    @DisplayName("Creates note resource with body at maximum length")
    void createsNoteResourceWithBodyAtMaximumLength() {
        String body = noteBodyAtLimit();

        graphQlTester.document("""
                        mutation($goalId: ID!, $body: String!) {
                          createResource(goalId: $goalId, input: {
                            type: "note"
                            title: "Research notes"
                            body: $body
                          }) {
                            id type title body
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .variable("body", body)
                .execute()
                .path("createResource.type").entity(String.class).isEqualTo("note")
                .path("createResource.title").entity(String.class).isEqualTo("Research notes")
                .path("createResource.body").entity(String.class).isEqualTo(body);
    }

    @Test
    @DisplayName("Returns ValidationError when creating note resource with oversized body")
    void returnsErrorWhenCreatingNoteWithOversizedBody() {
        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($goalId: ID!, $body: String!) {
                          createResource(goalId: $goalId, input: {
                            type: "note"
                            title: "Research notes"
                            body: $body
                          }) {
                            id
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .variable("body", oversizedNoteBody())
                .execute();

        assertValidationError(response, maxNoteBodyLengthMessage());
        assertNoResourcesCreated();
    }

    @Test
    @DisplayName("Returns ValidationError when creating note resource without title")
    void returnsErrorWhenCreatingNoteWithoutTitle() {
        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($goalId: ID!) {
                          createResource(goalId: $goalId, input: {
                            type: "note"
                          }) {
                            id
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .execute();

        assertValidationError(response, "Note resource requires title");
        assertNoResourcesCreated();
    }

    @Test
    @DisplayName("Returns ValidationError when creating note resource with blank title")
    void returnsErrorWhenCreatingNoteWithBlankTitle() {
        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($goalId: ID!) {
                          createResource(goalId: $goalId, input: {
                            type: "note"
                            title: "   "
                          }) {
                            id
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .execute();

        assertValidationError(response, "Note resource requires title");
        assertNoResourcesCreated();
    }

    @Test
    @DisplayName("Returns ValidationError when creating note resource with empty title")
    void returnsErrorWhenCreatingNoteWithEmptyTitle() {
        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($goalId: ID!) {
                          createResource(goalId: $goalId, input: {
                            type: "note"
                            title: ""
                          }) {
                            id
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .execute();

        assertValidationError(response, "Note resource requires title");
        assertNoResourcesCreated();
    }

    @Test
    @DisplayName("Returns ValidationError when creating note resource with newline title")
    void returnsErrorWhenCreatingNoteWithNewlineTitle() {
        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($goalId: ID!, $title: String) {
                          createResource(goalId: $goalId, input: {
                            type: "note"
                            title: $title
                          }) {
                            id
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .variable("title", "\n")
                .execute();

        assertValidationError(response, "Note resource requires title");
        assertNoResourcesCreated();
    }

    @Test
    @DisplayName("Returns ValidationError when creating resource with title longer than the label limit")
    void returnsErrorWhenCreatingResourceWithLongTitle() {
        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($goalId: ID!) {
                          createResource(goalId: $goalId, input: {
                            type: "note"
                            title: "%s"
                          }) {
                            id
                          }
                        }
                        """.formatted(labelOverLimit()))
                .variable("goalId", goalId)
                .execute();

        assertValidationError(response, labelTooLongMessage("Note resource title"));
        assertNoResourcesCreated();
    }

    @ParameterizedTest(name = "Creates {0} resource with label at the label limit")
    @MethodSource("resourcesWithMaximumLengthLabels")
    void createsResourceWithLabelAtMaximumLength(String resourceKind, String inputFields, String fieldName) {
        graphQlTester.document("""
                        mutation($goalId: ID!) {
                          createResource(goalId: $goalId, input: {
                            %s
                          }) {
                            id
                            title
                            name
                          }
                        }
                        """.formatted(inputFields))
                .variable("goalId", goalId)
                .execute()
                .path("createResource." + fieldName).entity(String.class).isEqualTo(labelAtLimit());
    }

    @ParameterizedTest(name = "Returns ValidationError when creating {0} resource with label longer than the label limit")
    @MethodSource("resourcesWithOversizedLabels")
    void returnsErrorWhenCreatingResourceWithOversizedLabel(String resourceKind, String inputFields, String message) {
        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($goalId: ID!) {
                          createResource(goalId: $goalId, input: {
                            %s
                          }) {
                            id
                          }
                        }
                        """.formatted(inputFields))
                .variable("goalId", goalId)
                .execute();

        assertValidationError(response, message);
        assertNoResourcesCreated();
    }

    @Test
    @DisplayName("Trims whitespace from note title on create")
    void trimsWhitespaceFromNoteTitleOnCreate() {
        graphQlTester.document("""
                        mutation($goalId: ID!) {
                          createResource(goalId: $goalId, input: {
                            type: "note"
                            title: "   Research notes   "
                          }) {
                            id type title
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .execute()
                .path("createResource.title").entity(String.class).isEqualTo("Research notes");
    }

    @Test
    @DisplayName("Creates link resource with required fields only")
    void createsLinkResourceWithRequiredFieldsOnly() {
        graphQlTester.document("""
                        mutation($goalId: ID!) {
                          createResource(goalId: $goalId, input: {
                            type: "link"
                            url: "https://example.com/docs"
                          }) {
                            id type title url
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .execute()
                .path("createResource.id").hasValue()
                .path("createResource.type").entity(String.class).isEqualTo("link")
                .path("createResource.title").entity(String.class).isEqualTo("example")
                .path("createResource.url").entity(String.class).isEqualTo("https://example.com/docs");
    }

    @Test
    @DisplayName("Creates link resource with title generated from domain")
    void createsLinkResourceWithGeneratedTitleFromDomain() {
        graphQlTester.document("""
                        mutation($goalId: ID!) {
                          createResource(goalId: $goalId, input: {
                            type: "link"
                            url: "https://chatgpt.com"
                          }) {
                            id type title url
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .execute()
                .path("createResource.type").entity(String.class).isEqualTo("link")
                .path("createResource.title").entity(String.class).isEqualTo("chatgpt")
                .path("createResource.url").entity(String.class).isEqualTo("https://chatgpt.com");
    }

    @Test
    @DisplayName("Creates link resource with title generated from www domain")
    void createsLinkResourceWithGeneratedTitleFromWwwDomain() {
        graphQlTester.document("""
                        mutation($goalId: ID!) {
                          createResource(goalId: $goalId, input: {
                            type: "link"
                            url: "https://www.chatgpt.com/path"
                          }) {
                            id type title url
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .execute()
                .path("createResource.title").entity(String.class).isEqualTo("chatgpt")
                .path("createResource.url").entity(String.class).isEqualTo("https://www.chatgpt.com/path");
    }

    @ParameterizedTest(name = "Creates link resource with {0} title by generating title from URL")
    @MethodSource("blankTitles")
    void createsLinkResourceWithBlankTitleByGeneratingTitleFromUrl(String label, String title) {
        graphQlTester.document("""
                        mutation($goalId: ID!, $title: String!) {
                          createResource(goalId: $goalId, input: {
                            type: "link"
                            title: $title
                            url: "https://chatgpt.com/docs"
                          }) {
                            id type title url
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .variable("title", title)
                .execute()
                .path("createResource.title").entity(String.class).isEqualTo("chatgpt");
    }

    @Test
    @DisplayName("Trims whitespace from link URL on create")
    void trimsWhitespaceFromLinkUrlOnCreate() {
        graphQlTester.document("""
                        mutation($goalId: ID!) {
                          createResource(goalId: $goalId, input: {
                            type: "link"
                            title: "Docs"
                            url: "  https://example.com/docs  "
                          }) {
                            id type title url
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .execute()
                .path("createResource.url").entity(String.class).isEqualTo("https://example.com/docs");
    }

    @Test
    @DisplayName("Trims whitespace from link title on create")
    void trimsWhitespaceFromLinkTitleOnCreate() {
        graphQlTester.document("""
                        mutation($goalId: ID!) {
                          createResource(goalId: $goalId, input: {
                            type: "link"
                            title: "   Docs   "
                            url: "https://example.com/docs"
                          }) {
                            id type title url
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .execute()
                .path("createResource.title").entity(String.class).isEqualTo("Docs");
    }

    @Test
    @DisplayName("Creates link resource with required and optional fields")
    void createsLinkResourceWithRequiredAndOptionalFields() {
        graphQlTester.document("""
                        mutation($goalId: ID!) {
                          createResource(goalId: $goalId, input: {
                            type: "link"
                            title: "Docs"
                            url: "https://example.com/docs"
                          }) {
                            id type title url
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .execute()
                .path("createResource.type").entity(String.class).isEqualTo("link")
                .path("createResource.title").entity(String.class).isEqualTo("Docs")
                .path("createResource.url").entity(String.class).isEqualTo("https://example.com/docs");
    }

    @Test
    @DisplayName("Returns ValidationError when creating link resource without URL")
    void returnsErrorWhenCreatingLinkWithoutUrl() {
        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($goalId: ID!) {
                          createResource(goalId: $goalId, input: {
                            type: "link"
                            title: "Docs"
                          }) {
                            id
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .execute();

        assertValidationError(response, "Link resource requires URL");
        assertNoResourcesCreated();
    }

    @Test
    @DisplayName("Returns ValidationError when creating link resource with blank URL")
    void returnsErrorWhenCreatingLinkWithBlankUrl() {
        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($goalId: ID!) {
                          createResource(goalId: $goalId, input: {
                            type: "link"
                            title: "Docs"
                            url: "   "
                          }) {
                            id
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .execute();

        assertValidationError(response, "Link resource requires URL");
        assertNoResourcesCreated();
    }

    @Test
    @DisplayName("Returns ValidationError when creating link resource with empty URL")
    void returnsErrorWhenCreatingLinkWithEmptyUrl() {
        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($goalId: ID!) {
                          createResource(goalId: $goalId, input: {
                            type: "link"
                            title: "Docs"
                            url: ""
                          }) {
                            id
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .execute();

        assertValidationError(response, "Link resource requires URL");
        assertNoResourcesCreated();
    }

    @Test
    @DisplayName("Returns ValidationError when creating link resource with newline URL")
    void returnsErrorWhenCreatingLinkWithNewlineUrl() {
        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($goalId: ID!, $url: String) {
                          createResource(goalId: $goalId, input: {
                            type: "link"
                            title: "Docs"
                            url: $url
                          }) {
                            id
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .variable("url", "\n")
                .execute();

        assertValidationError(response, "Link resource requires URL");
        assertNoResourcesCreated();
    }

    @Test
    @DisplayName("Returns ValidationError when creating link resource with non-http URL")
    void returnsErrorWhenCreatingLinkWithNonHttpUrl() {
        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($goalId: ID!) {
                          createResource(goalId: $goalId, input: {
                            type: "link"
                            title: "Local file"
                            url: "file:///tmp/readme.txt"
                          }) {
                            id
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .execute();

        assertValidationError(response, "Link resource URL must be a valid http(s) URL");
        assertNoResourcesCreated();
    }

    @Test
    @DisplayName("Creates link resource with URL at maximum length")
    void createsLinkResourceWithUrlAtMaximumLength() {
        String url = "https://example.com/" + "a".repeat(ResourceService.MAX_LINK_URL_LENGTH - "https://example.com/".length());
        graphQlTester.document("""
                        mutation($goalId: ID!, $url: String!) {
                          createResource(goalId: $goalId, input: {
                            type: "link"
                            url: $url
                          }) {
                            id type url
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .variable("url", url)
                .execute()
                .path("createResource.type").entity(String.class).isEqualTo("link")
                .path("createResource.url").entity(String.class).isEqualTo(url);
    }

    @Test
    @DisplayName("Returns ValidationError when creating link resource with URL exceeding maximum length")
    void returnsErrorWhenCreatingLinkWithOversizedUrl() {
        String url = "https://example.com/" + "a".repeat(ResourceService.MAX_LINK_URL_LENGTH);
        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($goalId: ID!, $url: String!) {
                          createResource(goalId: $goalId, input: {
                            type: "link"
                            url: $url
                          }) {
                            id
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .variable("url", url)
                .execute();

        assertValidationError(response, "Link resource URL must be " + ResourceService.MAX_LINK_URL_LENGTH + " characters or fewer");
        assertNoResourcesCreated();
    }

    @Test
    @DisplayName("Creates image file resource with required fields")
    void createsImageFileResourceWithRequiredFields() {
        graphQlTester.document("""
                        mutation($goalId: ID!, $dataUrl: String!) {
                          createResource(goalId: $goalId, input: {
                            type: "file"
                            title: "Screenshot"
                            mime: "image/png"
                            dataUrl: $dataUrl
                          }) {
                            id type title mime dataUrl
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .variable("dataUrl", PNG_DATA_URL)
                .execute()
                .path("createResource.id").hasValue()
                .path("createResource.type").entity(String.class).isEqualTo("file")
                .path("createResource.title").entity(String.class).isEqualTo("Screenshot")
                .path("createResource.mime").entity(String.class).isEqualTo("image/png")
                .path("createResource.dataUrl").entity(String.class).isEqualTo(PNG_DATA_URL);
    }

    @Test
    @DisplayName("Returns ValidationError when creating image file resource without title")
    void returnsErrorWhenCreatingImageFileWithoutTitle() {
        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($goalId: ID!, $dataUrl: String!) {
                          createResource(goalId: $goalId, input: {
                            type: "file"
                            mime: "image/png"
                            dataUrl: $dataUrl
                          }) {
                            id
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .variable("dataUrl", PNG_DATA_URL)
                .execute();

        assertValidationError(response, "File resource requires title");
        assertNoResourcesCreated();
    }

    @Test
    @DisplayName("Returns ValidationError when creating image file resource without MIME type")
    void returnsErrorWhenCreatingImageFileWithoutMime() {
        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($goalId: ID!, $dataUrl: String!) {
                          createResource(goalId: $goalId, input: {
                            type: "file"
                            title: "Screenshot"
                            dataUrl: $dataUrl
                          }) {
                            id
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .variable("dataUrl", PNG_DATA_URL)
                .execute();

        assertValidationError(response, "File resource requires MIME type");
        assertNoResourcesCreated();
    }

    @Test
    @DisplayName("Returns ValidationError when creating image file resource without data URL")
    void returnsErrorWhenCreatingImageFileWithoutDataUrl() {
        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($goalId: ID!) {
                          createResource(goalId: $goalId, input: {
                            type: "file"
                            title: "Screenshot"
                            mime: "image/png"
                          }) {
                            id
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .execute();

        assertValidationError(response, "File resource requires data URL");
        assertNoResourcesCreated();
    }

    @Test
    @DisplayName("Creates document file resource with required fields")
    void createsDocumentFileResourceWithRequiredFields() {
        graphQlTester.document("""
                        mutation($goalId: ID!, $dataUrl: String!) {
                          createResource(goalId: $goalId, input: {
                            type: "file"
                            title: "Brief"
                            mime: "application/pdf"
                            dataUrl: $dataUrl
                          }) {
                            id type title mime
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .variable("dataUrl", PDF_DATA_URL)
                .execute()
                .path("createResource.type").entity(String.class).isEqualTo("file")
                .path("createResource.mime").entity(String.class).isEqualTo("application/pdf");
    }

    @Test
    @DisplayName("Returns ValidationError when creating file resource without title")
    void returnsErrorWhenCreatingFileWithoutTitle() {
        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($goalId: ID!, $dataUrl: String!) {
                          createResource(goalId: $goalId, input: {
                            type: "file"
                            mime: "application/pdf"
                            dataUrl: $dataUrl
                          }) {
                            id
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .variable("dataUrl", PDF_DATA_URL)
                .execute();

        assertValidationError(response, "File resource requires title");
        assertNoResourcesCreated();
    }

    @Test
    @DisplayName("Returns ValidationError when creating file resource with blank title")
    void returnsErrorWhenCreatingFileWithBlankTitle() {
        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($goalId: ID!, $dataUrl: String!) {
                          createResource(goalId: $goalId, input: {
                            type: "file"
                            title: "   "
                            mime: "application/pdf"
                            dataUrl: $dataUrl
                          }) {
                            id
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .variable("dataUrl", PDF_DATA_URL)
                .execute();

        assertValidationError(response, "File resource requires title");
        assertNoResourcesCreated();
    }

    @Test
    @DisplayName("Returns ValidationError when creating file resource with empty title")
    void returnsErrorWhenCreatingFileWithEmptyTitle() {
        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($goalId: ID!, $dataUrl: String!) {
                          createResource(goalId: $goalId, input: {
                            type: "file"
                            title: ""
                            mime: "application/pdf"
                            dataUrl: $dataUrl
                          }) {
                            id
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .variable("dataUrl", PDF_DATA_URL)
                .execute();

        assertValidationError(response, "File resource requires title");
        assertNoResourcesCreated();
    }

    @Test
    @DisplayName("Returns ValidationError when creating file resource with newline title")
    void returnsErrorWhenCreatingFileWithNewlineTitle() {
        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($goalId: ID!, $title: String, $dataUrl: String!) {
                          createResource(goalId: $goalId, input: {
                            type: "file"
                            title: $title
                            mime: "application/pdf"
                            dataUrl: $dataUrl
                          }) {
                            id
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .variable("title", "\n")
                .variable("dataUrl", PDF_DATA_URL)
                .execute();

        assertValidationError(response, "File resource requires title");
        assertNoResourcesCreated();
    }

    @Test
    @DisplayName("Returns ValidationError when creating file resource without MIME type")
    void returnsErrorWhenCreatingFileWithoutMime() {
        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($goalId: ID!, $dataUrl: String!) {
                          createResource(goalId: $goalId, input: {
                            type: "file"
                            title: "Brief"
                            dataUrl: $dataUrl
                          }) {
                            id
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .variable("dataUrl", PDF_DATA_URL)
                .execute();

        assertValidationError(response, "File resource requires MIME type");
        assertNoResourcesCreated();
    }

    @Test
    @DisplayName("Returns ValidationError when creating file resource without data URL")
    void returnsErrorWhenCreatingFileWithoutDataUrl() {
        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($goalId: ID!) {
                          createResource(goalId: $goalId, input: {
                            type: "file"
                            title: "Brief"
                            mime: "application/pdf"
                          }) {
                            id
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .execute();

        assertValidationError(response, "File resource requires data URL");
        assertNoResourcesCreated();
    }

    @ParameterizedTest(name = "Returns ValidationError when creating file resource with {0} MIME type")
    @MethodSource("blankValues")
    void returnsErrorWhenCreatingFileWithBlankMime(String label, String mime) {
        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($goalId: ID!, $mime: String, $dataUrl: String!) {
                          createResource(goalId: $goalId, input: {
                            type: "file"
                            title: "Brief"
                            mime: $mime
                            dataUrl: $dataUrl
                          }) {
                            id
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .variable("mime", mime)
                .variable("dataUrl", PDF_DATA_URL)
                .execute();

        assertValidationError(response, "File resource requires MIME type");
        assertNoResourcesCreated();
    }

    @ParameterizedTest(name = "Returns ValidationError when creating file resource with {0} data URL")
    @MethodSource("blankValues")
    void returnsErrorWhenCreatingFileWithBlankDataUrl(String label, String dataUrl) {
        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($goalId: ID!, $dataUrl: String) {
                          createResource(goalId: $goalId, input: {
                            type: "file"
                            title: "Brief"
                            mime: "application/pdf"
                            dataUrl: $dataUrl
                          }) {
                            id
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .variable("dataUrl", dataUrl)
                .execute();

        assertValidationError(response, "File resource requires data URL");
        assertNoResourcesCreated();
    }

    @Test
    @DisplayName("Returns ValidationError when creating file resource with unsupported MIME type")
    void returnsErrorWhenCreatingFileWithUnsupportedMime() {
        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($goalId: ID!) {
                          createResource(goalId: $goalId, input: {
                            type: "file"
                            title: "Archive"
                            mime: "application/zip"
                            dataUrl: "data:application/zip;base64,aGVsbG8="
                          }) {
                            id
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .execute();

        assertValidationError(response, "File resource MIME type must be an image or PDF");
        assertNoResourcesCreated();
    }

    @Test
    @DisplayName("Returns ValidationError when file data URL MIME does not match")
    void returnsErrorWhenFileDataUrlMimeDoesNotMatch() {
        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($goalId: ID!, $dataUrl: String!) {
                          createResource(goalId: $goalId, input: {
                            type: "file"
                            title: "Screenshot"
                            mime: "image/png"
                            dataUrl: $dataUrl
                          }) {
                            id
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .variable("dataUrl", PDF_DATA_URL)
                .execute();

        assertValidationError(response, "File resource data URL must match MIME type");
        assertNoResourcesCreated();
    }

    @Test
    @DisplayName("Returns ValidationError when file data URL base64 is malformed")
    void returnsErrorWhenFileDataUrlBase64IsMalformed() {
        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($goalId: ID!) {
                          createResource(goalId: $goalId, input: {
                            type: "file"
                            title: "Screenshot"
                            mime: "image/png"
                            dataUrl: "data:image/png;base64,not base64!"
                          }) {
                            id
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .execute();

        assertValidationError(response, "File resource data URL must be valid base64");
        assertNoResourcesCreated();
    }

    @Test
    @DisplayName("Returns ValidationError when file resource is larger than 5 MB")
    void returnsErrorWhenFileResourceIsTooLarge() {
        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($goalId: ID!, $dataUrl: String!) {
                          createResource(goalId: $goalId, input: {
                            type: "file"
                            title: "Huge image"
                            mime: "image/png"
                            dataUrl: $dataUrl
                          }) {
                            id
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .variable("dataUrl", oversizedPngDataUrl())
                .execute();

        assertValidationError(response, "File resource must be 5 MB or smaller");
        assertNoResourcesCreated();
    }

    @Test
    @DisplayName("Creates email resource with required fields only")
    void createsEmailResourceWithRequiredFieldsOnly() {
        graphQlTester.document("""
                        mutation($goalId: ID!) {
                          createResource(goalId: $goalId, input: {
                            type: "email"
                            email: "ada@example.com"
                          }) {
                            id type name role email phone
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .execute()
                .path("createResource.id").hasValue()
                .path("createResource.type").entity(String.class).isEqualTo("email")
                .path("createResource.email").entity(String.class).isEqualTo("ada@example.com")
                .path("createResource.name").entity(String.class).isEqualTo("ada@example.com")
                .path("createResource.role").valueIsNull()
                .path("createResource.phone").valueIsNull();
    }

    @Test
    @DisplayName("Trims whitespace from email name on create")
    void trimsWhitespaceFromEmailNameOnCreate() {
        graphQlTester.document("""
                        mutation($goalId: ID!) {
                          createResource(goalId: $goalId, input: {
                            type: "email"
                            name: "   Ada Lovelace   "
                            email: "ada@example.com"
                          }) {
                            id type name email
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .execute()
                .path("createResource.name").entity(String.class).isEqualTo("Ada Lovelace");
    }

    @Test
    @DisplayName("Returns ValidationError when creating email resource with name longer than the label limit")
    void returnsErrorWhenCreatingEmailWithLongName() {
        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($goalId: ID!) {
                          createResource(goalId: $goalId, input: {
                            type: "email"
                            name: "%s"
                            email: "ada@example.com"
                          }) {
                            id
                          }
                        }
                        """.formatted(labelOverLimit()))
                .variable("goalId", goalId)
                .execute();

        assertValidationError(response, labelTooLongMessage("Email resource name"));
        assertNoResourcesCreated();
    }

    @ParameterizedTest(name = "Creates email resource with {0} name by generating name from email")
    @MethodSource("blankNames")
    void createsEmailResourceWithBlankNameByGeneratingNameFromEmail(String label, String name) {
        graphQlTester.document("""
                        mutation($goalId: ID!, $name: String) {
                          createResource(goalId: $goalId, input: {
                            type: "email"
                            name: $name
                            email: "ada@example.com"
                          }) {
                            id type name email
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .variable("name", name)
                .execute()
                .path("createResource.name").entity(String.class).isEqualTo("ada@example.com")
                .path("createResource.email").entity(String.class).isEqualTo("ada@example.com");
    }

    @Test
    @DisplayName("Trims whitespace from email email on create")
    void trimsWhitespaceFromEmailEmailOnCreate() {
        graphQlTester.document("""
                        mutation($goalId: ID!) {
                          createResource(goalId: $goalId, input: {
                            type: "email"
                            email: "  ada@example.com  "
                          }) {
                            id type email
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .execute()
                .path("createResource.email").entity(String.class).isEqualTo("ada@example.com");
    }

    @Test
    @DisplayName("Trims whitespace from email phone on create")
    void trimsWhitespaceFromEmailPhoneOnCreate() {
        graphQlTester.document("""
                        mutation($goalId: ID!) {
                          createResource(goalId: $goalId, input: {
                            type: "email"
                            name: "Ada Lovelace"
                            email: "ada@example.com"
                            phone: "  +46 70 123 45 67  "
                          }) {
                            id type phone
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .execute()
                .path("createResource.phone").entity(String.class).isEqualTo("+46 70 123 45 67");
    }

    @Test
    @DisplayName("Creates email resource with required and optional email fields")
    void createsEmailResourceWithRequiredAndOptionalFields() {
        graphQlTester.document("""
                        mutation($goalId: ID!) {
                          createResource(goalId: $goalId, input: {
                            type: "email"
                            name: "Ada Lovelace"
                            role: "Mentor"
                            email: "ada@example.com"
                            phone: "+46 70 123 45 67"
                          }) {
                            id type name role email phone
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .execute()
                .path("createResource.type").entity(String.class).isEqualTo("email")
                .path("createResource.name").entity(String.class).isEqualTo("Ada Lovelace")
                .path("createResource.role").entity(String.class).isEqualTo("Mentor")
                .path("createResource.email").entity(String.class).isEqualTo("ada@example.com")
                .path("createResource.phone").entity(String.class).isEqualTo("+46 70 123 45 67");
    }

    @Test
    @DisplayName("Returns ValidationError when creating email resource without email")
    void returnsErrorWhenCreatingEmailWithoutEmail() {
        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($goalId: ID!) {
                          createResource(goalId: $goalId, input: {
                            type: "email"
                          }) {
                            id
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .execute();

        assertValidationError(response, "Email resource requires email");
        assertNoResourcesCreated();
    }

    @Test
    @DisplayName("Returns ValidationError when creating email resource with blank email")
    void returnsErrorWhenCreatingEmailWithBlankEmail() {
        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($goalId: ID!) {
                          createResource(goalId: $goalId, input: {
                            type: "email"
                            email: "   "
                          }) {
                            id
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .execute();

        assertValidationError(response, "Email resource requires email");
        assertNoResourcesCreated();
    }

    @Test
    @DisplayName("Returns ValidationError when creating email resource with empty email")
    void returnsErrorWhenCreatingEmailWithEmptyEmail() {
        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($goalId: ID!) {
                          createResource(goalId: $goalId, input: {
                            type: "email"
                            email: ""
                          }) {
                            id
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .execute();

        assertValidationError(response, "Email resource requires email");
        assertNoResourcesCreated();
    }

    @Test
    @DisplayName("Returns ValidationError when creating email resource with newline email")
    void returnsErrorWhenCreatingEmailWithNewlineEmail() {
        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($goalId: ID!, $email: String) {
                          createResource(goalId: $goalId, input: {
                            type: "email"
                            email: $email
                          }) {
                            id
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .variable("email", "\n")
                .execute();

        assertValidationError(response, "Email resource requires email");
        assertNoResourcesCreated();
    }

    @Test
    @DisplayName("Returns ValidationError when creating resource with empty input")
    void returnsErrorWhenCreatingResourceWithEmptyInput() {
        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($goalId: ID!) {
                          createResource(goalId: $goalId, input: {}) {
                            id
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .execute();

        assertGraphQlValidationError(response, "missing required fields", "type");
        assertNoResourcesCreated();
    }

    @Test
    @DisplayName("Returns ValidationError when creating email resource with invalid email")
    void returnsErrorWhenCreatingEmailWithInvalidEmail() {
        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($goalId: ID!) {
                          createResource(goalId: $goalId, input: {
                            type: "email"
                            email: "not-an-email"
                          }) {
                            id
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .execute();

        assertValidationError(response, "Email resource email must be valid");
        assertNoResourcesCreated();
    }

    @Test
    @DisplayName("Returns ValidationError when resource fields do not match type on create")
    void returnsErrorWhenCreateFieldsDoNotMatchType() {
        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($goalId: ID!) {
                          createResource(goalId: $goalId, input: {
                            type: "note"
                            title: "Note"
                            email: "ada@example.com"
                          }) {
                            id
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .execute();

        assertValidationError(response, "Field 'email' is not allowed for note resources");
        assertNoResourcesCreated();
    }

    @Test
    @DisplayName("Returns ValidationError when resource fields do not match type on update")
    void returnsErrorWhenUpdateFieldsDoNotMatchType() {
        String resourceId = createLinkResource(goalId);

        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($id: ID!) {
                          updateResource(id: $id, input: { body: "Not a link field" }) {
                            id
                          }
                        }
                        """)
                .variable("id", resourceId)
                .execute();

        assertValidationError(response, "Field 'body' is not allowed for link resources");
    }

    @Test
    @DisplayName("Returns error when resource type is the old 'contact' alias")
    void rejectsOldContactAlias() {
        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($goalId: ID!) {
                          createResource(goalId: $goalId, input: {
                            type: "contact"
                            name: "Old alias"
                          }) {
                            id
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .execute();

        response.errors()
                .satisfy(errors -> assertThat(errors)
                        .anyMatch(error -> error.getMessage().contains("Unknown resource type: contact")));
    }

    @ParameterizedTest(name = "Rejects {2} when creating {0} resource")
    @MethodSource("disallowedResourceCreateFields")
    void returnsErrorWhenCreateFieldDoesNotMatchResourceType(String resourceKind, String validFields,
                                                             String disallowedField, String disallowedInput) {
        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($goalId: ID!) {
                          createResource(goalId: $goalId, input: {
                            %s
                            %s
                          }) {
                            id
                          }
                        }
                        """.formatted(validFields, disallowedInput))
                .variable("goalId", goalId)
                .execute();

        assertValidationError(response, "Field '" + disallowedField + "' is not allowed for " + resourceKind + " resources");
        assertNoResourcesCreated();
    }

    @ParameterizedTest(name = "Rejects {2} when updating {0} resource")
    @MethodSource("disallowedResourceUpdateFields")
    void returnsErrorWhenUpdateFieldDoesNotMatchResourceType(String resourceKind, String existingInput,
                                                             String disallowedField, String disallowedInput) {
        String resourceId = createResource(goalId, existingInput);

        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($id: ID!) {
                          updateResource(id: $id, input: {
                            %s
                          }) {
                            id
                          }
                        }
                        """.formatted(disallowedInput))
                .variable("id", resourceId)
                .execute();

        assertValidationError(response, "Field '" + disallowedField + "' is not allowed for " + resourceKind + " resources");
    }

    @Test
    @DisplayName("Updates link resource URL when field matches type")
    void updatesLinkResourceUrl() {
        String resourceId = createLinkResource(goalId);

        graphQlTester.document("""
                        mutation($id: ID!) {
                          updateResource(id: $id, input: { url: "https://example.com/updated" }) {
                            id type url
                          }
                        }
                        """)
                .variable("id", resourceId)
                .execute()
                .path("updateResource.url").entity(String.class).isEqualTo("https://example.com/updated");
    }

    @Test
    @DisplayName("Trims whitespace from link URL on update")
    void trimsWhitespaceFromLinkUrlOnUpdate() {
        String resourceId = createLinkResource(goalId);

        graphQlTester.document("""
                        mutation($id: ID!) {
                          updateResource(id: $id, input: { url: "  https://example.com/updated  " }) {
                            id type url
                          }
                        }
                        """)
                .variable("id", resourceId)
                .execute()
                .path("updateResource.url").entity(String.class).isEqualTo("https://example.com/updated");
    }

    @Test
    @DisplayName("Trims whitespace from link title on update")
    void trimsWhitespaceFromLinkTitleOnUpdate() {
        String resourceId = createLinkResource(goalId);

        graphQlTester.document("""
                        mutation($id: ID!) {
                          updateResource(id: $id, input: { title: "   Docs updated   " }) {
                            id type title
                          }
                        }
                        """)
                .variable("id", resourceId)
                .execute()
                .path("updateResource.title").entity(String.class).isEqualTo("Docs updated");
    }

    @Test
    @DisplayName("Regenerates link title when an autogenerated link URL changes")
    void regeneratesLinkTitleWhenAutogeneratedLinkUrlChanges() {
        String resourceId = createResource(goalId, """
                            type: "link"
                            url: "https://example.com/docs"
                """);

        graphQlTester.document("""
                        mutation($id: ID!) {
                          updateResource(id: $id, input: { url: "https://chatgpt.com" }) {
                            id type title url
                          }
                        }
                        """)
                .variable("id", resourceId)
                .execute()
                .path("updateResource.title").entity(String.class).isEqualTo("chatgpt")
                .path("updateResource.url").entity(String.class).isEqualTo("https://chatgpt.com");
    }

    @Test
    @DisplayName("Preserves manual link title when URL changes")
    void preservesManualLinkTitleWhenUrlChanges() {
        String resourceId = createLinkResource(goalId);

        graphQlTester.document("""
                        mutation($id: ID!) {
                          updateResource(id: $id, input: { url: "https://chatgpt.com" }) {
                            id type title url
                          }
                        }
                        """)
                .variable("id", resourceId)
                .execute()
                .path("updateResource.title").entity(String.class).isEqualTo("Docs")
                .path("updateResource.url").entity(String.class).isEqualTo("https://chatgpt.com");
    }

    @ParameterizedTest(name = "Returns ValidationError when updating {0} resource label past the label limit")
    @MethodSource("resourcesWithOversizedLabelUpdates")
    void returnsErrorWhenUpdatingResourceWithOversizedLabel(String resourceKind, String existingInput,
                                                            String updateInput, String fieldName,
                                                            String previousValue, String message) {
        String resourceId = createResource(goalId, existingInput);

        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($id: ID!) {
                          updateResource(id: $id, input: {
                            %s
                          }) {
                            id
                          }
                        }
                        """.formatted(updateInput))
                .variable("id", resourceId)
                .execute();

        assertValidationError(response, message);
        assertResourceField(resourceId, fieldName, previousValue);
    }

    @Test
    @DisplayName("Updates note resource body when body already exists")
    void updatesNoteResourceBodyWhenBodyAlreadyExists() {
        String resourceId = createResource(goalId, """
                            type: "note"
                            title: "Research notes"
                            body: "<p>Draft note.</p>"
                """);

        graphQlTester.document("""
                        mutation($id: ID!) {
                          updateResource(id: $id, input: { body: "<p>Updated note.</p>" }) {
                            id type title body
                          }
                        }
                        """)
                .variable("id", resourceId)
                .execute()
                .path("updateResource.type").entity(String.class).isEqualTo("note")
                .path("updateResource.title").entity(String.class).isEqualTo("Research notes")
                .path("updateResource.body").entity(String.class).isEqualTo("<p>Updated note.</p>");
    }

    @Test
    @DisplayName("Adds note resource body when body is empty")
    void addsNoteResourceBodyWhenBodyIsEmpty() {
        String resourceId = createResource(goalId, """
                            type: "note"
                            title: "Research notes"
                """);

        graphQlTester.document("""
                        mutation($id: ID!) {
                          updateResource(id: $id, input: { body: "<p>New note text.</p>" }) {
                            id type title body
                          }
                        }
                        """)
                .variable("id", resourceId)
                .execute()
                .path("updateResource.type").entity(String.class).isEqualTo("note")
                .path("updateResource.title").entity(String.class).isEqualTo("Research notes")
                .path("updateResource.body").entity(String.class).isEqualTo("<p>New note text.</p>");
    }

    @Test
    @DisplayName("Clears note resource body with empty text")
    void clearsNoteResourceBodyWithEmptyText() {
        String resourceId = createResource(goalId, """
                            type: "note"
                            title: "Research notes"
                            body: "<p>Draft note.</p>"
                """);

        graphQlTester.document("""
                        mutation($id: ID!) {
                          updateResource(id: $id, input: { body: "" }) {
                            id type title body
                          }
                        }
                        """)
                .variable("id", resourceId)
                .execute()
                .path("updateResource.type").entity(String.class).isEqualTo("note")
                .path("updateResource.title").entity(String.class).isEqualTo("Research notes")
                .path("updateResource.body").entity(String.class).isEqualTo("");
    }

    @Test
    @DisplayName("Updates note resource body to maximum length")
    void updatesNoteResourceBodyToMaximumLength() {
        String resourceId = createResource(goalId, """
                            type: "note"
                            title: "Research notes"
                            body: "<p>Draft note.</p>"
                """);
        String body = noteBodyAtLimit();

        graphQlTester.document("""
                        mutation($id: ID!, $body: String!) {
                          updateResource(id: $id, input: { body: $body }) {
                            id type title body
                          }
                        }
                        """)
                .variable("id", resourceId)
                .variable("body", body)
                .execute()
                .path("updateResource.type").entity(String.class).isEqualTo("note")
                .path("updateResource.title").entity(String.class).isEqualTo("Research notes")
                .path("updateResource.body").entity(String.class).isEqualTo(body);
    }

    @Test
    @DisplayName("Returns ValidationError when updating note resource with oversized body")
    void returnsErrorWhenUpdatingNoteWithOversizedBody() {
        String resourceId = createResource(goalId, """
                            type: "note"
                            title: "Research notes"
                            body: "<p>Draft note.</p>"
                """);

        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($id: ID!, $body: String!) {
                          updateResource(id: $id, input: { body: $body }) {
                            id
                          }
                        }
                        """)
                .variable("id", resourceId)
                .variable("body", oversizedNoteBody())
                .execute();

        assertValidationError(response, maxNoteBodyLengthMessage());
        assertResourceBody(resourceId, "<p>Draft note.</p>");
    }

    @Test
    @DisplayName("Clears note resource body with explicit null")
    void clearsNoteResourceBodyWithExplicitNull() {
        String resourceId = createResource(goalId, """
                            type: "note"
                            title: "Research notes"
                            body: "<p>Draft note.</p>"
                """);

        graphQlTester.document("""
                        mutation($id: ID!) {
                          updateResource(id: $id, input: { body: null }) {
                            id type title body
                          }
                        }
                        """)
                .variable("id", resourceId)
                .execute()
                .path("updateResource.type").entity(String.class).isEqualTo("note")
                .path("updateResource.title").entity(String.class).isEqualTo("Research notes")
                .path("updateResource.body").valueIsNull();
    }

    @Test
    @DisplayName("Returns ValidationError when updating note resource with link field")
    void returnsErrorWhenUpdatingNoteWithLinkField() {
        String resourceId = createResource(goalId, """
                            type: "note"
                            title: "Research notes"
                            body: "<p>Draft note.</p>"
                """);

        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($id: ID!) {
                          updateResource(id: $id, input: { url: "https://example.com/not-a-note-field" }) {
                            id
                          }
                        }
                        """)
                .variable("id", resourceId)
                .execute();

        assertValidationError(response, "Field 'url' is not allowed for note resources");
        assertResourceBody(resourceId, "<p>Draft note.</p>");
    }

    @Test
    @DisplayName("Trims whitespace from email email on update")
    void trimsWhitespaceFromEmailEmailOnUpdate() {
        String resourceId = createResource(goalId, """
                            type: "email"
                            email: "ada@example.com"
                """);

        graphQlTester.document("""
                        mutation($id: ID!) {
                          updateResource(id: $id, input: { email: "  ada@test.io  " }) {
                            id type email
                          }
                        }
                        """)
                .variable("id", resourceId)
                .execute()
                .path("updateResource.email").entity(String.class).isEqualTo("ada@test.io");
    }

    @Test
    @DisplayName("Returns ValidationError when updating link resource with invalid URL")
    void returnsErrorWhenUpdatingLinkWithInvalidUrl() {
        String resourceId = createLinkResource(goalId);

        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($id: ID!) {
                          updateResource(id: $id, input: { url: "not-a-url" }) {
                            id
                          }
                        }
                        """)
                .variable("id", resourceId)
                .execute();

        assertValidationError(response, "Link resource URL must be a valid http(s) URL");
        assertResourceUrl(resourceId, "https://example.com/docs");
    }

    @Test
    @DisplayName("Returns ValidationError when updating link resource with blank URL")
    void returnsErrorWhenUpdatingLinkWithBlankUrl() {
        String resourceId = createLinkResource(goalId);

        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($id: ID!) {
                          updateResource(id: $id, input: { url: "   " }) {
                            id
                          }
                        }
                        """)
                .variable("id", resourceId)
                .execute();

        assertValidationError(response, "Link resource requires URL");
        assertResourceUrl(resourceId, "https://example.com/docs");
    }

    @Test
    @DisplayName("Updates email resource email")
    void updatesEmailResourceEmail() {
        String resourceId = createResource(goalId, """
                            type: "email"
                            name: "Ada Lovelace"
                            email: "ada@example.com"
                """);

        graphQlTester.document("""
                        mutation($id: ID!) {
                          updateResource(id: $id, input: { email: "ada.updated@example.com" }) {
                            id type name email
                          }
                        }
                        """)
                .variable("id", resourceId)
                .execute()
                .path("updateResource.type").entity(String.class).isEqualTo("email")
                .path("updateResource.name").entity(String.class).isEqualTo("Ada Lovelace")
                .path("updateResource.email").entity(String.class).isEqualTo("ada.updated@example.com");
    }

    @Test
    @DisplayName("Regenerates email name when an autogenerated email address changes")
    void regeneratesEmailNameWhenAutogeneratedEmailChanges() {
        String resourceId = createResource(goalId, """
                            type: "email"
                            email: "ada@example.com"
                """);

        graphQlTester.document("""
                        mutation($id: ID!) {
                          updateResource(id: $id, input: { email: "ada.updated@test.io" }) {
                            id type name email
                          }
                        }
                        """)
                .variable("id", resourceId)
                .execute()
                .path("updateResource.name").entity(String.class).isEqualTo("ada.updated@test.io")
                .path("updateResource.email").entity(String.class).isEqualTo("ada.updated@test.io");
    }

    @Test
    @DisplayName("Preserves manual email name when email address changes")
    void preservesManualEmailNameWhenEmailChanges() {
        String resourceId = createResource(goalId, """
                            type: "email"
                            name: "Ada Lovelace"
                            email: "ada@example.com"
                """);

        graphQlTester.document("""
                        mutation($id: ID!) {
                          updateResource(id: $id, input: { email: "ada.updated@test.io" }) {
                            id type name email
                          }
                        }
                        """)
                .variable("id", resourceId)
                .execute()
                .path("updateResource.name").entity(String.class).isEqualTo("Ada Lovelace")
                .path("updateResource.email").entity(String.class).isEqualTo("ada.updated@test.io");
    }

    @Test
    @DisplayName("Returns ValidationError when updating email resource with invalid email")
    void returnsErrorWhenUpdatingEmailWithInvalidEmail() {
        String resourceId = createResource(goalId, """
                            type: "email"
                            email: "ada@example.com"
                """);

        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($id: ID!) {
                          updateResource(id: $id, input: { email: "not-an-email" }) {
                            id
                          }
                        }
                        """)
                .variable("id", resourceId)
                .execute();

        assertValidationError(response, "Email resource email must be valid");
        assertResourceEmail(resourceId, "ada@example.com");
    }

    @Test
    @DisplayName("Returns ValidationError when updating email resource with blank email")
    void returnsErrorWhenUpdatingEmailWithBlankEmail() {
        String resourceId = createResource(goalId, """
                            type: "email"
                            email: "ada@example.com"
                """);

        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($id: ID!) {
                          updateResource(id: $id, input: { email: "   " }) {
                            id
                          }
                        }
                        """)
                .variable("id", resourceId)
                .execute();

        assertValidationError(response, "Email resource requires email");
        assertResourceEmail(resourceId, "ada@example.com");
    }

    @Test
    @DisplayName("Updates image file resource data URL")
    void updatesImageFileResourceDataUrl() {
        String resourceId = createResource(goalId, """
                            type: "file"
                            title: "Screenshot"
                            mime: "image/png"
                            dataUrl: "data:image/png;base64,aGVsbG8="
                """);

        graphQlTester.document("""
                        mutation($id: ID!, $dataUrl: String!) {
                          updateResource(id: $id, input: { dataUrl: $dataUrl }) {
                            id type title mime dataUrl
                          }
                        }
                        """)
                .variable("id", resourceId)
                .variable("dataUrl", UPDATED_PNG_DATA_URL)
                .execute()
                .path("updateResource.type").entity(String.class).isEqualTo("file")
                .path("updateResource.mime").entity(String.class).isEqualTo("image/png")
                .path("updateResource.dataUrl").entity(String.class).isEqualTo(UPDATED_PNG_DATA_URL);
    }

    @Test
    @DisplayName("Returns ValidationError when updating image file data URL MIME does not match")
    void returnsErrorWhenUpdatingImageFileDataUrlMimeDoesNotMatch() {
        String resourceId = createResource(goalId, """
                            type: "file"
                            title: "Screenshot"
                            mime: "image/png"
                            dataUrl: "data:image/png;base64,aGVsbG8="
                """);

        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($id: ID!, $dataUrl: String!) {
                          updateResource(id: $id, input: { dataUrl: $dataUrl }) {
                            id
                          }
                        }
                        """)
                .variable("id", resourceId)
                .variable("dataUrl", PDF_DATA_URL)
                .execute();

        assertValidationError(response, "File resource data URL must match MIME type");
        assertResourceDataUrl(resourceId, PNG_DATA_URL);
    }

    @Test
    @DisplayName("Trims whitespace from file resource title on create")
    void trimsWhitespaceFromFileTitleOnCreate() {
        graphQlTester.document("""
                        mutation($goalId: ID!) {
                          createResource(goalId: $goalId, input: {
                            type: "file"
                            title: "   Test File   "
                            mime: "application/pdf"
                            dataUrl: "data:application/pdf;base64,JVBERi0xLjQ="
                          }) {
                            id type title mime dataUrl
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .execute()
                .path("createResource.title").entity(String.class).isEqualTo("Test File");
    }

    @Test
    @DisplayName("Trims whitespace from file resource title on update")
    void trimsWhitespaceFromFileTitleOnUpdate() {
        String resourceId = createResource(goalId, """
                            type: "file"
                            title: "Test File"
                            mime: "application/pdf"
                            dataUrl: "data:application/pdf;base64,JVBERi0xLjQ="
                """);

        graphQlTester.document("""
                        mutation($id: ID!) {
                          updateResource(id: $id, input: {
                            title: "   Updated Title   "
                          }) {
                            id type title
                          }
                        }
                        """)
                .variable("id", resourceId)
                .execute()
                .path("updateResource.title").entity(String.class).isEqualTo("Updated Title");
    }

    @Test
    @DisplayName("Updates PDF file resource data URL")
    void updatesPdfFileResourceDataUrl() {
        String resourceId = createResource(goalId, """
                            type: "file"
                            title: "Brief"
                            mime: "application/pdf"
                            dataUrl: "data:application/pdf;base64,JVBERi0xLjQ="
                """);

        graphQlTester.document("""
                        mutation($id: ID!, $dataUrl: String!) {
                          updateResource(id: $id, input: { dataUrl: $dataUrl }) {
                            id type title mime dataUrl
                          }
                        }
                        """)
                .variable("id", resourceId)
                .variable("dataUrl", UPDATED_PDF_DATA_URL)
                .execute()
                .path("updateResource.type").entity(String.class).isEqualTo("file")
                .path("updateResource.mime").entity(String.class).isEqualTo("application/pdf")
                .path("updateResource.dataUrl").entity(String.class).isEqualTo(UPDATED_PDF_DATA_URL);
    }

    @Test
    @DisplayName("Returns ValidationError when updating PDF file data URL MIME does not match")
    void returnsErrorWhenUpdatingPdfFileDataUrlMimeDoesNotMatch() {
        String resourceId = createResource(goalId, """
                            type: "file"
                            title: "Brief"
                            mime: "application/pdf"
                            dataUrl: "data:application/pdf;base64,JVBERi0xLjQ="
                """);

        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($id: ID!, $dataUrl: String!) {
                          updateResource(id: $id, input: { dataUrl: $dataUrl }) {
                            id
                          }
                        }
                        """)
                .variable("id", resourceId)
                .variable("dataUrl", PNG_DATA_URL)
                .execute();

        assertValidationError(response, "File resource data URL must match MIME type");
        assertResourceDataUrl(resourceId, PDF_DATA_URL);
    }

    @Test
    @DisplayName("Returns ValidationError when updating file resource with unsupported MIME type")
    void returnsErrorWhenUpdatingFileWithUnsupportedMime() {
        String resourceId = createResource(goalId, """
                            type: "file"
                            title: "Screenshot"
                            mime: "image/png"
                            dataUrl: "data:image/png;base64,aGVsbG8="
                """);

        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($id: ID!) {
                          updateResource(id: $id, input: {
                            mime: "application/zip"
                            dataUrl: "data:application/zip;base64,aGVsbG8="
                          }) {
                            id
                          }
                        }
                        """)
                .variable("id", resourceId)
                .execute();

        assertValidationError(response, "File resource MIME type must be an image or PDF");
        assertResourceMime(resourceId, "image/png");
        assertResourceDataUrl(resourceId, PNG_DATA_URL);
    }

    @ParameterizedTest(name = "Deletes {0} resource")
    @MethodSource("deletableResources")
    void deletesResource(String resourceKind, String inputFields) {
        String resourceId = createResource(goalId, inputFields);

        assertDeleteResourceSucceeds(resourceId);
        assertResourceNotFound(resourceId);
        assertNoResourcesCreated();
    }

    @Test
    @DisplayName("Returns NOT_FOUND error when creating resource for non-existent goal")
    void returnsErrorWhenCreatingResourceForNonExistentGoal() {
        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($goalId: ID!) {
                          createResource(goalId: $goalId, input: {
                            type: "note"
                            title: "Note"
                          }) {
                            id
                          }
                        }
                        """)
                .variable("goalId", NON_EXISTENT_ID)
                .execute();

        assertNotFound(response, "Goal not found: " + NON_EXISTENT_ID);
    }

    @Test
    @DisplayName("Returns NOT_FOUND error when querying resources for non-existent goal")
    void returnsErrorWhenQueryingResourcesForNonExistentGoal() {
        GraphQlTester.Response response = graphQlTester.document("""
                        query($goalId: ID!) {
                          resourcesByGoal(goalId: $goalId) { id }
                        }
                        """)
                .variable("goalId", NON_EXISTENT_ID)
                .execute();

        assertNotFound(response, "Goal not found: " + NON_EXISTENT_ID);
    }

    @Test
    @DisplayName("Returns NOT_FOUND error when querying non-existent resource")
    void returnsErrorWhenQueryingNonExistentResource() {
        GraphQlTester.Response response = graphQlTester.document("""
                        query($id: ID!) {
                          resourceById(id: $id) { id }
                        }
                        """)
                .variable("id", NON_EXISTENT_ID)
                .execute();

        assertNotFound(response, "Resource not found: " + NON_EXISTENT_ID);
    }

    @Test
    @DisplayName("Returns NOT_FOUND error when updating non-existent resource")
    void returnsErrorWhenUpdatingNonExistentResource() {
        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($id: ID!) {
                          updateResource(id: $id, input: { title: "Updated" }) { id }
                        }
                        """)
                .variable("id", NON_EXISTENT_ID)
                .execute();

        assertNotFound(response, "Resource not found: " + NON_EXISTENT_ID);
    }

    @Test
    @DisplayName("Returns NOT_FOUND error when deleting non-existent resource")
    void returnsErrorWhenDeletingNonExistentResource() {
        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($id: ID!) {
                          deleteResource(id: $id)
                        }
                        """)
                .variable("id", NON_EXISTENT_ID)
                .execute();

        assertNotFound(response, "Resource not found: " + NON_EXISTENT_ID);
    }

    private String createLinkResource(String goalId) {
        return createResource(goalId, """
                            type: "link"
                            title: "Docs"
                            url: "https://example.com/docs"
                """);
    }

    private String createResource(String goalId, String inputFields) {
        return graphQlTester.document("""
                        mutation($goalId: ID!) {
                          createResource(goalId: $goalId, input: {
                            %s
                          }) {
                            id
                          }
                        }
                        """.formatted(inputFields))
                .variable("goalId", goalId)
                .execute()
                .path("createResource.id").entity(String.class).get();
    }

    private static Stream<Arguments> deletableResources() {
        return Stream.of(
                Arguments.of("note", """
                            type: "note"
                            title: "Research notes"
                        """),
                Arguments.of("link", """
                            type: "link"
                            title: "Docs"
                            url: "https://example.com/docs"
                        """),
                Arguments.of("image file", """
                            type: "file"
                            title: "Screenshot"
                            mime: "image/png"
                            dataUrl: "data:image/png;base64,aGVsbG8="
                        """),
                Arguments.of("document file", """
                            type: "file"
                            title: "Brief"
                            mime: "application/pdf"
                            dataUrl: "data:application/pdf;base64,JVBERi0xLjQ="
                        """),
                Arguments.of("email", """
                            type: "email"
                            email: "ada@example.com"
                        """)
        );
    }

    private static Stream<Arguments> resourcesWithMaximumLengthLabels() {
        String label = labelAtLimit();
        return Stream.of(
                Arguments.of("note", """
                            type: "note"
                            title: "%s"
                        """.formatted(label), "title"),
                Arguments.of("link", """
                            type: "link"
                            title: "%s"
                            url: "https://example.com/docs"
                        """.formatted(label), "title"),
                Arguments.of("file", """
                            type: "file"
                            title: "%s"
                            mime: "application/pdf"
                            dataUrl: "data:application/pdf;base64,JVBERi0xLjQ="
                        """.formatted(label), "title"),
                Arguments.of("email", """
                            type: "email"
                            name: "%s"
                            email: "ada@example.com"
                        """.formatted(label), "name")
        );
    }

    private static Stream<Arguments> resourcesWithOversizedLabels() {
        String label = labelOverLimit();
        return Stream.of(
                Arguments.of("note", """
                            type: "note"
                            title: "%s"
                        """.formatted(label), labelTooLongMessage("Note resource title")),
                Arguments.of("link", """
                            type: "link"
                            title: "%s"
                            url: "https://example.com/docs"
                        """.formatted(label), labelTooLongMessage("Link resource title")),
                Arguments.of("file", """
                            type: "file"
                            title: "%s"
                            mime: "application/pdf"
                            dataUrl: "data:application/pdf;base64,JVBERi0xLjQ="
                        """.formatted(label), labelTooLongMessage("File resource title")),
                Arguments.of("email", """
                            type: "email"
                            name: "%s"
                            email: "ada@example.com"
                        """.formatted(label), labelTooLongMessage("Email resource name"))
        );
    }

    private static Stream<Arguments> resourcesWithOversizedLabelUpdates() {
        String label = labelOverLimit();
        return Stream.of(
                Arguments.of("note", """
                            type: "note"
                            title: "Research notes"
                        """, """
                            title: "%s"
                        """.formatted(label), "title", "Research notes", labelTooLongMessage("Note resource title")),
                Arguments.of("link", """
                            type: "link"
                            title: "Docs"
                            url: "https://example.com/docs"
                        """, """
                            title: "%s"
                        """.formatted(label), "title", "Docs", labelTooLongMessage("Link resource title")),
                Arguments.of("file", """
                            type: "file"
                            title: "Brief"
                            mime: "application/pdf"
                            dataUrl: "data:application/pdf;base64,JVBERi0xLjQ="
                        """, """
                            title: "%s"
                        """.formatted(label), "title", "Brief", labelTooLongMessage("File resource title")),
                Arguments.of("email", """
                            type: "email"
                            name: "Ada Lovelace"
                            email: "ada@example.com"
                        """, """
                            name: "%s"
                        """.formatted(label), "name", "Ada Lovelace", labelTooLongMessage("Email resource name"))
        );
    }

    private static Stream<Arguments> blankTitles() {
        return Stream.of(
                Arguments.of("blank", "   "),
                Arguments.of("empty", "")
        );
    }

    private static Stream<Arguments> blankNames() {
        return Stream.of(
                Arguments.of("null", null),
                Arguments.of("blank", "   "),
                Arguments.of("empty", "")
        );
    }

    private static Stream<Arguments> blankValues() {
        return Stream.of(
                Arguments.of("null", null),
                Arguments.of("blank", "   "),
                Arguments.of("empty", "")
        );
    }

    private static Stream<Arguments> disallowedResourceCreateFields() {
        return Stream.of(
                Arguments.of("note", """
                            type: "note"
                            title: "Note"
                        """, "url", """
                            url: "https://example.com"
                        """),
                Arguments.of("note", """
                            type: "note"
                            title: "Note"
                        """, "email", """
                            email: "ada@example.com"
                        """),
                Arguments.of("link", """
                            type: "link"
                            url: "https://example.com/docs"
                        """, "body", """
                            body: "Not a link field"
                        """),
                Arguments.of("link", """
                            type: "link"
                            url: "https://example.com/docs"
                        """, "email", """
                            email: "ada@example.com"
                        """),
                Arguments.of("file", """
                            type: "file"
                            title: "Brief"
                            mime: "application/pdf"
                            dataUrl: "data:application/pdf;base64,JVBERi0xLjQ="
                        """, "url", """
                            url: "https://example.com"
                        """),
                Arguments.of("file", """
                            type: "file"
                            title: "Brief"
                            mime: "application/pdf"
                            dataUrl: "data:application/pdf;base64,JVBERi0xLjQ="
                        """, "email", """
                            email: "ada@example.com"
                        """),
                Arguments.of("email", """
                            type: "email"
                            email: "ada@example.com"
                        """, "title", """
                            title: "Ada"
                        """),
                Arguments.of("email", """
                            type: "email"
                            email: "ada@example.com"
                        """, "dataUrl", """
                            dataUrl: "data:image/png;base64,aGVsbG8="
                        """)
        );
    }

    private static Stream<Arguments> disallowedResourceUpdateFields() {
        return Stream.of(
                Arguments.of("note", """
                            type: "note"
                            title: "Note"
                        """, "url", """
                            url: "https://example.com"
                        """),
                Arguments.of("link", """
                            type: "link"
                            url: "https://example.com/docs"
                        """, "body", """
                            body: "Not a link field"
                        """),
                Arguments.of("file", """
                            type: "file"
                            title: "Brief"
                            mime: "application/pdf"
                            dataUrl: "data:application/pdf;base64,JVBERi0xLjQ="
                        """, "email", """
                            email: "ada@example.com"
                        """),
                Arguments.of("email", """
                            type: "email"
                            email: "ada@example.com"
                        """, "title", """
                            title: "Ada"
                        """)
        );
    }

    private String oversizedPngDataUrl() {
        int base64Length = ((ResourceService.MAX_FILE_BYTES + 1 + 2) / 3) * 4;
        return "data:image/png;base64," + "A".repeat(base64Length);
    }

    private String noteBodyAtLimit() {
        return "A".repeat(ResourceService.MAX_NOTE_BODY_LENGTH);
    }

    private String oversizedNoteBody() {
        return "A".repeat(ResourceService.MAX_NOTE_BODY_LENGTH + 1);
    }

    private String maxNoteBodyLengthMessage() {
        return "Note resource body must be " + ResourceService.MAX_NOTE_BODY_LENGTH + " characters or fewer";
    }

    /** A label exactly at the allowed maximum (accepted). */
    private static String labelAtLimit() {
        return "A".repeat(ResourceService.MAX_RESOURCE_LABEL_LENGTH);
    }

    /** A label one character over the allowed maximum (rejected). */
    private static String labelOverLimit() {
        return "A".repeat(ResourceService.MAX_RESOURCE_LABEL_LENGTH + 1);
    }

    /** The validation message for an over-long label, kept in sync with the limit. */
    private static String labelTooLongMessage(String fieldLabel) {
        return fieldLabel + " must be " + ResourceService.MAX_RESOURCE_LABEL_LENGTH + " characters or fewer";
    }

    private void assertValidationError(GraphQlTester.Response response, String message) {
        response.errors()
                .satisfy(errors -> assertThat(errors)
                        .anyMatch(error ->
                                error.getMessage().contains(message) &&
                                "ValidationError".equals(error.getExtensions().get("classification"))));
    }

    private void assertGraphQlValidationError(GraphQlTester.Response response, String... messageParts) {
        response.errors()
                .satisfy(errors -> assertThat(errors)
                        .anyMatch(error ->
                                "ValidationError".equals(error.getErrorType().toString()) &&
                                java.util.Arrays.stream(messageParts)
                                        .allMatch(part -> error.getMessage().contains(part))));
    }

    private void assertDeleteResourceSucceeds(String resourceId) {
        graphQlTester.document("""
                        mutation($id: ID!) {
                          deleteResource(id: $id)
                        }
                        """)
                .variable("id", resourceId)
                .execute()
                .path("deleteResource").entity(Boolean.class).isEqualTo(true);
    }

    private void assertResourceNotFound(String resourceId) {
        GraphQlTester.Response response = graphQlTester.document("""
                        query($id: ID!) {
                          resourceById(id: $id) { id }
                        }
                        """)
                .variable("id", resourceId)
                .execute();

        assertNotFound(response, "Resource not found: " + resourceId);
    }

    private void assertNotFound(GraphQlTester.Response response, String message) {
        response.errors()
                .satisfy(errors -> assertThat(errors)
                        .anyMatch(error ->
                                error.getMessage().contains(message) &&
                                "NOT_FOUND".equals(error.getExtensions().get("classification"))));
    }

    private void assertResourceBody(String resourceId, String expectedBody) {
        assertResourceField(resourceId, "body", expectedBody);
    }

    private void assertResourceUrl(String resourceId, String expectedUrl) {
        assertResourceField(resourceId, "url", expectedUrl);
    }

    private void assertResourceEmail(String resourceId, String expectedEmail) {
        assertResourceField(resourceId, "email", expectedEmail);
    }

    private void assertResourceMime(String resourceId, String expectedMime) {
        assertResourceField(resourceId, "mime", expectedMime);
    }

    private void assertResourceDataUrl(String resourceId, String expectedDataUrl) {
        assertResourceField(resourceId, "dataUrl", expectedDataUrl);
    }

    private void assertResourceField(String resourceId, String fieldName, String expectedValue) {
        graphQlTester.document("""
                        query($id: ID!) {
                          resourceById(id: $id) {
                            title
                            body
                            url
                            mime
                            dataUrl
                            name
                            email
                          }
                        }
                        """)
                .variable("id", resourceId)
                .execute()
                .path("resourceById." + fieldName).entity(String.class).isEqualTo(expectedValue);
    }

    private void assertNoResourcesCreated() {
        graphQlTester.document("""
                        query($goalId: ID!) {
                          resourcesByGoal(goalId: $goalId) { id }
                        }
                        """)
                .variable("goalId", goalId)
                .execute()
                .path("resourcesByGoal").entityList(Object.class).hasSize(0);
    }
}
