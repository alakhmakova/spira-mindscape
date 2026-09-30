package com.spiramindscape.backend.ai.cv.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Starting an application: the goal it belongs to, and the advert it is for as
 * <b>either a link or the text</b>.
 *
 * <p>Whichever is given, the row ends up holding the text — the server fetches the page
 * when only a link arrives. Reading a job board fails often enough (JavaScript-rendered
 * pages, bot blocking) that this has to be tried <b>now</b>, at the start card, where the
 * user can paste the advert instead; a row holding only a dead link is an application
 * with nothing to deconstruct, and the failure would otherwise surface halfway through a
 * session.
 */
public record CreateCvApplicationRequest(
        @NotNull Long goalId,

        /**
         * "QA-testare — iFacts, Malmö". Optional and normally absent: the user is not asked
         * for it, and the server takes it from the advert's own first line.
         */
        @Size(max = 300) String title,

        @Size(max = 2000) String vacancyUrl,

        /**
         * The advert itself, when the user pasted it. Capped at the same 50k as a chat
         * message and a note's body: it is pasted by the same person, through the same kind
         * of field.
         */
        @Size(max = 50_000, message = "That job advert is too long — keep it under 50000 characters")
        String vacancyText,

        /** BCP-47-ish tag for the language the CV and letter get written in. */
        @Size(max = 16) String vacancyLanguage) {

    /** One of the two has to be there; which one is the user's choice. */
    @jakarta.validation.constraints.AssertTrue(
            message = "Give either a link to the advert or its text")
    public boolean hasAdvert() {
        return (vacancyUrl != null && !vacancyUrl.isBlank())
                || (vacancyText != null && !vacancyText.isBlank());
    }
}
