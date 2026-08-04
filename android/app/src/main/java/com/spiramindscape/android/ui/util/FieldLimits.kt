package com.spiramindscape.android.ui.util

/**
 * Canonical maximum lengths for user-editable text fields — the Kotlin mirror of the web
 * `src/lib/spira/limits.ts`, which in turn mirrors the server-side limits. A value that passes
 * here can never be rejected by the backend (which on mobile would surface as an edit that
 * silently reverts on the next refetch).
 */
object FieldLimits {
    const val GOAL_TITLE = 200
    const val GOAL_DESCRIPTION = 5000
    const val TARGET_TITLE = 200
    const val TARGET_UNIT = 50
    const val OPTION_TEXT = 500
    const val REALITY_TEXT = 500
    const val CHECKLIST_TEXT = 500
    const val RESOURCE_LABEL = 200
}
