package com.spiramindscape.backend.ai.cv;

/**
 * The words on the CV panel's own buttons, in the language the conversation runs in.
 *
 * <p><b>Button copy belongs to the server, like every other word the process says.</b> The panel
 * used to carry English literals while every server-written line was localised, so a Russian
 * session announced a step in Russian and the control under it spoke English (owner's live run,
 * 2026-09-16).
 *
 * <p>Two controls are left: the competence checklist and the one-question cards are gone
 * (2026-09-18) — the vacancy map is where she answers now, so the collection copy went with them,
 * and what the map step needs is the way to it. {@code {title}} is substituted by the client.
 */
public record CvCardText(String useAsDetails, String openMap) {

    public static CvCardText of(String language) {
        return switch (CvTransitions.lang(language)) {
            case "ru" -> new CvCardText("Использовать «{title}» как мои данные", "Открыть карту вакансии");
            case "sv" -> new CvCardText("Använd «{title}» som mina uppgifter", "Öppna vakanskartan");
            default -> new CvCardText("Use “{title}” as my details", "Open the vacancy map");
        };
    }
}
