package com.spiramindscape.android.ui.settings

/**
 * The copy for **About Spira**. A verbatim port of `src/lib/spira/about-content.ts` —
 * change one, change both. The two surfaces mirror one design, and this page in
 * particular has to read the same on a phone and a laptop, because it is the one place
 * that explains what the coach is doing and why.
 *
 * ## Why this page exists
 *
 * Two reasons, and the first is the real one. Spira's central mechanic is
 * counter-intuitive: an AI that deliberately refuses to give advice. The coaching
 * traditions this draws on all start a relationship the same way — by explaining what
 * coaching is, what it isn't, and what it needs from the person being coached. Without
 * that, a user meets a coach that won't answer their question and concludes it is broken.
 * The second reason is that the coach is barred from explaining its own method during a
 * session (naming the technique is what stops it working), so the explanation has to live
 * somewhere. This is where, and it is where the coach points anyone who asks how it works.
 *
 * ## Why it is written the way it is
 *
 * Every word is original. Ideas, facts and methods are free to use; an author's sentences
 * are not, and a summary that follows a book's own selection and running order can itself
 * be a derivative work. So this is Spira explaining coaching, not a digest of anyone's
 * book: no passages, no borrowed structure, no reused examples. The books appear only
 * under Further reading, as the field's standard texts.
 */

sealed interface AboutBlock {
    data class Prose(val text: String) : AboutBlock
    data class Points(val items: List<AboutPoint>) : AboutBlock
}

data class AboutPoint(val term: String, val text: String)

data class AboutSection(val heading: String, val blocks: List<AboutBlock>)

data class AboutBook(val title: String, val author: String, val note: String, val href: String)

val ABOUT_SECTIONS: List<AboutSection> = listOf(
    AboutSection(
        heading = "What coaching is — and what it isn't",
        blocks = listOf(
            AboutBlock.Prose(
                "Coaching is a thinking partnership. A coach doesn't hand you answers or a " +
                    "plan. They listen closely, play your own words back to you, and ask the " +
                    "question that makes you look at them again. Most of the time you already " +
                    "hold the answer; what you don't have is a way to see past the story you " +
                    "have been telling yourself about it.",
            ),
            AboutBlock.Prose(
                "That is why it works where advice doesn't. Advice arrives from outside, and " +
                    "it is easy to agree with and forget by Monday. Something you worked out " +
                    "yourself changes what you do, because it changed how you see the thing.",
            ),
            AboutBlock.Points(
                listOf(
                    AboutPoint(
                        "Not advice, and not consulting",
                        "A consultant brings their expertise and tells you what to do. A coach " +
                            "starts from the opposite assumption: the expertise about your life " +
                            "is yours.",
                    ),
                    AboutPoint(
                        "Not mentoring",
                        "A mentor has walked a path like yours and shares the route they took. " +
                            "A coach has no route to offer and is not trying to find one for you.",
                    ),
                    AboutPoint(
                        "Not therapy",
                        "Therapy works with distress, with the past, and with mental health, " +
                            "and it is done by trained clinicians. Coaching starts from where " +
                            "you are now and works forwards. The two are not substitutes, and " +
                            "neither one is the lesser thing.",
                    ),
                    AboutPoint(
                        "Not encouragement",
                        "A session can be uncomfortable. Being asked something you would rather " +
                            "not answer is usually the moment it starts working.",
                    ),
                ),
            ),
            AboutBlock.Prose(
                "There is a line, and Spira's coach will tell you when you have reached it. If " +
                    "you are in distress or in danger, or the question is a medical one, a legal " +
                    "one, or about money you cannot afford to lose, that needs a qualified " +
                    "professional — not a coach, and not an AI. Hearing that is not the session " +
                    "failing. It is the coach doing its job.",
            ),
        ),
    ),
    AboutSection(
        heading = "What GROW is",
        blocks = listOf(
            AboutBlock.Prose(
                "GROW is a shape for a coaching conversation, developed in the 1980s by Sir " +
                    "John Whitmore and colleagues, and it is the most widely used one there is. " +
                    "Four questions, in a useful order:",
            ),
            AboutBlock.Points(
                listOf(
                    AboutPoint(
                        "Goal",
                        "What do you want? Not what is wrong — what would be different, and " +
                            "what would make this half hour worth spending.",
                    ),
                    AboutPoint(
                        "Reality",
                        "Where are you now? What is actually happening, what you have already " +
                            "tried, and what is getting in the way.",
                    ),
                    AboutPoint(
                        "Options",
                        "What could you do? All of it, including the ideas you would normally " +
                            "dismiss before saying them out loud.",
                    ),
                    AboutPoint(
                        "Will",
                        "What will you do? One thing, with a when attached to it.",
                    ),
                ),
            ),
            AboutBlock.Prose(
                "It is a map, not a form. A conversation can start anywhere on it, double back, " +
                    "or skip the parts you do not need — and a good session usually does. " +
                    "Spira's coach never announces which part you are in, because naming the " +
                    "machinery turns a conversation into a procedure.",
            ),
        ),
    ),
    AboutSection(
        heading = "How a session goes, and what it needs from you",
        blocks = listOf(
            AboutBlock.Prose(
                "You choose how long the session runs. The coach opens by asking what you want " +
                    "out of the time — not what is wrong, but what should be clearer by the " +
                    "end. After that it mostly listens, reflects back what it heard, and asks " +
                    "one question at a time. It will not tell you what to do.",
            ),
            AboutBlock.Prose(
                "The session ends when the work is done, which may be before the clock runs " +
                    "out. Nothing is written to your goal while you are still talking: at the " +
                    "end you decide what to keep, and every change is yours to approve or refuse.",
            ),
            AboutBlock.Prose("What makes it work is mostly on your side of the conversation:"),
            AboutBlock.Points(
                listOf(
                    AboutPoint(
                        "Answer the awkward question",
                        "The one you would rather skip is usually the one that moves something.",
                    ),
                    AboutPoint(
                        "Take part, don't watch",
                        "This is not a conversation that works if you observe it from a distance.",
                    ),
                    AboutPoint(
                        "Say the true version",
                        "Including the part that makes you look bad. Nobody is keeping score.",
                    ),
                    AboutPoint(
                        "Do the thing you said you would do",
                        "A session that ends in a commitment and nothing else is a session that " +
                            "changed nothing.",
                    ),
                    AboutPoint(
                        "Give it a few minutes afterwards",
                        "A lot of what shifts, shifts in the days after the session rather than " +
                            "during it.",
                    ),
                ),
            ),
        ),
    ),
    AboutSection(
        heading = "How to use Spira",
        blocks = listOf(
            AboutBlock.Points(
                listOf(
                    AboutPoint(
                        "Goals",
                        "What you are going for. Everything else in the app hangs off one.",
                    ),
                    AboutPoint(
                        "Reality",
                        "What is actually happening around a goal: the obstacles in the way, " +
                            "and the actions you have already taken.",
                    ),
                    AboutPoint(
                        "Options",
                        "Strategies you could follow. Mark the one you are running with, and " +
                            "note how each turned out as you learn.",
                    ),
                    AboutPoint(
                        "Targets",
                        "The measurable pieces of a goal — a simple done or not done, a number " +
                            "to reach, or a checklist.",
                    ),
                    AboutPoint(
                        "Resources",
                        "Notes, links, files and contacts kept with the goal they belong to, so " +
                            "the material is where the work is.",
                    ),
                    AboutPoint(
                        "The coach",
                        "Two different things share one panel. Ordinary chat is an assistant: " +
                            "it can look things up, draft, and propose changes to a goal. A " +
                            "GROW session is coaching, and it stays coaching for the whole " +
                            "session.",
                    ),
                    AboutPoint(
                        "Memory",
                        "At the end of a session you decide whether the coach keeps a record of " +
                            "it. If you don't save it, there is nothing for it to carry into " +
                            "next time.",
                    ),
                    AboutPoint(
                        "Your API key",
                        "Spira runs on a key you provide, stored encrypted on your account. You " +
                            "choose the provider and the model, and you can switch whenever you " +
                            "like.",
                    ),
                ),
            ),
        ),
    ),
)

/**
 * The field's standard texts, for anyone who wants to read further. Publisher pages,
 * verified live 2026-08-23.
 */
val ABOUT_FURTHER_READING: List<AboutBook> = listOf(
    AboutBook(
        title = "Coaching for Performance",
        author = "Sir John Whitmore and Tiffany Gaskell",
        note = "The book that introduced GROW, and still the standard text on coaching at work.",
        href = "https://www.performanceconsultants.com/resources/coaching-for-performance-book/",
    ),
    AboutBook(
        title = "Coach the Person, Not the Problem",
        author = "Marcia Reynolds",
        note = "On reflective inquiry — why playing someone's own words back to them does more " +
            "than asking clever questions.",
        href = "https://www.penguinrandomhouse.com/books/808347/" +
            "coach-the-person-not-the-problem-second-edition-by-marcia-reynolds/",
    ),
)
