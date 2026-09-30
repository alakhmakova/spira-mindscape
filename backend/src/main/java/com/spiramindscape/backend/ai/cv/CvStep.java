package com.spiramindscape.backend.ai.cv;

/**
 * The four steps the user sees, one or more {@link CvPhase}s each.
 *
 * <p>The advert is analysed into a vacancy map; the user fills the map (herself or with the coach);
 * the CV is written from it; then the letter. A step has a number, a short title for lists and a
 * verb phrase for the pill — "Step 2 of 4 · Filling in the vacancy map" — so the screen always
 * answers "where am I, and what is happening".
 */
public enum CvStep {

    ANALYSIS(1, "Job analysis", "Analyzing the job advert"),
    MAP(2, "The vacancy map", "Filling in the vacancy map"),
    CV(3, "Your CV", "Writing your CV"),
    LETTER(4, "Cover letter", "Writing your cover letter");

    public static final int TOTAL = values().length;

    private final int number;
    private final String title;
    private final String activity;

    CvStep(int number, String title, String activity) {
        this.number = number;
        this.title = title;
        this.activity = activity;
    }

    public int number() {
        return number;
    }

    /** Short, for lists: "The vacancy map". */
    public String title() {
        return title;
    }

    /** A verb phrase, for the pill: "Analyzing the job advert". */
    public String activity() {
        return activity;
    }

    public static CvStep of(CvPhase phase) {
        if (phase == null) return ANALYSIS;
        return switch (phase) {
            case ANALYSIS -> ANALYSIS;
            case MAP -> MAP;
            case DRAFT, CV_REVIEW -> CV;
            case LETTER_STRATEGY, LETTER, DONE -> LETTER;
        };
    }

    public static CvStep ofNumber(int n) {
        for (CvStep s : values()) if (s.number == n) return s;
        return ANALYSIS;
    }
}
