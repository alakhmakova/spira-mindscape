package com.spiramindscape.android.ui.goals

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.spiramindscape.android.ui.components.SpiraFormSheet
import com.spiramindscape.android.ui.components.SpiraTextField

/**
 * Bottom-sheet form to create an option (a strategy). Mirrors the goal/target create sheets —
 * creation happens in a form, while the option cards themselves stay inline-editable.
 */
@Composable
fun NewOptionSheet(
    onDismiss: () -> Unit,
    onCreate: (text: String) -> Unit,
) {
    var text by remember { mutableStateOf("") }

    SpiraFormSheet(
        title = "New option",
        onDismiss = onDismiss,
        confirmLabel = "Add option",
        onConfirm = { onCreate(text) },
        confirmEnabled = text.isNotBlank(),
    ) {
        SpiraTextField(text, { text = it }, "What's this strategy?", singleLine = false, minLines = 3)
    }
}
