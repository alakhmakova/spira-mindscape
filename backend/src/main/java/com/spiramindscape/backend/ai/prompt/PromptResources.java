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
    private static final String CV_WRITER_METHOD_PATH = "prompts/cv/writer-method.md";
    private static final String CV_FORMAT_PATH = "prompts/cv/cv-format.md";
    private static final String CV_LETTER_METHOD_PATH = "prompts/cv/letter-method.md";
    private static final String CV_ANALYSIS_EXTRACT_PATH = "prompts/cv/analysis-extract.md";
    private static final String CV_ANALYSIS_PLAN_PATH = "prompts/cv/analysis-plan.md";
    private static final String CV_ANALYSIS_COVERAGE_PATH = "prompts/cv/analysis-coverage.md";

    private final String growCoachMethod;
    private final String cvWriterMethod;
    private final String cvFormat;
    private final String cvLetterMethod;
    private final String cvAnalysisExtract;
    private final String cvAnalysisPlan;
    private final String cvAnalysisCoverage;

    public PromptResources() {
        this.growCoachMethod = read(COACH_METHOD_PATH);
        this.cvWriterMethod = read(CV_WRITER_METHOD_PATH);
        this.cvFormat = read(CV_FORMAT_PATH);
        this.cvLetterMethod = read(CV_LETTER_METHOD_PATH);
        this.cvAnalysisExtract = read(CV_ANALYSIS_EXTRACT_PATH);
        this.cvAnalysisPlan = read(CV_ANALYSIS_PLAN_PATH);
        this.cvAnalysisCoverage = read(CV_ANALYSIS_COVERAGE_PATH);
    }

    /**
     * The GROW coach's persona, method and failure-handling, distilled from
     * "Coach the Person, Not the Problem" (Marcia Reynolds, 2020).
     */
    public String growCoachMethod() {
        return growCoachMethod;
    }

    /**
     * Who the CV writer is, how the interview runs, and what may never be written.
     * Carried on every turn of a CV session.
     *
     * <p>Distilled from Martin Yate, <i>Resumes That Knock 'em Dead</i> (ch. 1–5) and
     * <i>Cover Letters That Knock 'em Dead</i> (ch. 1–4), with the era's furniture —
     * paper stock, postal campaigns — stripped out. See
     * {@code specs/2026-09-08-cv-and-cover-letter-agent/requirements.md} §3.
     */
    public String cvWriterMethod() {
        return cvWriterMethod;
    }

    /**
     * The CV's shape: which of the three formats to use, the section template, the
     * length rule and the proofreading gate. Loaded only in the phases that build or
     * show the document — it has no business in context during the interview.
     */
    public String cvFormat() {
        return cvFormat;
    }

    /**
     * The covering letter as its own genre: the four ingredients, the AIDA shape, and
     * the rule that its subject is whatever the one-page CV had no room for. Loaded
     * only once the CV is done.
     */
    public String cvLetterMethod() {
        return cvLetterMethod;
    }

    /** The job analysis, stage A: topics from the advert. */
    public String cvAnalysisExtract() {
        return cvAnalysisExtract;
    }

    /** Stage C: which primary sources to read for each topic. */
    public String cvAnalysisPlan() {
        return cvAnalysisPlan;
    }

    /** Stage E: how well her material covers each topic. */
    public String cvAnalysisCoverage() {
        return cvAnalysisCoverage;
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
