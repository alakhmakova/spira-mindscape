package com.spiramindscape.android.ui.goals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.spiramindscape.android.ui.components.ConfidenceStepper
import com.spiramindscape.android.ui.components.DeadlineField
import com.spiramindscape.android.ui.components.FieldLabel
import com.spiramindscape.android.ui.components.SpiraFormSheet
import com.spiramindscape.android.ui.components.SpiraTextField

/**
 * Bottom-sheet form to create a goal (mirrors the web `NewGoalSheet`): title (required),
 * description, confidence (1–10), and an optional deadline.
 */
@Composable
fun NewGoalSheet(
    onDismiss: () -> Unit,
    creating: Boolean,
    onCreate: (title: String, description: String?, confidence: Int, deadline: String?) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var confidence by remember { mutableStateOf(5) }
    var deadline by remember { mutableStateOf<String?>(null) }

    SpiraFormSheet(
        title = "New goal",
        onDismiss = onDismiss,
        confirmLabel = if (creating) "Creating…" else "Create goal",
        onConfirm = { onCreate(title, description, confidence, deadline) },
        confirmEnabled = title.isNotBlank() && !creating,
    ) {
        SpiraTextField(title, { title = it }, "Title")
        SpiraTextField(description, { description = it }, "Description", singleLine = false, minLines = 3)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FieldLabel("Confidence: $confidence/10")
            ConfidenceStepper(confidence, { confidence = it })
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FieldLabel("Deadline")
            DeadlineField(deadline, { deadline = it })
        }
    }
}
