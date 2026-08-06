package com.spiramindscape.android.ui.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spiramindscape.android.ui.components.InlineEditText
import com.spiramindscape.android.ui.icons.SpiraIcons
import com.spiramindscape.android.ui.theme.spiraExtras

/** The providers the backend accepts, in the order the web lists them. */
private val PROVIDERS = listOf("ANTHROPIC", "OPENAI", "MISTRAL", "GEMINI")

/**
 * Provider and API-key management — the Android twin of the web `ProviderSheet`.
 *
 * The key is typed once and sent straight to the server, which encrypts it; the app never keeps a
 * copy and the server only ever hands back a hint (the last few characters). Picking a provider
 * saves the choice so it follows the user to their other devices.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderSheet(viewModel: AiChatViewModel, onDismiss: () -> Unit) {
    val keys by viewModel.keys.collectAsStateWithLifecycle()
    val active by viewModel.provider.collectAsStateWithLifecycle()

    var selected by remember { mutableStateOf(active) }
    var keyDraft by remember { mutableStateOf("") }
    var models by remember { mutableStateOf<List<String>>(emptyList()) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    val saved = keys.firstOrNull { it.provider.equals(selected, ignoreCase = true) }

    // Models can only be listed once a key exists — the call authenticates with it.
    LaunchedEffect(selected, saved?.hint) {
        models = emptyList()
        if (saved != null) viewModel.loadModels(selected) { list, error -> models = list; if (error != null) message = error }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.spiraExtras.surfaceRaised,
    ) {
        Column(
            Modifier
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
                .heightIn(max = 560.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text("Assistant provider", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(6.dp))
            Text(
                "Your key is encrypted on the server and never leaves it. Spira only ever sees a hint.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.spiraExtras.mutedForeground,
            )

            Spacer(Modifier.height(16.dp))
            PROVIDERS.forEach { provider ->
                val hasKey = keys.any { it.provider.equals(provider, ignoreCase = true) }
                ProviderRow(
                    label = providerLabel(provider),
                    hint = keys.firstOrNull { it.provider.equals(provider, ignoreCase = true) }?.hint,
                    selected = provider == selected,
                    active = provider == active,
                    hasKey = hasKey,
                    onClick = { selected = provider; message = null },
                )
            }

            Spacer(Modifier.height(18.dp))
            Text(
                if (saved == null) "Add a ${providerLabel(selected)} key" else "Replace the ${providerLabel(selected)} key",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, MaterialTheme.spiraExtras.border, RoundedCornerShape(10.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                InlineEditText(
                    value = keyDraft,
                    onCommit = { keyDraft = it },
                    onTextChanged = { keyDraft = it },
                    placeholder = "Paste the API key",
                    textStyle = MaterialTheme.typography.bodySmall,
                    keyboardType = KeyboardType.Password,
                )
            }

            if (models.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text("Model", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    models.take(12).forEach { model ->
                        ModelRow(
                            name = model,
                            selected = model == saved?.model,
                            onClick = { viewModel.chooseModel(selected, model) },
                        )
                    }
                }
            }

            message?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SheetButton(
                    label = if (keyDraft.isBlank()) "Use ${providerLabel(selected)}" else "Save key",
                    primary = true,
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                ) {
                    if (keyDraft.isBlank()) {
                        viewModel.chooseProvider(selected)
                        onDismiss()
                    } else {
                        busy = true
                        viewModel.saveKey(selected, keyDraft.trim(), null) { error ->
                            busy = false
                            if (error == null) {
                                keyDraft = ""
                                onDismiss()
                            } else {
                                message = error
                            }
                        }
                    }
                }
                SheetButton("Cancel", modifier = Modifier.weight(1f), enabled = !busy, onClick = onDismiss)
            }
        }
    }
}

@Composable
private fun ProviderRow(
    label: String,
    hint: String?,
    selected: Boolean,
    active: Boolean,
    hasKey: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(10.dp))
            .border(
                if (selected) 2.dp else 1.dp,
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.spiraExtras.border,
                RoundedCornerShape(10.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(
                when {
                    hint != null -> "Key ending $hint"
                    hasKey -> "Key saved"
                    else -> "No key yet"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.spiraExtras.mutedForeground,
            )
        }
        if (active) {
            Text(
                "In use",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun ModelRow(name: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            name,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(
                SpiraIcons.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun SheetButton(
    label: String,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .height(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .then(
                if (primary) {
                    Modifier.background(
                        if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.spiraExtras.surfaceSunken,
                    )
                } else {
                    Modifier.border(1.dp, MaterialTheme.spiraExtras.border, RoundedCornerShape(10.dp))
                },
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (primary && enabled) Color.White else MaterialTheme.colorScheme.onSurface,
        )
    }
}
