package com.spiramindscape.backend.ai.cv;

/**
 * Where a CV application has got to.
 *
 * <p>The advert is analysed first and turned into a vacancy map — a page the user opens and edits
 * (specs/2026-09-17-vacancy-map/). The work then happens ON the map, in whatever order she chooses:
 * she fills it herself, or the coach helps her part by part. Only when she says the map is done,
 * and agrees to start, is the CV written from it.
 *
 * <p>This replaced a conveyor of six fixed steps (2026-09-18) — details, a competence checklist,
 * then one requirement per turn — in which the server decided what came next and the coach could
 * not ask her which part she wanted to work on. The phase lives on the application, not in the
 * model's head; what the USER sees is the {@link CvStep}.
 */
public enum CvPhase {

    /** The server reads the advert and builds the vacancy map. */
    ANALYSIS,

    /**
     * The vacancy map, filled by the user — herself, or with the coach one part at a time. Her
     * details (contact, employers, education) are one of those parts. Ends only when she says the
     * map is finished AND agrees to start the CV.
     */
    MAP,

    /** Write the CV — from the map, in one pass, including SAMMANFATTNING. */
    DRAFT,

    /** The finished CV is shown: corrections, and the offer to save it as a PDF. */
    CV_REVIEW,

    /** Assemble the letter's ingredients before any letter text exists. */
    LETTER_STRATEGY,

    /** Write the letter. */
    LETTER,

    /** Both documents delivered. */
    DONE;

    /** The wire/storage value. */
    public String wireValue() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    /** The step the user sees for this phase. */
    public CvStep step() {
        return CvStep.of(this);
    }

    /**
     * Parse a stored phase, defaulting to {@link #ANALYSIS}.
     *
     * <p>Failing open is right for a value out of our own database — a row written by an older
     * version should open at the start of the work, not crash a session. It is <b>wrong</b> for a
     * value a model supplied: see {@link #parse}.
     */
    public static CvPhase from(String raw) {
        return parse(raw).orElse(ANALYSIS);
    }

    /**
     * Parse strictly — empty when the value is not one of the phases.
     *
     * <p>Use this for anything a model supplied. An unrecognised {@code next} used to become the
     * first phase, and since a backward move is always allowed the transition was accepted: a
     * twenty-question interview silently rewound and the tool answered "Moved to profile."
     *
     * <p>The retired names still parse, so an older client or a stored value lands where that work
     * happens now: every collection step of the old process — the intake form, the competence
     * checklist, the core message, the requirements, the qualities — is the {@link #MAP}.
     */
    public static java.util.Optional<CvPhase> parse(String raw) {
        if (raw == null) return java.util.Optional.empty();
        String v = raw.strip().toLowerCase(java.util.Locale.ROOT);
        switch (v) {
            case "vacancy", "deconstruct" -> {
                return java.util.Optional.of(ANALYSIS);
            }
            case "profile", "intake", "competencies", "competences", "core", "requirements", "traits",
                 "evidence" -> {
                return java.util.Optional.of(MAP);
            }
            case "summary" -> {
                return java.util.Optional.of(DRAFT);
            }
            default -> {
            }
        }
        for (CvPhase p : values()) {
            if (p.name().equalsIgnoreCase(v)) return java.util.Optional.of(p);
        }
        return java.util.Optional.empty();
    }

    /**
     * Does this phase produce a note the application should own?
     *
     * <p>The map is a resource the server creates itself, and the letter's strategy produces no
     * document; everything else does — her details during the map step, then the CV and the letter.
     */
    public boolean producesNote() {
        return this != ANALYSIS && this != LETTER_STRATEGY;
    }

    /**
     * May the application move from {@code this} to {@code next}?
     *
     * <p>Forward by one step, or backward to any earlier phase. Backward is allowed because
     * revision is normal — a user reading the finished CV may remember another example, which puts
     * the work back on the {@link #MAP} and then forward again. Skipping ahead is not: every phase
     * produces what the next one consumes.
     *
     * <p>The one legitimate skip is {@link #CV_REVIEW} straight to {@link #DONE}, when no cover
     * letter is wanted.
     */
    public boolean canAdvanceTo(CvPhase next) {
        if (next == null) return false;
        if (next.ordinal() < this.ordinal()) return true;
        if (next.ordinal() == this.ordinal() + 1) return true;
        return this == CV_REVIEW && next == DONE;
    }

    /** The one phase a forward move may go to — empty at {@link #DONE}. */
    public java.util.Optional<CvPhase> next() {
        return this == DONE
                ? java.util.Optional.empty()
                : java.util.Optional.of(values()[ordinal() + 1]);
    }

    /**
     * Every phase this one may legally move to, in the order they should be offered.
     *
     * <p>Backward moves are left out on purpose: they are always legal, and listing them would
     * bury the one that matters.
     */
    public java.util.List<CvPhase> forwardMoves() {
        if (this == CV_REVIEW) return java.util.List.of(LETTER_STRATEGY, DONE);
        return next().map(java.util.List::of).orElseGet(java.util.List::of);
    }
}
