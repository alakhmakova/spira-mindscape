package com.spiramindscape.backend.ai.cv;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * What the CV coach says at the start and at every step change — written by the server, never by a
 * model.
 *
 * <p>The process is the owner's (2026-09-18): the advert is read into a vacancy map, she is sent the
 * link and told what each part of the map is and what it becomes in the CV, and then asked whether
 * she will fill it herself or wants help. Structure a template carries cannot be skipped by a model
 * having an off day.
 *
 * <p>Pure: facts in, text out. Russian, Swedish and English; anything else is English. No emoji,
 * per the app-wide rule. The Russian never genders the coach ("я прочитал" is a man talking, and
 * the agent is not one — owner's live run, 2026-09-16), so the work is stated rather than the worker.
 */
public final class CvTransitions {

    private CvTransitions() {
    }

    /** Everything a step message may mention. Built by {@link CvApplicationService#facts}. */
    public record Facts(
            String vacancyTitle,
            String role,
            String company,
            String location,
            /** What the advert is about, in its own words — quoted in the analysis summary. */
            String coreMessage,
            /** exists | candidate | none — her details note */
            String intakeStatus,
            String intakeTitle,
            String candidateTitle,
            List<String> intakeMissing,
            /** Skills on the map. */
            int competences,
            /** Requirements marked very important. */
            int cores,
            int requirements,
            /** Personal qualities on the map. */
            int traits,
            /** The advert's hard conditions, raised with her before the map is worked on. */
            List<String> flags,
            /** Requirements with no answer under any employer yet. */
            int open,
            /** The vacancy map's title — the vacancy itself. */
            String mapNoteTitle,
            String cvNoteTitle,
            Boolean letterWanted,
            /** Where the map lives, e.g. {@code /goals/12?resource=34} — it opens as a panel on the goal page; null when it has none yet. */
            String mapLink,
            /**
             * The map was already on the goal before this application started: she made it, and
             * this application adopted it rather than creating a second (owner, 2026-09-23).
             */
            boolean mapIsHers) {

        /**
         * The eighteen-field form, without {@code mapLink}. A record's components are positional, so
         * adding one would otherwise be a mechanical edit to call sites that have nothing to do with
         * the link.
         */
        public Facts(String vacancyTitle, String role, String company, String location,
                     String coreMessage, String intakeStatus, String intakeTitle,
                     String candidateTitle, List<String> intakeMissing, int competences, int cores,
                     int requirements, int traits, List<String> flags, int open, String mapNoteTitle,
                     String cvNoteTitle, Boolean letterWanted) {
            this(vacancyTitle, role, company, location, coreMessage, intakeStatus, intakeTitle,
                    candidateTitle, intakeMissing, competences, cores, requirements, traits, flags,
                    open, mapNoteTitle, cvNoteTitle, letterWanted, null, false);
        }

        /** The nineteen-field form, for callers that predate the adopted-map question. */
        public Facts(String vacancyTitle, String role, String company, String location,
                     String coreMessage, String intakeStatus, String intakeTitle,
                     String candidateTitle, List<String> intakeMissing, int competences, int cores,
                     int requirements, int traits, List<String> flags, int open, String mapNoteTitle,
                     String cvNoteTitle, Boolean letterWanted, String mapLink) {
            this(vacancyTitle, role, company, location, coreMessage, intakeStatus, intakeTitle,
                    candidateTitle, intakeMissing, competences, cores, requirements, traits, flags,
                    open, mapNoteTitle, cvNoteTitle, letterWanted, mapLink, false);
        }
    }

    /** The three supported languages; everything else is English. */
    public static String lang(String raw) {
        if (raw == null) return "en";
        String l = raw.strip().toLowerCase(Locale.ROOT);
        if (l.startsWith("ru")) return "ru";
        if (l.startsWith("sv")) return "sv";
        return "en";
    }

    private static final Pattern CYRILLIC = Pattern.compile("\\p{IsCyrillic}");
    private static final Pattern SWEDISH = Pattern.compile(
            "[åäöÅÄÖ]|(?i)\\b(och|jag|att|är|inte|det|har|för|med|som|vill)\\b");

    /**
     * The language a user message is written in, or {@code null} when it is too short or too mixed
     * to say. Only used to keep the step messages in the user's own language.
     */
    public static String detectLanguage(String text) {
        if (text == null) return null;
        String t = text.strip();
        if (t.length() < 3 || t.startsWith("[")) return null;
        long cyr = CYRILLIC.matcher(t).results().count();
        long letters = t.chars().filter(Character::isLetter).count();
        if (letters == 0) return null;
        if (cyr * 2 >= letters) return "ru";
        if (SWEDISH.matcher(t).find()) return "sv";
        return letters >= 12 ? "en" : null;
    }

    private static String target(Facts f, String lang) {
        String role = blank(f.role()) ? f.vacancyTitle() : f.role();
        if (blank(f.company())) return "«" + role + "»";
        return switch (lang) {
            case "ru" -> "«" + role + "» в " + f.company();
            case "sv" -> "«" + role + "» hos " + f.company();
            default -> "«" + role + "» at " + f.company();
        };
    }

    private static String header(CvStep step, String lang) {
        return switch (lang) {
            case "ru" -> "**Шаг " + step.number() + " из " + CvStep.TOTAL + " · " + ruTitle(step) + "**";
            case "sv" -> "**Steg " + step.number() + " av " + CvStep.TOTAL + " · " + svTitle(step) + "**";
            default -> "**Step " + step.number() + " of " + CvStep.TOTAL + " · " + step.title() + "**";
        };
    }

    static String ruTitle(CvStep s) {
        return switch (s) {
            case ANALYSIS -> "Анализ вакансии";
            case MAP -> "Карта вакансии";
            case CV -> "Ваше CV";
            case LETTER -> "Сопроводительное письмо";
        };
    }

    static String svTitle(CvStep s) {
        return switch (s) {
            case ANALYSIS -> "Analys av annonsen";
            case MAP -> "Vakanskartan";
            case CV -> "Ditt CV";
            case LETTER -> "Personligt brev";
        };
    }

    // ── The opening ──────────────────────────────────────────────────────────

    /** The first message: who, which vacancy was received, what happens, and one question. */
    public static String opening(Facts f, String language) {
        String lang = lang(language);
        String t = target(f, lang);
        return switch (lang) {
            case "ru" -> "Здравствуйте! Я ваш специалист по резюме в Spira. Вместе мы подготовим CV "
                    + "и сопроводительное письмо под одну вакансию — " + t + ". Объявление у меня есть, "
                    + "его текст сохранён, присылать его ещё раз не нужно.\n\n"
                    + "Как мы будем работать:\n\n"
                    + "1. **Анализ вакансии** — объявление будет разобрано по предложениям и разложено "
                    + "на карту вакансии: отдельную страницу, где по частям видно, что ищет работодатель.\n"
                    + "2. **Карта вакансии** — вы заполняете её своими примерами: сами или вместе со "
                    + "мной, в любом порядке. Это и есть материал для CV.\n"
                    + "3. **Ваше CV** — когда скажете, что карта готова, я напишу CV целиком по ней.\n"
                    + "4. **Сопроводительное письмо** — если оно нужно.\n\n"
                    + "Можно приступать?";
            case "sv" -> "Hej! Jag är din CV-specialist i Spira. Tillsammans tar vi fram ett CV och ett "
                    + "personligt brev för en tjänst — " + t + ". Jag har annonsen sparad, så du behöver "
                    + "inte skicka den igen.\n\n"
                    + "Så här arbetar vi:\n\n"
                    + "1. **Analys av annonsen** — den läses mening för mening och läggs upp som en "
                    + "vakanskarta: en egen sida där du ser, del för del, vad arbetsgivaren söker.\n"
                    + "2. **Vakanskartan** — du fyller i den med dina egna exempel, själv eller tillsammans "
                    + "med mig, i vilken ordning du vill. Det är materialet till CV:t.\n"
                    + "3. **Ditt CV** — när du säger att kartan är klar skriver jag CV:t i sin helhet utifrån den.\n"
                    + "4. **Personligt brev** — om det behövs.\n\n"
                    + "Kan vi börja?";
            default -> "Hello! I'm your CV specialist in Spira. Together we'll prepare a CV and a cover "
                    + "letter for one vacancy — " + t + ". I have the advert saved, so there's no need to "
                    + "send it again.\n\n"
                    + "Here is how we'll work:\n\n"
                    + "1. **Job analysis** — the advert is read sentence by sentence and laid out as a "
                    + "vacancy map: a page of its own that shows, part by part, what the employer wants.\n"
                    + "2. **The vacancy map** — you fill it in with your own examples, yourself or with "
                    + "me, in any order you like. That is the material the CV is made of.\n"
                    + "3. **Your CV** — when you tell me the map is ready, I write the whole CV from it.\n"
                    + "4. **Cover letter** — if one is wanted.\n\n"
                    + "Shall we start?";
        };
    }

    // ── Step announcements ───────────────────────────────────────────────────

    /** The message that starts {@code step}: what finished, what starts, what is needed now. */
    public static String announce(CvStep step, Facts f, String language) {
        String lang = lang(language);
        return header(step, lang) + "\n\n" + switch (step) {
            case ANALYSIS -> analysisStep(lang);
            case MAP -> mapStep(f, lang);
            case CV -> cvStep(lang);
            case LETTER -> letterStep(lang);
        };
    }

    private static String analysisStep(String lang) {
        return switch (lang) {
            case "ru" -> "Читаю объявление по предложениям. Отбрасываю связующие слова и общие фразы и "
                    + "оставляю только то, что нужно для CV и письма: должность, локацию, навыки, "
                    + "требования работодателя, личные качества и условия. Повторы объединяю. "
                    + "Это займёт около минуты — от вас пока ничего не нужно.";
            case "sv" -> "Jag läser annonsen mening för mening. Jag rensar bort bindeord och allmänna "
                    + "fraser och behåller bara det som behövs för CV och brev: roll, plats, färdigheter, "
                    + "arbetsgivarens krav, personliga egenskaper och villkor. Upprepningar slår jag ihop. "
                    + "Det tar ungefär en minut — du behöver inte göra något just nu.";
            default -> "I'm reading the advert sentence by sentence. I drop the connective words and the "
                    + "decoration and keep only what the CV and the letter need: the role, the location, "
                    + "the skills, the employer's requirements, the personal qualities and the conditions. "
                    + "Repeats are merged. About a minute — nothing is needed from you yet.";
        };
    }

    /**
     * What the analysis found. Shown once, just before the map step is announced — the link to the
     * map belongs to that announcement, so it is said once.
     *
     * <p>Any condition of the advert comes LAST and as a question: see {@link #flagQuestion}.
     */
    public static String analysisSummary(Facts f, String language) {
        String lang = lang(language);
        String role = blank(f.role()) ? f.vacancyTitle() : f.role();
        String core = blank(f.coreMessage()) ? "" : f.coreMessage();
        String body = switch (lang) {
            case "ru" -> "Объявление на должность " + role + " прочитано и разобрано: " + f.competences()
                    + " навыков, " + f.requirements() + " требований и " + f.traits() + " личных качеств."
                    + (core.isEmpty() ? "" : "\n\nСамое важное для работодателя звучит так:\n\n> " + core);
            case "sv" -> "Annonsen för " + role + " är läst och genomgången: " + f.competences()
                    + " färdigheter, " + f.requirements() + " krav och " + f.traits() + " personliga egenskaper."
                    + (core.isEmpty() ? "" : "\n\nDet viktigaste för arbetsgivaren låter så här:\n\n> " + core);
            default -> "I've read the advert for " + role + " and gone through it: " + f.competences()
                    + " skills, " + f.requirements() + " requirements and " + f.traits() + " personal qualities."
                    + (core.isEmpty() ? "" : "\n\nWhat the employer is looking for first reads like this:\n\n> " + core);
        };
        return body + flagQuestion(f, lang);
    }

    /**
     * The advert's own conditions — a mandatory background check, a permit, a stated number of
     * years — as their own paragraph, ending in a question.
     *
     * <p>Her process says a hard condition is raised with her NOW, and raising it means asking — a
     * line nobody answers is not a conversation (owner's live run, 2026-09-16). Empty when there is
     * nothing to raise, which is what lets the caller decide whether to announce the map in the same
     * turn.
     */
    public static String flagQuestion(Facts f, String language) {
        List<String> flags = f.flags();
        if (flags == null || flags.isEmpty()) return "";
        String lang = lang(language);
        boolean one = flags.size() == 1;
        StringBuilder sb = new StringBuilder("\n\n").append(switch (lang) {
            case "ru" -> one ? "Но сначала одно условие из объявления." : "Но сначала условия из объявления.";
            case "sv" -> one ? "Men först ett villkor ur annonsen." : "Men först villkoren i annonsen.";
            default -> one ? "But first, one condition from the advert." : "But first, the conditions the advert sets.";
        });
        for (String flag : flags) {
            if (blank(flag)) continue;
            sb.append("\n\n> ").append(flag.strip());
        }
        sb.append("\n\n").append(switch (lang) {
            case "ru" -> one ? "Подходит ли вам это условие?" : "Подходят ли вам эти условия?";
            case "sv" -> one ? "Stämmer det för dig?" : "Stämmer de för dig?";
            default -> one ? "Does that work for you?" : "Do those work for you?";
        });
        return sb.toString();
    }

    /**
     * The map step: where the map is, what each part is for and what it becomes in the CV, and the
     * one question — herself, or together?
     *
     * <p>The explanation is the point, not decoration (owner, 2026-09-18): the map only works as the
     * agenda if she knows what each part turns into, and the coach has to be able to say it.
     */
    private static String mapStep(Facts f, String lang) {
        String title = blank(f.mapNoteTitle()) ? f.vacancyTitle() : f.mapNoteTitle();
        String where = blank(f.mapLink())
                ? switch (lang) {
                    case "ru" -> "Карта вакансии создана — она в ресурсах цели. ";
                    case "sv" -> "Vakanskartan är skapad — den finns bland målets resurser. ";
                    default -> "The vacancy map is ready — it's in the goal's resources. ";
                }
                : f.mapIsHers()
                    // **Her own map, adopted rather than duplicated** (owner, 2026-09-23). Said
                    // plainly, because the writer used to make a second map beside hers and work
                    // only from that one — and nothing on screen admitted it.
                    ? switch (lang) {
                        // No gendered verbs: the coach is not a man and not a woman (see the
                        // note at the top of this file).
                        case "ru" -> "В работе ваша карта: [" + title + "](" + f.mapLink()
                                + "). Новая не создавалась, в вашей ничего не изменилось. Если нужно "
                                + "разобрать объявление заново и предложить то, чего в ней нет, — скажите. ";
                        case "sv" -> "Jag arbetar med din karta: [" + title + "](" + f.mapLink()
                                + "). Jag skapade ingen ny och ändrade inget i din. Säg till om du vill "
                                + "att jag läser annonsen igen och föreslår det som saknas. ";
                        default -> "I'm working from your map: [" + title + "](" + f.mapLink()
                                + "). I did not make a new one and I have changed nothing in yours. "
                                + "Say the word if you want me to read the advert again and suggest "
                                + "what is missing. ";
                    }
                    : switch (lang) {
                        case "ru" -> "Карта вакансии готова: [" + title + "](" + f.mapLink() + "). ";
                        case "sv" -> "Vakanskartan är klar: [" + title + "](" + f.mapLink() + "). ";
                        default -> "The vacancy map is ready: [" + title + "](" + f.mapLink() + "). ";
                    };
        String body = switch (lang) {
            case "ru" -> where + "Советую сначала открыть её и просмотреть: там по частям разложено всё, "
                    + "что есть в объявлении, и любую часть можно править.\n\n"
                    + "Что в ней и во что это превратится в CV:\n\n"
                    + "- **Факты** — локация, ссылка, требования к образованию, опыту и языкам, дедлайн. "
                    + "Рядом с образованием, опытом и языками есть поле для вашего комментария — из него "
                    + "пишутся разделы об образовании и языках.\n"
                    + "- **Skills** — навыки из объявления. Отметьте свои: отмеченные попадут в блок "
                    + "компетенций CV, неотмеченные нигде не появятся.\n"
                    + "- **Личные качества** — в CV их не называют прилагательными, их показывают примеры. "
                    + "Отметьте свои и оставьте в комментарии случай, который это показывает.\n"
                    + "- **Требования** — самое важное. Под каждым есть поле на каждое место работы "
                    + "(Company 1, 2, 3 — впишите вместо них настоящие названия): что вы там делали, что "
                    + "отвечает этому требованию. Из этого получаются пункты опыта в CV. Флажком "
                    + "отмечено самое важное — с него начнётся профиль.\n"
                    + "- **Дополнительная информация** — то, что стоит сказать в письме или в профиле CV; "
                    + "тег справа говорит, куда это пойдёт.\n"
                    + "- **О компании** — для мотивации в письме.\n"
                    + "- **Ваши данные** — контакты, места работы, образование, языки — хранятся отдельной "
                    + "заметкой и идут в шапку и структуру CV.";
            case "sv" -> where + "Öppna den gärna först och titta igenom den: där ligger allt i annonsen "
                    + "uppdelat i delar, och varje del går att ändra.\n\n"
                    + "Vad den innehåller och vad det blir i CV:t:\n\n"
                    + "- **Fakta** — plats, länk, krav på utbildning, erfarenhet och språk, sista ansökningsdag. "
                    + "Bredvid utbildning, erfarenhet och språk finns ett fält för din kommentar — det blir "
                    + "underlaget till avsnitten om utbildning och språk.\n"
                    + "- **Skills** — färdigheterna i annonsen. Markera dina: de markerade hamnar i CV:ts "
                    + "kompetensblock, de omarkerade syns ingenstans.\n"
                    + "- **Personliga egenskaper** — i ett CV skrivs de inte som adjektiv, de visas med "
                    + "exempel. Markera dina och skriv i kommentaren ett tillfälle som visar det.\n"
                    + "- **Krav** — det viktigaste. Under varje krav finns ett fält per arbetsplats "
                    + "(Company 1, 2, 3 — skriv de riktiga namnen i stället): vad du gjorde där som svarar "
                    + "mot kravet. Det blir erfarenhetspunkterna i CV:t. Flaggan markerar det viktigaste — "
                    + "profilen börjar med det.\n"
                    + "- **Övrig information** — sådant som är värt att säga i brevet eller i CV:ts profil; "
                    + "taggen till höger säger vart det hör.\n"
                    + "- **Om företaget** — motivationen i brevet.\n"
                    + "- **Dina uppgifter** — kontakt, arbetsplatser, utbildning, språk — sparas som en egen "
                    + "anteckning och blir CV:ts huvud och struktur.";
            default -> where + "I'd suggest opening it and looking through it first: everything in the "
                    + "advert is laid out there part by part, and you can change any of it.\n\n"
                    + "What's in it, and what it becomes in the CV:\n\n"
                    + "- **Facts** — the location, the link, what the advert asks for in education, "
                    + "experience and languages, the deadline. Beside education, experience and languages "
                    + "there's room for your own comment — that is what those sections of the CV are "
                    + "written from.\n"
                    + "- **Skills** — the skills the advert names. Tick yours: ticked ones go into the "
                    + "CV's competence block, unticked ones appear nowhere.\n"
                    + "- **Personal qualities** — a CV never states these as adjectives; examples show "
                    + "them. Tick yours and leave, in the comment, an occasion that shows it.\n"
                    + "- **Requirements** — the most important part. Under each one there's a box per "
                    + "place you've worked (Company 1, 2, 3 — put the real names over them): what you "
                    + "did there that answers it. Those become the experience bullets of the CV. The "
                    + "flag marks what matters most — the profile starts with it.\n"
                    + "- **Additional information** — things worth saying in the letter or in the CV's "
                    + "profile; the tag on the right says which.\n"
                    + "- **Company information** — the motivation in the letter.\n"
                    + "- **Your details** — contact, employers, education, languages — kept as a note of "
                    + "their own; they become the CV's header and its structure.";
        };
        String details = switch (f.intakeStatus() == null ? "none" : f.intakeStatus()) {
            case "exists" -> switch (lang) {
                case "ru" -> "\n\nВаши данные у меня уже есть — «" + f.intakeTitle() + "»"
                        + missingSuffix(f, lang) + ".";
                case "sv" -> "\n\nDina uppgifter har jag redan — «" + f.intakeTitle() + "»"
                        + missingSuffix(f, lang) + ".";
                default -> "\n\nI already have your details — “" + f.intakeTitle() + "”"
                        + missingSuffix(f, lang) + ".";
            };
            case "candidate" -> switch (lang) {
                case "ru" -> "\n\nВ ресурсах есть заметка «" + f.candidateTitle() + "» — если это ваши "
                        + "данные, нажмите «Использовать как мои данные».";
                case "sv" -> "\n\nBland resurserna finns anteckningen «" + f.candidateTitle() + "» — är det "
                        + "dina uppgifter, tryck på «Använd som mina uppgifter».";
                default -> "\n\nThere's a note in Resources called “" + f.candidateTitle() + "” — if those "
                        + "are your details, press “Use as my details”.";
            };
            default -> "";
        };
        String question = switch (lang) {
            case "ru" -> "\n\nХотите заполнить карту сами или вместе со мной? Если вместе — скажите, с "
                    + "какой части начнём.";
            case "sv" -> "\n\nVill du fylla i kartan själv eller tillsammans med mig? Om tillsammans — "
                    + "säg vilken del vi börjar med.";
            default -> "\n\nWould you like to fill the map in yourself, or together with me? If together, "
                    + "tell me which part to start with.";
        };
        return body + details + question;
    }

    private static String missingSuffix(Facts f, String lang) {
        if (f.intakeMissing() == null || f.intakeMissing().isEmpty()) return "";
        String missing = String.join(", ", f.intakeMissing());
        return switch (lang) {
            case "ru" -> " (не хватает: " + missing + ")";
            case "sv" -> " (saknas: " + missing + ")";
            default -> " (missing: " + missing + ")";
        };
    }

    private static String cvStep(String lang) {
        return switch (lang) {
            case "ru" -> "Спасибо, карта готова. Теперь я собираю всё вместе — ваши данные, навыки, "
                    + "ответы по требованиям, примеры личных качеств — и пишу CV целиком по карте, а не "
                    + "по кусочкам. Когда документ будет готов, дам ссылку на заметку, и вы скажете, что "
                    + "поправить.";
            case "sv" -> "Tack, kartan är klar. Nu sätter jag ihop allt — dina uppgifter, färdigheterna, "
                    + "svaren på kraven och exemplen på egenskaper — och skriver CV:t i sin helhet utifrån "
                    + "kartan, inte bit för bit. När dokumentet är klart får du en länk till anteckningen "
                    + "och säger vad som ska ändras.";
            default -> "Thank you — the map is ready. Now I put it all together: your details, the skills, "
                    + "your answers to the requirements and the examples of your qualities, and write the "
                    + "CV as a whole from the map rather than piece by piece. When it's ready I'll give you "
                    + "the link to the note and you tell me what to change.";
        };
    }

    private static String letterStep(String lang) {
        return switch (lang) {
            case "ru" -> "CV готово. Нужно ли сопроводительное письмо? Если да — начнём с главного: чем "
                    + "зацепить, что показать и почему именно эта компания.";
            case "sv" -> "CV:t är klart. Behövs ett personligt brev? Om ja börjar vi med det viktigaste: "
                    + "hur det fångar intresset, vad det visar och varför just det här företaget.";
            default -> "Your CV is done. Is a cover letter wanted? If so, we start with the essentials: how "
                    + "it opens, what it shows, and why this company.";
        };
    }

    // ── Status, waiting and failure lines ────────────────────────────────────

    /** When a turn produced nothing: where we are and what is needed, never "nothing came back". */
    public static String waiting(CvStep step, Facts f, String language) {
        String lang = lang(language);
        String need = switch (step) {
            case ANALYSIS -> switch (lang) {
                case "ru" -> "Напишите что угодно, и я продолжу разбор объявления.";
                case "sv" -> "Skriv vad som helst så fortsätter jag analysen.";
                default -> "Send me anything and I'll carry on with the analysis.";
            };
            case MAP -> switch (lang) {
                case "ru" -> "Заполняйте карту сами — или скажите, с какой части начнём вместе.";
                case "sv" -> "Fyll i kartan själv — eller säg vilken del vi tar tillsammans.";
                default -> "Fill the map in yourself — or tell me which part we should do together.";
            };
            default -> switch (lang) {
                case "ru" -> "Напишите, что поправить, или скажите «дальше».";
                case "sv" -> "Skriv vad som ska ändras, eller säg «vidare».";
                default -> "Tell me what to change, or say “next”.";
            };
        };
        return header(step, lang) + " — " + need;
    }

    public static String analysisStatus(String stage, String language) {
        String lang = lang(language);
        return switch (stage) {
            case "extract" -> switch (lang) {
                case "ru" -> "Читаю объявление по предложениям…";
                case "sv" -> "Läser annonsen mening för mening…";
                default -> "Reading the advert sentence by sentence…";
            };
            case "map" -> switch (lang) {
                case "ru" -> "Собираю карту вакансии…";
                case "sv" -> "Bygger vakanskartan…";
                default -> "Building the vacancy map…";
            };
            case "intake" -> switch (lang) {
                case "ru" -> "Читаю ваши документы…";
                case "sv" -> "Läser dina dokument…";
                default -> "Reading your documents…";
            };
            default -> switch (lang) {
                case "ru" -> "Пишу CV…";
                case "sv" -> "Skriver CV:t…";
                default -> "Writing the CV…";
            };
        };
    }

    /** The analysis is cut at the stream deadline and resumes in the next request by itself. */
    public static String stillWorking(String language) {
        return switch (lang(language)) {
            case "ru" -> "Разбор ещё идёт — продолжаю.";
            case "sv" -> "Analysen pågår fortfarande — jag fortsätter.";
            default -> "Still working through the advert — carrying on.";
        };
    }

    public static String analysisFailed(String language) {
        return switch (lang(language)) {
            case "ru" -> "Не получилось закончить разбор объявления — модель не ответила как нужно. "
                    + "Напишите что угодно, и я попробую снова; уже сделанное не пропадёт.";
            case "sv" -> "Jag kunde inte slutföra analysen — modellen svarade inte som den skulle. Skriv "
                    + "vad som helst så försöker jag igen; det som redan är gjort sparas.";
            default -> "I couldn't finish reading the advert — the model didn't answer in the form I need. "
                    + "Send me anything and I'll try again; what's done so far is kept.";
        };
    }

    /**
     * The provider refused the KEY — expired, revoked, or not allowed to use the model.
     *
     * <p>Its own message, because nothing the user can do in this conversation fixes it: she has
     * to replace the key. It used to be reported as an overloaded service (owner, 2026-09-22),
     * which invited her to wait a minute for something that would never come back on its own.
     */
    public static String keyRejected(String language) {
        return switch (lang(language)) {
            case "ru" -> "\u041f\u0440\u043e\u0432\u0430\u0439\u0434\u0435\u0440 \u043d\u0435 \u043f\u0440\u0438\u043d\u044f\u043b \u0432\u0430\u0448 API-\u043a\u043b\u044e\u0447 \u2014 \u0441\u043a\u043e\u0440\u0435\u0435 \u0432\u0441\u0435\u0433\u043e, \u043e\u043d \u0438\u0441\u0442\u0451\u043a \u0438\u043b\u0438 \u0431\u043e\u043b\u044c\u0448\u0435 \u043d\u0435 "
                    + "\u0434\u0435\u0439\u0441\u0442\u0432\u0443\u0435\u0442. \u041e\u0442\u043a\u0440\u043e\u0439\u0442\u0435 \u00ab\u0421\u0432\u043e\u0439 \u043a\u043b\u044e\u0447\u00bb \u0438 \u0437\u0430\u043c\u0435\u043d\u0438\u0442\u0435 \u0435\u0433\u043e, \u043f\u043e\u0442\u043e\u043c \u043d\u0430\u043f\u0438\u0448\u0438\u0442\u0435 \u0447\u0442\u043e \u0443\u0433\u043e\u0434\u043d\u043e \u2014 \u044f "
                    + "\u043f\u0440\u043e\u0434\u043e\u043b\u0436\u0443 \u0441 \u0442\u043e\u0433\u043e \u0436\u0435 \u043c\u0435\u0441\u0442\u0430; \u0443\u0436\u0435 \u0441\u0434\u0435\u043b\u0430\u043d\u043d\u043e\u0435 \u043d\u0435 \u043f\u0440\u043e\u043f\u0430\u0434\u0451\u0442.";
            case "sv" -> "Leverant\u00f6ren avvisade din API-nyckel \u2014 den har troligen g\u00e5tt ut eller "
                    + "\u00e5terkallats. \u00d6ppna \u00abEgen nyckel\u00bb och byt ut den, skriv sedan vad som helst \u2014 jag "
                    + "forts\u00e4tter d\u00e4r vi slutade; det som redan \u00e4r gjort sparas.";
            default -> "The provider rejected your API key \u2014 it has most likely expired or been revoked. "
                    + "Open \u201cBring your own key\u201d and replace it, then send me anything \u2014 I'll carry on from "
                    + "where it stopped; what's done so far is kept.";
        };
    }

    /** The provider refused (overloaded or rate-limited); nothing done so far is lost. */
    public static String analysisBusy(String language) {
        return switch (lang(language)) {
            case "ru" -> "Сервис модели сейчас перегружен и не ответил. Подождите минуту и напишите что "
                    + "угодно — я продолжу разбор с того места, где он остановился.";
            case "sv" -> "Modelltjänsten är överbelastad just nu och svarade inte. Vänta en minut och skriv "
                    + "vad som helst — jag fortsätter analysen där den stannade.";
            default -> "The model service is overloaded right now and did not answer. Wait a minute and send "
                    + "me anything — I'll carry on from where it stopped.";
        };
    }

    /**
     * The analysis summary for an advert that states nothing to match against. The map exists all
     * the same, with whatever facts the advert did give; its requirements are filled in with her.
     */
    public static String noRequirements(String language) {
        return switch (lang(language)) {
            case "ru" -> "В этом объявлении не сказано, что именно ищет работодатель. Карта вакансии "
                    + "всё равно создана — с тем, что в объявлении есть. Требования в неё можно вписать "
                    + "вместе со мной: по двум-трём похожим объявлениям или по названию должности.";
            case "sv" -> "Annonsen säger inte vad arbetsgivaren söker. Vakanskartan är ändå skapad — med "
                    + "det som står i annonsen. Kraven kan vi fylla i tillsammans: utifrån två-tre liknande "
                    + "annonser, eller utifrån rollens namn.";
            default -> "This advert doesn't say what the employer is looking for. The vacancy map is made "
                    + "all the same, with what the advert does give. We can fill the requirements in "
                    + "together — from two or three comparable adverts, or from the name of the role.";
        };
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
