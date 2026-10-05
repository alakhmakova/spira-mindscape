package com.spiramindscape.backend.ai.provider.cohere;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spiramindscape.backend.ai.provider.ImageTextReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reads the text out of a picture using <b>Cohere's own vision model, on the user's Cohere
 * key</b> — so choosing Cohere does not oblige someone to go and get a second API key from a
 * second company just to attach a photo (owner, 2026-08-28: <i>"а можно чтобы для cohere это
 * была не же модель, чтобы не нужно было подключать 2 ключа?"</i>).
 *
 * <p>It is the Cohere twin of {@code MistralOcrService}, and it exists for the same reason that
 * service does: most of Cohere's Command line is text-only, so an attached photo would otherwise
 * reach the model as nothing at all, and the model would answer as though it had looked
 * (BUG-027). The difference is only whose key pays.
 *
 * <h2>Not OCR, and described as such</h2>
 *
 * <p>Mistral's {@code mistral-ocr-latest} is a document-OCR product. This is a general vision
 * model being asked to transcribe, which is a different thing: better at understanding a
 * cluttered photo, weaker on dense scans and handwriting, and — the part that matters — able to
 * <b>paraphrase or embellish</b> if the prompt lets it. Hence {@link #TRANSCRIBE_PROMPT}, which
 * asks for the text and nothing else, and {@link #describeReading()}, which tells the chat model
 * what kind of reading it is holding so it can say so to the user rather than claiming sight.
 *
 * <p>Never throws: any failure returns empty and the caller falls back to telling the user
 * plainly that the image could not be read.
 */
@Service
public class CohereVisionReader implements ImageTextReader {

    private static final Logger log = LoggerFactory.getLogger(CohereVisionReader.class);

    private static final String ENDPOINT = "https://api.cohere.com/v2/chat";

    /**
     * The vision model used for the reading, whatever chat model the user picked.
     *
     * <p>Deliberately independent of their choice: the point is to serve someone whose chosen
     * model is blind, so the reader has to name a seeing one itself.
     */
    private static final String VISION_MODEL = "command-a-vision-07-2025";

    /** Reading a photo is a few seconds; the cap is generous but finite. */
    private static final Duration TIMEOUT = Duration.ofSeconds(60);

    /**
     * What the vision model is asked to do.
     *
     * <p>Every clause is load-bearing. A general model told merely to "describe this image"
     * returns prose <i>about</i> the picture, which then reaches the chat model as though it
     * were the picture's text — so the request is for the text itself, verbatim, with an
     * explicit instruction to say when there is none rather than to fill the silence.
     */
    static final String TRANSCRIBE_PROMPT =
            "Transcribe every piece of text visible in this image, exactly as written, keeping "
            + "the original language and the reading order. Do not translate, summarise, "
            + "explain or comment. If a word is unclear, write it as best you can and mark it "
            + "with [?]. If the image contains no text at all, reply with exactly: NO TEXT.";

    /** What the model answers when it found nothing — see {@link #TRANSCRIBE_PROMPT}. */
    private static final String NOTHING_FOUND = "NO TEXT";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public CohereVisionReader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public Optional<String> extractText(String apiKey, String dataUrl, int maxChars) {
        if (apiKey == null || apiKey.isBlank() || dataUrl == null || !dataUrl.startsWith("data:")) {
            return Optional.empty();
        }
        // A PDF is not an image part; that case stays Mistral OCR's, which handles scans.
        if (!dataUrl.regionMatches(true, 0, "data:image/", 0, 11)) {
            return Optional.empty();
        }
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ENDPOINT))
                    .header("authorization", "Bearer " + apiKey)
                    .header("content-type", "application/json")
                    .timeout(TIMEOUT)
                    .POST(HttpRequest.BodyPublishers.ofString(buildRequestBody(dataUrl)))
                    .build();

            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("cohere_vision_read_failed status={}", response.statusCode());
                return Optional.empty();
            }
            return parseText(objectMapper.readTree(response.body()), maxChars);
        } catch (Exception e) {
            log.warn("cohere_vision_read_failed", e);
            return Optional.empty();
        }
    }

    @Override
    public String describeReading() {
        return "text read out of the image by a vision model — it may contain mistakes, "
                + "especially with handwriting, and it is a transcription rather than sight";
    }

    /** A single non-streaming turn: the instruction and the picture. */
    String buildRequestBody(String dataUrl) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", VISION_MODEL);
        body.put("stream", false);
        // Deterministic: this is a transcription, not a piece of writing.
        body.put("temperature", 0);
        body.put("messages", List.of(Map.of(
                "role", "user",
                "content", List.of(
                        Map.of("type", "text", "text", TRANSCRIBE_PROMPT),
                        Map.of("type", "image_url",
                                "image_url", Map.of("url", dataUrl))))));
        return objectMapper.writeValueAsString(body);
    }

    /**
     * Pulls the text out of a v2 chat response — {@code message.content[]} is an array of typed
     * parts, and only the {@code text} ones are the answer ({@code thinking} parts are the
     * model's working and must not be handed on as though they were the image's contents).
     *
     * <p>Package-private so the parsing is unit-tested without an HTTP call.
     */
    static Optional<String> parseText(JsonNode root, int maxChars) {
        StringBuilder sb = new StringBuilder();
        for (JsonNode part : root.path("message").path("content")) {
            if (!"text".equals(part.path("type").asText())) continue;
            String text = part.path("text").asText("").trim();
            if (text.isEmpty()) continue;
            if (sb.length() > 0) sb.append("\n");
            sb.append(text);
        }
        String text = sb.toString().trim();
        if (text.isEmpty() || NOTHING_FOUND.equalsIgnoreCase(text)) return Optional.empty();
        return Optional.of(text.length() > maxChars ? text.substring(0, maxChars) + "…" : text);
    }
}
