package com.spiramindscape.backend.ai.cv.dto;

import com.spiramindscape.backend.ai.cv.CvApplication;
import com.spiramindscape.backend.ai.cv.CvStep;

/**
 * One application as the clients see it.
 *
 * <p>{@code phase} is internal machinery and is never shown; the clients show {@code step} —
 * "Step 2 of 4 · Filling in the vacancy map" — and the counts, so the user can see how many of the
 * map's requirements still have no answer.
 */
public record CvApplicationDto(
        Long id,
        Long goalId,
        String title,
        String vacancyUrl,
        String vacancyLanguage,
        String contactName,
        Boolean letterRequired,
        String phase,
        /** The intake form ("your details"); historically the profile note. */
        Long profileNoteId,
        Long storiesNoteId,
        Long cvNoteId,
        Long letterNoteId,
        Long briefingNoteId,
        /** Items of the requirement map that are settled, and how many are asked about in all. */
        int answered,
        int total,
        int step,
        int steps,
        String stepTitle,
        String stepActivity,
        /** exists | candidate | none */
        String intakeStatus,
        /**
         * The note the server MEANT by {@code candidate}, and what it is called.
         *
         * <p>The client used to find it again with a regex of its own, which had drifted from the
         * server's: the step message said "press Use as my details" and the button was not drawn,
         * because the two patterns disagreed about one alternative (owner's live run,
         * 2026-09-16). The server already knows which note it meant, so it says which.
         */
        Long intakeCandidateId,
        String intakeCandidateTitle,
        boolean intakeConfirmed,
        String analysisState,
        Long analysisNoteId,
        /** Items of the CURRENT step still waiting for an answer. */
        int openTopics,
        int competences,
        int requirements,
        int traits,
        long transcriptRevision,
        String roleTitle,
        String companyName,
        String location,
        String coreMessage,
        /**
         * The language the conversation runs in — what the card copy is fetched in. The documents
         * are written in {@code vacancyLanguage}, which is usually a different one.
         */
        String conversationLanguage,
        /** The vacancy map this application works on — what the panel links to. Null until built. */
        Long mapResourceId) {

    public static CvApplicationDto from(CvApplication app, int answered, int total) {
        return from(app, answered, total, app.getProfileNoteId() != null ? "exists" : "none",
                null, null, total - answered, 0, 0, 0);
    }

    public static CvApplicationDto from(CvApplication app, int answered, int total, String intakeStatus,
                                        Long candidateId, String candidateTitle, int openTopics,
                                        int competences, int requirements, int traits) {
        CvStep step = app.getPhase().step();
        return new CvApplicationDto(
                app.getId(),
                app.getGoalId(),
                app.getTitle(),
                app.getVacancyUrl(),
                app.getVacancyLanguage(),
                app.getContactName(),
                app.getLetterRequired(),
                app.getPhase().wireValue(),
                app.getProfileNoteId(),
                app.getStoriesNoteId(),
                app.getCvNoteId(),
                app.getLetterNoteId(),
                app.getBriefingNoteId(),
                answered,
                total,
                step.number(),
                CvStep.TOTAL,
                step.title(),
                step.activity(),
                intakeStatus,
                candidateId,
                candidateTitle,
                app.getIntakeConfirmedAt() != null,
                app.getAnalysisState(),
                app.getAnalysisNoteId(),
                openTopics,
                competences,
                requirements,
                traits,
                app.getTranscriptRevision(),
                app.getRoleTitle(),
                app.getCompanyName(),
                app.getLocation(),
                app.getCoreMessage(),
                app.getConversationLanguage(),
                app.getMapResourceId());
    }
}
