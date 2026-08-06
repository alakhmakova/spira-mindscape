package com.spiramindscape.android.ui.util

import com.spiramindscape.android.data.goals.ChecklistItemModel
import com.spiramindscape.android.data.goals.TargetItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Kotlin side of `src/lib/spira/progress.test.ts` — the two surfaces must print the same
 * percentage for the same target, so these mirror the web cases.
 */
class ProgressTest {

    private fun numeric(current: Double, total: Double, start: Double = 0.0, progress: Float) =
        TargetItem.Numeric(
            id = "t1", title = "Save", progress = progress, deadline = null, achieved = false,
            current = current, total = total, start = start, unit = "SEK",
        )

    private fun checklist(done: Int, of: Int, locked: Boolean? = null) = TargetItem.Checklist(
        id = "t2", title = "Steps", progress = if (of == 0) 0f else done.toFloat() / of,
        deadline = null, achieved = false,
        items = (1..of).map { ChecklistItemModel("i$it", "task $it", it <= done) },
        progressLocked = locked,
    )

    // ── formatPercent ────────────────────────────────────────────────────────

    @Test
    fun `a coarse target prints whole percent`() {
        assertEquals("50", formatPercent(0.5f, 4))
        assertEquals("25", formatPercent(0.25f, 4))
    }

    @Test
    fun `a large numeric target keeps the decimals that distinguish two values`() {
        // 10 000 and 20 000 of 1 900 000 both round to "1" — the bug this precision rule fixes.
        val steps = 1_900_000
        assertEquals("0.53", formatPercent((10_000.0 / 1_900_000).toFloat(), steps))
        assertEquals("1.05", formatPercent((20_000.0 / 1_900_000).toFloat(), steps))
    }

    @Test
    fun `zero and one hundred print only when they are true`() {
        assertEquals("0", formatPercent(0f, 1_900_000))
        assertEquals("100", formatPercent(1f, 1_900_000))
    }

    @Test
    fun `a tiny non-zero fraction never prints as zero`() {
        val text = formatPercent(0.00001f, 1_900_000)
        assertTrue("expected a non-zero reading, got $text", text != "0")
    }

    @Test
    fun `an almost-complete target never prints as one hundred`() {
        val text = formatPercent(0.9999999f, 1_900_000)
        assertTrue("expected a sub-100 reading, got $text", text != "100")
    }

    @Test
    fun `trailing zeros are trimmed`() {
        // 1 000 steps → one decimal; 50% must still read "50", not "50.0".
        assertEquals("50", formatPercent(0.5f, 1_000))
    }

    @Test
    fun `out of range fractions are clamped`() {
        assertEquals("0", formatPercent(-1f, 4))
        assertEquals("100", formatPercent(2f, 4))
    }

    // ── progressSteps ────────────────────────────────────────────────────────

    @Test
    fun `steps follow the target type`() {
        assertEquals(4, progressSteps(checklist(done = 1, of = 4)))
        assertEquals(1, progressSteps(TargetItem.Binary("b", "Ship", 0f, null, false, done = false)))
        assertEquals(1_900_000, progressSteps(numeric(0.0, 1_900_000.0, progress = 0f)))
    }

    @Test
    fun `a countdown target measures the distance travelled`() {
        assertEquals(100, progressSteps(numeric(current = 40.0, total = 0.0, start = 100.0, progress = 0.6f)))
    }

    // ── isProgressLocked ─────────────────────────────────────────────────────

    @Test
    fun `an achieved target locks itself`() {
        assertTrue(isProgressLocked(checklist(done = 2, of = 2)))
    }

    @Test
    fun `an unfinished target is open`() {
        assertFalse(isProgressLocked(checklist(done = 1, of = 2)))
    }

    @Test
    fun `the users explicit choice always wins`() {
        assertFalse(isProgressLocked(checklist(done = 2, of = 2, locked = false)))
        assertTrue(isProgressLocked(checklist(done = 0, of = 2, locked = true)))
    }
}
