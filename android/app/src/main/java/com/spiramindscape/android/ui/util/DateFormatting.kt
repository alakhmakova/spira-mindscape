package com.spiramindscape.android.ui.util

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** "due <date> · <N days left / overdue / today>" (mirrors the web `DeadlineLabel`). */
fun deadlineCountdown(iso: String): String {
    val date = try {
        Instant.parse(iso).atZone(ZoneOffset.UTC).toLocalDate()
    } catch (e: Exception) {
        return "due ${iso.take(10)}"
    }
    val today = java.time.LocalDate.now()
    val days = ChronoUnit.DAYS.between(today, date)
    val relative = when {
        days > 1 -> "$days days left"
        days == 1L -> "1 day left"
        days == 0L -> "due today"
        days == -1L -> "1 day overdue"
        else -> "${-days} days overdue"
    }
    return "due $date  ·  $relative"
}

/** The big-number / caption pair for the deadline stat card. */
data class DeadlineCountdownParts(val bigText: String, val caption: String)

fun deadlineCountdownParts(iso: String?): DeadlineCountdownParts {
    if (iso == null) return DeadlineCountdownParts("–", "No deadline set")
    val date = try {
        Instant.parse(iso).atZone(ZoneOffset.UTC).toLocalDate()
    } catch (e: Exception) {
        return DeadlineCountdownParts("–", "No deadline set")
    }
    val days = ChronoUnit.DAYS.between(java.time.LocalDate.now(), date)
    return when {
        days > 1 -> DeadlineCountdownParts("$days", "Days until deadline")
        days == 1L -> DeadlineCountdownParts("1", "Day until deadline")
        days == 0L -> DeadlineCountdownParts("Today", "Deadline is today")
        days == -1L -> DeadlineCountdownParts("1", "Day overdue")
        else -> DeadlineCountdownParts("${-days}", "Days overdue")
    }
}

/** "Nov 1, 2026" (matches the web's date link format). */
fun formatDeadlineDate(iso: String): String = try {
    Instant.parse(iso).atZone(ZoneOffset.UTC).toLocalDate()
        .format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
} catch (e: Exception) {
    iso.take(10)
}

/** "Jan 5, 2026 · 14:30" (mirrors the web history row timestamp). */
fun formatHistoryTimestamp(iso: String): String = try {
    Instant.parse(iso).atZone(ZoneOffset.systemDefault())
        .format(DateTimeFormatter.ofPattern("MMM d, yyyy · HH:mm"))
} catch (e: Exception) {
    iso
}

/** "3 days ago" / "2 hours ago" / "just now" (mirrors the web's formatDistanceToNow). */
fun relativeTime(iso: String): String {
    val then = try { Instant.parse(iso) } catch (e: Exception) { return "" }
    val seconds = ChronoUnit.SECONDS.between(then, Instant.now())
    return when {
        seconds < 60 -> "just now"
        seconds < 3600 -> "${seconds / 60} minute${if (seconds / 60 == 1L) "" else "s"} ago"
        seconds < 86400 -> "${seconds / 3600} hour${if (seconds / 3600 == 1L) "" else "s"} ago"
        else -> "${seconds / 86400} day${if (seconds / 86400 == 1L) "" else "s"} ago"
    }
}
