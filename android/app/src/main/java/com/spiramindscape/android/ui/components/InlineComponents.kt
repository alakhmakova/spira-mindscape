package com.spiramindscape.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.spiramindscape.android.ui.icons.SpiraIcons
import com.spiramindscape.android.ui.theme.spiraExtras

/**
 * True inline-editable text (the goal-page pattern required by specs/tech-stack.md and mirrored
 * from the web `InlineText`): looks like the surrounding text — no box, no floating label — and
 * becomes editable in place. Commits (via [onCommit]) on **focus loss** and on the **Done** key,
 * never per keystroke. A [required] field reverts to the last good value if emptied. Re-seeds
 * from [value] when it changes externally (e.g. after a reload).
 */
@Composable
fun InlineEditText(
    value: String,
    onCommit: (String) -> Unit,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    placeholder: String = "",
    singleLine: Boolean = true,
    minLines: Int = 1,
    required: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    // Multiline fields default to a literal newline on Enter (e.g. a description). Pass Done
    // explicitly for fields that should commit on Enter even while wrapping (e.g. a Reality item).
    imeAction: ImeAction = if (singleLine) ImeAction.Done else ImeAction.Default,
    textAlign: TextAlign = TextAlign.Start,
    onFocusChanged: (Boolean) -> Unit = {},
) {
    var text by remember { mutableStateOf(value) }
    var focused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    // Adopt an external change to `value` only when the user is NOT editing — otherwise a
    // recomposition (progress recompute, another field's optimistic update, a refetch) must not
    // wipe the text the user is currently typing. This is the fix for "my edits aren't saved".
    LaunchedEffect(value) { if (!focused) text = value }

    fun commit() {
        if (required && text.isBlank()) {
            text = value // revert — required fields never save empty
            return
        }
        if (text != value) onCommit(text)
    }

    // If this row is disposed while still focused (e.g. scrolled away / navigated), commit the
    // pending edit so nothing is lost even when onFocusChanged(blur) doesn't fire. Also force
    // focus off: a focused BasicTextField keeps its cursor blink animation running, and leaving
    // it dangling past disposal is what causes a cursor to "blink forever" — the field is gone,
    // but nothing ever told the animation to stop.
    val pending = rememberUpdatedState(Triple(text, value, required))
    val wasFocused = rememberUpdatedState(focused)
    DisposableEffect(Unit) {
        onDispose {
            val (t, v, req) = pending.value
            if (t != v && !(req && t.isBlank())) onCommit(t)
            if (wasFocused.value) focusManager.clearFocus(force = true)
        }
    }

    val effectiveStyle = textStyle.merge(TextStyle(color = MaterialTheme.colorScheme.onSurface, textAlign = textAlign))
    val centered = textAlign == TextAlign.Center

    BasicTextField(
        value = text,
        onValueChange = { text = it },
        modifier = (if (centered) modifier.fillMaxWidth() else modifier)
            .onFocusChanged { state ->
                if (focused && !state.isFocused) commit()
                focused = state.isFocused
                onFocusChanged(state.isFocused)
            },
        textStyle = effectiveStyle,
        singleLine = singleLine,
        minLines = minLines,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(
            imeAction = imeAction,
            keyboardType = keyboardType,
        ),
        keyboardActions = KeyboardActions(onDone = {
            commit()
            focusManager.clearFocus()
        }),
        decorationBox = { inner ->
            // When centered, the core text field is measured to its content width, so aligning it
            // to the Box centre is what actually centres short text (textAlign alone only centres
            // WITHIN that content-sized field, which is invisible). Left fields keep top-start.
            Box(
                modifier = if (centered) Modifier.fillMaxWidth() else Modifier,
                contentAlignment = if (centered) Alignment.Center else Alignment.TopStart,
            ) {
                if (text.isEmpty() && placeholder.isNotEmpty()) {
                    Text(
                        placeholder,
                        style = effectiveStyle,
                        color = MaterialTheme.spiraExtras.mutedForeground,
                        modifier = if (centered) Modifier.fillMaxWidth() else Modifier,
                    )
                }
                inner()
            }
        },
    )
}

/** An editable list row: inline-editable text + a delete button (mirrors the web `InlineList` row). */
@Composable
fun InlineItemRow(text: String, onCommit: (String) -> Unit, onRemove: () -> Unit, required: Boolean = true) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        InlineEditText(
            value = text,
            onCommit = onCommit,
            modifier = Modifier.weight(1f).padding(vertical = 8.dp),
            textStyle = MaterialTheme.typography.bodyMedium,
            required = required,
        )
        IconButton(onClick = onRemove) {
            Icon(SpiraIcons.X, contentDescription = "Remove", tint = MaterialTheme.spiraExtras.mutedForeground)
        }
    }
}

/** The "add a new item" row: type text, press Done or the + button to add, then it clears. */
@Composable
fun AddItemRow(placeholder: String, onAdd: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    fun commit() {
        val t = text.trim()
        if (t.isNotEmpty()) {
            onAdd(t)
            text = ""
        }
    }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(placeholder) },
        singleLine = true,
        shape = MaterialTheme.shapes.small,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { commit() }),
        trailingIcon = {
            IconButton(onClick = { commit() }) { Icon(SpiraIcons.Plus, contentDescription = "Add") }
        },
    )
}

/** A titled list of editable items with an add row (Reality actions/obstacles use this). */
@Composable
fun InlineList(
    items: List<Pair<String, String>>,
    addPlaceholder: String,
    onAdd: (String) -> Unit,
    onUpdate: (id: String, text: String) -> Unit,
    onRemove: (id: String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { (id, text) ->
            InlineItemRow(text = text, onCommit = { onUpdate(id, it) }, onRemove = { onRemove(id) })
        }
        AddItemRow(addPlaceholder, onAdd)
    }
}
