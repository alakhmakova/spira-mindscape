package com.spiramindscape.android.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **A convention test, because this one cannot be rendered.**
 *
 * The chat's floating footer measures itself into the transcript's bottom `contentPadding`, and
 * the measurement has to include the insets it is padded by — the navigation bar, and the keyboard
 * while one is up. Modifiers apply outside-in and each padding reports the *padded* size upward,
 * so `onSizeChanged` placed **after** `imePadding()` / `navigationBarsPadding()` measures the
 * footer's content and misses both. That shipped: the transcript was padded short by a nav bar,
 * its last messages sat behind the footer, and nothing could scroll to them (owner, 2026-08-29:
 * "невозможно прокрутить до конца"). It also made the keyboard scroll look broken — it ran, and
 * landed under the composer.
 *
 * Robolectric cannot catch it. Its window reports **zero** system-bar insets, so the two orderings
 * measure the same number there and a rendered assertion passes either way — the same blindness
 * `DeadlinePickerDialogTest` documents for a dialog's platform width. What is left is to read the
 * source, which is what `sheet-units.test.ts` does on the web for vaul's `repositionInputs`.
 *
 * Comments are stripped first: the note explaining this rule sits directly above the code, and a
 * scan that accepts its own explanation as evidence proves nothing.
 */
class ChatFooterConventionTest {

    @Test
    fun `the footer measures itself outside its inset padding`() {
        val code = File("src/main/java/com/spiramindscape/android/ui/ai/AiChatScreen.kt")
            .readText()
            .lines()
            .filterNot { it.trim().startsWith("//") }
            .joinToString("\n")

        // **Only the footer's own chain.** The first version searched the whole file from the
        // `onSizeChanged` onwards, so with the order reverted it simply found the *next*
        // `.imePadding()` further down — the composer has one — and passed. A scan for "is A
        // before B" has to be given the one block both are supposed to be in.
        // Taken LINE by line, ending at the line that is exactly `) {`. Slicing on the first
        // `") {"` cut the chain short at `with(density) {` inside the `onSizeChanged` lambda,
        // which then reported the keyboard padding missing rather than mis-ordered — a scan can
        // be wrong in a way that looks like the thing it is looking for.
        val lines = code.lines()
        val first = lines.indexOfFirst { it.contains(".align(Alignment.BottomCenter)") }
        assertTrue("the footer is no longer the bottom-aligned column", first >= 0)
        val last = lines.drop(first).indexOfFirst { it.trim() == ") {" } + first
        val chain = lines.subList(first, last)

        val measured = chain.indexOfFirst { it.contains(".onSizeChanged { footerHeight") }
        val ime = chain.indexOfFirst { it.contains(".imePadding()") }
        val nav = chain.indexOfFirst { it.contains(".navigationBarsPadding()") }

        assertTrue("the footer no longer measures itself into `footerHeight`", measured >= 0)
        assertTrue("the footer no longer pads itself for the keyboard", ime >= 0)
        assertTrue("the footer no longer pads itself for the navigation bar", nav >= 0)
        assertTrue(
            "`onSizeChanged` must come BEFORE `imePadding()` in the footer's chain, or it " +
                "measures the footer without the keyboard and the transcript is padded short.",
            measured < ime,
        )
        assertTrue(
            "`onSizeChanged` must come BEFORE `navigationBarsPadding()`, for the same reason.",
            measured < nav,
        )
    }
}
