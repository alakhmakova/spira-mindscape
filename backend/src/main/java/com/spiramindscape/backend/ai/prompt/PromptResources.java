package com.spiramindscape.backend.ai.prompt;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * Prompt text that lives in {@code src/main/resources/prompts/} rather than in a
 * Java text block, so it can be read and edited as prose.
 *
 * <p>Loaded once at construction and held in memory: a prompt file is a few KB
 * and is needed on every request. A missing or blank file <b>fails startup</b> —
 * the same "no silent fallback" rule the GROW library follows
 * ({@link com.spiramindscape.backend.ai.grow.GrowLibraryService}), because a
 * coach silently running without its method is far worse than a boot failure.
 */
@Component
public class PromptResources {

    private static final String COACH_METHOD_PATH = "prompts/grow/coach-method.md";

    private final String growCoachMethod;

    public PromptResources() {
        this.growCoachMethod = read(COACH_METHOD_PATH);
    }

    /**
     * The GROW coach's persona, method and failure-handling, distilled from
     * "Coach the Person, Not the Problem" (Marcia Reynolds, 2020).
     */
    public String growCoachMethod() {
        return growCoachMethod;
    }

    private static String read(String path) {
        ClassPathResource resource = new ClassPathResource(path);
        if (!resource.exists()) {
            throw new IllegalStateException("Prompt resource is missing: " + path);
        }
        String text;
        try (var in = resource.getInputStream()) {
            text = new String(in.readAllBytes(), StandardCharsets.UTF_8).strip();
        } catch (IOException e) {
            throw new UncheckedIOException("Prompt resource could not be read: " + path, e);
        }
        if (text.isBlank()) {
            throw new IllegalStateException("Prompt resource is empty: " + path);
        }
        return text;
    }
}
