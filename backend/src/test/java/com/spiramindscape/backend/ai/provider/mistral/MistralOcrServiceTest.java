package com.spiramindscape.backend.ai.provider.mistral;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Parsing of a Mistral OCR response ({@code POST /v1/ocr}). The HTTP call itself needs a real
 * key, so the wire shape is pinned here instead: pages joined in order, blanks skipped, output
 * capped, and "nothing readable" reported as empty rather than as an empty string the caller
 * might mistake for text (BUG-027).
 */
class MistralOcrServiceTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private Optional<String> parse(String json, int maxChars) throws Exception {
        return MistralOcrService.parseMarkdown(mapper.readTree(json), maxChars);
    }

    @Test
    void joinsPagesInOrder() throws Exception {
        String json = """
                {"pages":[{"index":0,"markdown":"Rågen Roast'n toast\\n263 kkal/100"},
                          {"index":1,"markdown":"Melon 60 грамм"}],
                 "model":"mistral-ocr-latest"}
                """;
        assertThat(parse(json, 1000)).contains("Rågen Roast'n toast\n263 kkal/100\n\nMelon 60 грамм");
    }

    @Test
    void skipsBlankPagesAndReportsAnEmptyDocumentAsEmpty() throws Exception {
        assertThat(parse("""
                {"pages":[{"index":0,"markdown":"   "},{"index":1,"markdown":"real text"}]}
                """, 1000)).contains("real text");

        assertThat(parse("""
                {"pages":[{"index":0,"markdown":""}]}
                """, 1000)).isEmpty();
        assertThat(parse("{}", 1000)).isEmpty();
    }

    @Test
    void capsLongDocuments() throws Exception {
        String page = "x".repeat(500);
        String json = "{\"pages\":[{\"index\":0,\"markdown\":\"" + page + "\"}]}";
        Optional<String> text = parse(json, 100);
        assertThat(text).isPresent();
        assertThat(text.get()).hasSize(101).endsWith("…");
    }

    @Test
    void refusesToCallWithoutAKeyOrADataUrl() {
        MistralOcrService service = new MistralOcrService(mapper);
        assertThat(service.extractText(null, "data:image/png;base64,AAAA", 100)).isEmpty();
        assertThat(service.extractText("  ", "data:image/png;base64,AAAA", 100)).isEmpty();
        assertThat(service.extractText("key", "https://example.com/x.png", 100)).isEmpty();
        assertThat(service.extractText("key", null, 100)).isEmpty();
    }
}
