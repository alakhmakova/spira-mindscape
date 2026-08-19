package com.spiramindscape.android.ui.goals

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.spiramindscape.android.ui.components.SpiraFormSheet
import com.spiramindscape.android.ui.components.SpiraTextField
import com.spiramindscape.android.ui.util.FieldLimits

/**
 * Bottom-sheet form to create one option, reached from the Options page's "Add option" button.
 * The same shape as the goal / target / reality create sheets.
 *
 * It replaced an inline "Add an option…" field at the foot of the list — two ways to add the same
 * thing on one screen, and the inline one drifted out of view as the list grew.
 */
@Composable
fun NewOptionSheet(onDismiss: () -> Unit, onCreate: (text: String) -> Unit) {
    var text by remember { mutableStateOf("") }
    SpiraFormSheet(
        title = "New option",
        onDismiss = onDismiss,
        confirmLabel = "Add option",
        onConfirm = { onCreate(text.trim()) },
        // Never save an empty option, and never one past the server's limit — an over-long value
        // would be rejected and the optimistic row would snap back on the next refetch.
        confirmEnabled = text.isNotBlank() && text.trim().length <= FieldLimits.OPTION_TEXT,
    ) {
        SpiraTextField(
            text,
            { text = it },
            "What could move this goal forward?",
            singleLine = false,
            minLines = 3,
        )
    }
}
