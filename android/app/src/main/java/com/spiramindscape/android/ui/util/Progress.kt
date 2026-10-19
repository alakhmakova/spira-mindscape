package com.spiramindscape.android.ui.util

import com.spiramindscape.android.data.goals.TargetItem
import kotlin.math.abs
import kotlin.math.min

/**
 * Progress presentation rules, ported from the web `src/lib/spira/progress.ts` so a target reads
 * the same on both surfaces. The *value* still comes from the server (`TargetItem.progress`);
 * what lives here is how many steps a target has and how many decimals its percentage needs.
 */

/**
 * Whether a target's progress is pinned. An achieved target locks itself so a stray tap can't
 * undo it; anything else is open unless the user locked it deliberately. Either way the user's
 * explicit choice (`progressLocked`) wins, so a finished target can be unlocked to correct it.
 */
fun isProgressLocked(target: TargetItem): Boolean =
    target.progressLocked ?: (target.progress >= 1f)

/**
 * How many discrete steps a target's progress can take: 4 for a four-task checklist, 1 900 000
 * for a 0 → 1 900 000 SEK goal, 1 for a binary one. This is what decides how many decimals a
 * percentage needs — see [formatPercent].
 */
fun progressSteps(target: TargetItem): Int = when (target) {
    is TargetItem.Binary -> 1
    is TargetItem.Numeric -> {
        val start = target.start ?: if (target.total != null && target.current > target.total) target.current else 0.0
        val total = target.total ?: target.current
        // A step is one unit, so the count is the distance — capped so a huge range can't
        // overflow Int (any value that large already asks for the finest precision).
        min(abs(total - start), Int.MAX_VALUE.toDouble()).toInt()
    }
    is TargetItem.Checklist -> target.items.size
    is TargetItem.Other -> 0
}

/**
 * A GOAL's progress as the percentage to show — **always a whole number** (owner, 2026-10-19),
 * mirroring the web's `formatGoalPercent`. The decimals [formatPercent] gives a fine-grained
 * *target* stay on the target. Only a genuine 0 and a genuine 1 print "0" and "100": a goal that
 * has moved shows at least 1, one that is not finished at most 99.
 */
fun formatGoalPercent(fraction: Float): String {
    val clamped = fraction.toDouble().coerceIn(0.0, 1.0)
    if (clamped == 0.0 || clamped == 1.0) return (clamped * 100).toInt().toString()
    return Math.round(clamped * 100).toInt().coerceIn(1, 99).toString()
}

/** Trailing zeros carry no information: "50.00" is noise where "50" says the same thing. */
private fun trimZeros(s: String): String =
    if (!s.contains('.')) s else s.trimEnd('0').trimEnd('.')

/**
 * A progress fraction as the percentage to show the user.
 *
 * Whole percent is right for a four-task checklist and wrong for a 1 900 000 SEK target: there,
 * 10 000 and 20 000 both print "1%", so weeks of saving look like nothing happened. [steps] —
 * how many increments the target actually has ([progressSteps]) — decides the precision: when one
 * step is worth less than a tenth of a percent we print two decimals, less than a whole percent
 * one decimal, otherwise none. Trailing zeros are trimmed, so a checklist still reads "50%".
 *
 * Whatever the precision, a genuine 0 and a genuine 1 are the ONLY values that may print as "0"
 * and "100" — anything else that would round to them gets more decimals, or "<0.01" / ">99.99".
 */
fun formatPercent(fraction: Float, steps: Int? = null): String {
    val clamped = fraction.toDouble().coerceIn(0.0, 1.0)
    val pct = clamped * 100
    if (pct == 0.0 || pct == 100.0) return pct.toInt().toString()

    // One step, as a percentage of the whole → the smallest change worth showing.
    val stepPct = if (steps != null && steps > 0) 100.0 / steps else 100.0
    val decimals = if (stepPct >= 1) 0 else if (stepPct >= 0.1) 1 else 2

    for (d in decimals..2) {
        val text = trimZeros(String.format(java.util.Locale.US, "%.${d}f", pct))
        // Never let rounding claim the target is untouched or finished when it isn't.
        if (text != "0" && text != "100") return text
    }
    return if (pct < 50) "<0.01" else ">99.99"
}

/** The percentage for a target, at the resolution that target deserves. */
fun formatTargetPercent(target: TargetItem, progress: Float = target.progress): String =
    formatPercent(progress, progressSteps(target))
