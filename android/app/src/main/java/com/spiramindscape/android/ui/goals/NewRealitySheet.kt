package com.spiramindscape.android.ui.goals

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.spiramindscape.android.ui.components.SpiraFormSheet
import com.spiramindscape.android.ui.components.SpiraTextField

/**
 * Bottom-sheet form to create a Reality item — an action or an obstacle, chosen by [kind]
 * ("actions"/"obstacles"). Mirrors the goal/target/option create sheets: creation happens in a
 * form, while the saved items stay inline-editable.
 */
@Composable
fun NewRealitySheet(
    kind: String,
    onDismiss: () -> Unit,
    onCreate: (text: String) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    val isAction = kind == "actions"
    val noun = if (isAction) "action" else "obstacle"

    SpiraFormSheet(
        title = if (isAction) "New action" else "New obstacle",
        onDismiss = onDismiss,
        confirmLabel = if (isAction) "Add action" else "Add obstacle",
        onConfirm = { onCreate(text) },
        confirmEnabled = text.isNotBlank(),
    ) {
        SpiraTextField(
            text,
            { text = it },
            "Describe this $noun",
            singleLine = false,
            minLines = 3,
        )
    }
}
