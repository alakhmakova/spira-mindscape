package com.spiramindscape.android.ui.ai

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spiramindscape.android.data.ai.AiApi
import com.spiramindscape.android.ui.components.InlineEditText
import com.spiramindscape.android.ui.components.SpiraBadge
import com.spiramindscape.android.ui.components.SpiraBadgeTone
import com.spiramindscape.android.ui.icons.SpiraIcons
import com.spiramindscape.android.ui.theme.Kale100
import com.spiramindscape.android.ui.theme.Kale500
import com.spiramindscape.android.ui.theme.Salt1000
import com.spiramindscape.android.ui.theme.spiraExtras

/**
 * "AI providers" — the Android port of the web `ProviderSheet` in `AiPanel.tsx`, card for card.
 *
 * One card per provider: its vendor and context window, an Active pill or a "Use this" button, a
 * model dropdown that loads the live list from the provider on first open, the stored key's hint
 * with "Replace key", and an inline key form with Show/Hide. Below them, Tavily as a separate
 * web-search key — it is a key slot, never a chat provider.
 *
 * Keys are typed once and sent straight to the server, which encrypts them; the app only ever sees
 * the hint the server hands back.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderSheet(viewModel: AiChatViewModel, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.spiraExtras.surfaceRaised,
    ) {
        ProviderSheetContent(viewModel, onDismiss)
    }
}

/**
 * The sheet's body, separate from the `ModalBottomSheet` that carries it — a modal sheet renders
 * in its own window, which a decorView screenshot can't capture, so the visual check renders this
 * directly (CLAUDE.md → "Verify UI changes visually").
 */
@Composable
internal fun ProviderSheetContent(viewModel: AiChatViewModel, onDismiss: () -> Unit) {
    val keys by viewModel.keys.collectAsStateWithLifecycle()
    val activeId by viewModel.provider.collectAsStateWithLifecycle()

    // Which card has its key form open, which has its dropdown open, and the models fetched so far.
    var editing by remember { mutableStateOf<String?>(null) }
    var openDropdown by remember { mutableStateOf<String?>(null) }
    var modelLists by remember { mutableStateOf<Map<String, List<String>>>(emptyMap()) }
    var loadingModels by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    fun loadModels(id: String) {
        if (modelLists.containsKey(id) || loadingModels == id) return
        loadingModels = id
        viewModel.loadModels(id) { list, error ->
            loadingModels = null
            if (error == null) modelLists = modelLists + (id to list) else message = error
        }
    }

    Column(
        Modifier
            .background(MaterialTheme.spiraExtras.surfaceRaised)
            .padding(horizontal = 20.dp)
            .padding(bottom = 20.dp)
            .imePadding()
            .heightIn(max = 620.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        // ── head ────────────────────────────────────────────────────────
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Kicker("Bring your own key")
                Spacer(Modifier.height(6.dp))
                Text(
                    "AI providers",
                    // The serif, as on the web (`font-['Playfair_Display']`).
                    style = MaterialTheme.typography.headlineSmall,
                    fontSize = 22.sp,
                    lineHeight = 26.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Salt1000,
                )
            }
            Box(
                Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    SpiraIcons.X,
                    contentDescription = "Close",
                    tint = Salt1000.copy(alpha = 0.5f),
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Keys are stored encrypted on your account. Keep several connected and switch anytime.",
            style = MaterialTheme.typography.bodyMedium,
            fontSize = 13.5.sp,
            lineHeight = 20.sp,
            color = Salt1000.copy(alpha = 0.6f),
        )
        Spacer(Modifier.height(16.dp))

        // ── one card per chat provider ──────────────────────────────────
        CHAT_PROVIDERS.forEach { info ->
            val saved = keys.firstOrNull { it.provider.equals(info.id, ignoreCase = true) }
            ProviderCard(
                info = info,
                saved = saved,
                isActive = info.id.equals(activeId, ignoreCase = true) && saved != null,
                editing = editing == info.id,
                dropdownOpen = openDropdown == info.id,
                loadingModels = loadingModels == info.id,
                models = modelLists[info.id] ?: info.models,
                onActivate = { viewModel.chooseProvider(info.id) },
                onToggleDropdown = {
                    if (openDropdown == info.id) {
                        openDropdown = null
                    } else {
                        openDropdown = info.id
                        loadModels(info.id)
                    }
                },
                onPickModel = { model ->
                    openDropdown = null
                    viewModel.chooseModel(info.id, model)
                },
                onStartEditing = { editing = info.id; openDropdown = null; message = null },
                onCancelEditing = { editing = null },
                onSaveKey = { key ->
                    editing = null
                    viewModel.saveKey(info.id, key, null) { error -> message = error }
                },
            )
            Spacer(Modifier.height(12.dp))
        }

        // ── web search ──────────────────────────────────────────────────
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = MaterialTheme.spiraExtras.border)
        Spacer(Modifier.height(16.dp))
        Kicker("Web search")
        Spacer(Modifier.height(4.dp))
        Text(
            "Add a Tavily key (tavily.com) to let the assistant search the web. Optional.",
            style = MaterialTheme.typography.bodyMedium,
            fontSize = 12.5.sp,
            lineHeight = 19.sp,
            color = Salt1000.copy(alpha = 0.6f),
        )
        Spacer(Modifier.height(10.dp))
        ProviderCard(
            info = TAVILY,
            saved = keys.firstOrNull { it.provider.equals(TAVILY.id, ignoreCase = true) },
            // Tavily is a key slot, not something the chat can be switched to, so it never
            // shows Active / Use this — only Connected.
            isActive = false,
            connectedOnly = true,
            editing = editing == TAVILY.id,
            dropdownOpen = false,
            loadingModels = false,
            models = emptyList(),
            onActivate = {},
            onToggleDropdown = {},
            onPickModel = {},
            onStartEditing = { editing = TAVILY.id; message = null },
            onCancelEditing = { editing = null },
            onSaveKey = { key ->
                editing = null
                viewModel.saveKey(TAVILY.id, key, null) { error -> message = error }
            },
        )

        message?.let {
            Spacer(Modifier.height(12.dp))
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                SpiraIcons.Shield,
                contentDescription = null,
                tint = Salt1000.copy(alpha = 0.4f),
                modifier = Modifier.size(12.dp),
            )
            Spacer(Modifier.size(6.dp))
            Text(
                "Keys never leave your account and are encrypted at rest.",
                style = MaterialTheme.typography.labelMedium,
                color = Salt1000.copy(alpha = 0.4f),
            )
        }
    }
}

/** The small teal all-caps label the web uses above each block. */
@Composable
private fun Kicker(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(SpiraIcons.NavKey, contentDescription = null, tint = Kale500, modifier = Modifier.size(14.dp))
        Spacer(Modifier.size(6.dp))
        Text(
            text.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
            color = Kale500,
        )
    }
}

@Composable
private fun ProviderCard(
    info: ProviderInfo,
    saved: AiApi.KeyInfo?,
    isActive: Boolean,
    editing: Boolean,
    dropdownOpen: Boolean,
    loadingModels: Boolean,
    models: List<String>,
    onActivate: () -> Unit,
    onToggleDropdown: () -> Unit,
    onPickModel: (String) -> Unit,
    onStartEditing: () -> Unit,
    onCancelEditing: () -> Unit,
    onSaveKey: (String) -> Unit,
    connectedOnly: Boolean = false,
) {
    val connected = saved != null
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isActive) Kale100 else Color.White)
            .border(
                1.dp,
                if (isActive) Kale500 else MaterialTheme.spiraExtras.border,
                RoundedCornerShape(12.dp),
            )
            .padding(14.dp),
    ) {
        // ── header row: vendor + context, then the state ───────────────────
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom) {
                Text(
                    info.vendor,
                    style = MaterialTheme.typography.bodyLarge,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Salt1000,
                )
                if (info.context.isNotEmpty()) {
                    Spacer(Modifier.size(8.dp))
                    Text(
                        info.context,
                        style = MaterialTheme.typography.labelMedium,
                        color = Salt1000.copy(alpha = 0.5f),
                    )
                }
            }
            when {
                isActive || (connectedOnly && connected) -> StatePill(
                    if (connectedOnly) "Connected" else "Active",
                )
                connected -> Row(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, MaterialTheme.spiraExtras.border, RoundedCornerShape(8.dp))
                        .clickable(onClick = onActivate)
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        SpiraIcons.SwitchArrows,
                        contentDescription = null,
                        tint = Salt1000,
                        modifier = Modifier.size(12.dp),
                    )
                    Spacer(Modifier.size(5.dp))
                    Text(
                        "Use this",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = Salt1000,
                    )
                }
                else -> Text(
                    "Not connected",
                    style = MaterialTheme.typography.labelMedium,
                    color = Salt1000.copy(alpha = 0.4f),
                )
            }
        }

        // ── model selector (chat providers only, once a key exists) ────────
        if (connected && !editing && !connectedOnly) {
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White)
                    .border(1.dp, MaterialTheme.spiraExtras.border, RoundedCornerShape(8.dp))
                    .clickable(onClick = onToggleDropdown)
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    saved?.model ?: info.defaultModel.ifEmpty { "Select model" },
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = Salt1000,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    SpiraIcons.ChevronDown,
                    contentDescription = null,
                    tint = Salt1000.copy(alpha = 0.5f),
                    modifier = Modifier.size(14.dp).rotate(if (dropdownOpen) 180f else 0f),
                )
            }

            AnimatedVisibility(visible = dropdownOpen) {
                Column(
                    Modifier
                        .padding(top = 4.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White)
                        .border(1.dp, MaterialTheme.spiraExtras.border, RoundedCornerShape(12.dp)),
                ) {
                    when {
                        loadingModels -> DropdownNote("Loading models…")
                        models.isEmpty() -> DropdownNote("No models found")
                        else -> Column(
                            Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState()),
                        ) {
                            models.forEach { model ->
                                val picked = model == saved?.model
                                Text(
                                    model,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = if (picked) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (picked) Kale500 else Salt1000,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(if (picked) Kale100 else Color.Transparent)
                                        .clickable { onPickModel(model) }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── the stored key's hint, and how to replace it ───────────────────
        if (connected && !editing) {
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    SpiraIcons.Shield,
                    contentDescription = null,
                    tint = Salt1000.copy(alpha = 0.5f),
                    modifier = Modifier.size(12.dp),
                )
                Spacer(Modifier.size(6.dp))
                Text(
                    saved?.hint ?: "",
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                    color = Salt1000.copy(alpha = 0.5f),
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "Replace key",
                    style = MaterialTheme.typography.labelMedium,
                    color = Kale500,
                    modifier = Modifier.clickable(onClick = onStartEditing),
                )
            }
        }

        if (!connected && !editing) {
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.clickable(onClick = onStartEditing),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(SpiraIcons.Plus, contentDescription = null, tint = Kale500, modifier = Modifier.size(14.dp))
                Spacer(Modifier.size(6.dp))
                Text(
                    "Connect a key",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = Kale500,
                )
            }
        }

        if (editing) {
            Spacer(Modifier.height(12.dp))
            KeyForm(
                placeholder = if (info.keyPrefix.isNotEmpty()) "${info.keyPrefix}…" else "API key",
                onSave = onSaveKey,
                onCancel = onCancelEditing,
            )
        }
    }
}

@Composable
private fun StatePill(label: String) {
    // Word only, no tick: the badge already reads as a state, and the check was saying "yes"
    // twice. Same shape as every other status badge in the app — see `SpiraBadge`.
    //
    // Green, not the brand teal: these say "this one is working", and in teal they were the same
    // colour as the outline of the card they sat on, so the badge disappeared into its own frame.
    SpiraBadge(label, SpiraBadgeTone.Success)
}

@Composable
private fun DropdownNote(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = Salt1000.copy(alpha = 0.5f),
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
    )
}

/** The inline key form: a focused teal field with Show/Hide, then Save & activate / Cancel. */
@Composable
private fun KeyForm(placeholder: String, onSave: (String) -> Unit, onCancel: () -> Unit) {
    var value by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White)
                .border(2.dp, Kale500, RoundedCornerShape(12.dp))
                .padding(start = 12.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            InlineEditText(
                value = value,
                onCommit = { value = it },
                onTextChanged = { value = it },
                placeholder = placeholder,
                textStyle = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.5.sp,
                    color = Salt1000,
                ),
                // The key is masked until the user asks to see it — it is pasted far more often
                // than it is read, and a visible key on screen is a shoulder-surfing hazard.
                keyboardType = if (visible) KeyboardType.Text else KeyboardType.Password,
                autoFocus = true,
                modifier = Modifier.weight(1f).padding(vertical = 8.dp),
            )
            Box(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Salt1000.copy(alpha = 0.05f))
                    .clickable { visible = !visible }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(
                    if (visible) "Hide" else "Show",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Salt1000.copy(alpha = 0.6f),
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            val canSave = value.isNotBlank()
            Row(
                Modifier
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (canSave) Kale500 else Kale500.copy(alpha = 0.4f))
                    .clickable(enabled = canSave) { onSave(value.trim()) }
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(SpiraIcons.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                Spacer(Modifier.size(6.dp))
                Text(
                    "Save & activate",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                )
            }
            Spacer(Modifier.size(8.dp))
            Text(
                "Cancel",
                style = MaterialTheme.typography.bodySmall,
                color = Salt1000.copy(alpha = 0.5f),
                modifier = Modifier.clickable(onClick = onCancel).padding(horizontal = 8.dp, vertical = 8.dp),
            )
        }
    }
}

/**
 * What a provider card shows before the server has been asked anything: its name, its context
 * window, the shape of its key, and the models to offer if the live list can't be fetched. Mirrors
 * the web `PROVIDERS_DEFAULT`.
 */
internal data class ProviderInfo(
    val id: String,
    val vendor: String,
    val context: String,
    val keyPrefix: String,
    val defaultModel: String,
    val models: List<String>,
)

internal val CHAT_PROVIDERS = listOf(
    ProviderInfo(
        id = "ANTHROPIC",
        vendor = "Anthropic",
        context = "200 000 tokens",
        keyPrefix = "sk-ant-",
        defaultModel = "claude-sonnet-4-6",
        models = listOf("claude-sonnet-4-6", "claude-opus-4-8", "claude-haiku-4-5-20251001"),
    ),
    ProviderInfo(
        id = "OPENAI",
        vendor = "OpenAI",
        context = "128 000 tokens",
        keyPrefix = "sk-",
        defaultModel = "gpt-4o",
        models = listOf("gpt-4o", "gpt-4o-mini", "o3", "o4-mini"),
    ),
    ProviderInfo(
        id = "MISTRAL",
        vendor = "Mistral",
        context = "128 000 tokens",
        keyPrefix = "",
        defaultModel = "mistral-large-latest",
        models = listOf(
            "mistral-large-latest",
            "mistral-small-latest",
            "codestral-latest",
            "open-mixtral-8x7b",
            "open-mistral-7b",
        ),
    ),
    ProviderInfo(
        id = "GEMINI",
        vendor = "Google Gemini",
        context = "1 000 000 tokens",
        keyPrefix = "AIza",
        defaultModel = "gemini-2.5-flash",
        models = listOf("gemini-2.5-flash", "gemini-2.5-pro", "gemini-2.0-flash", "gemini-1.5-pro"),
    ),
)

/** The web-search key. Stored in the same table as the chat keys, but never a chat provider. */
internal val TAVILY = ProviderInfo(
    id = "TAVILY",
    vendor = "Tavily",
    context = "",
    keyPrefix = "tvly-",
    defaultModel = "",
    models = emptyList(),
)
