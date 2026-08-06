package com.spiramindscape.android.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import com.spiramindscape.android.data.goals.ResourceItem
import com.spiramindscape.android.ui.icons.SpiraIcons
import com.spiramindscape.android.ui.theme.spiraExtras
import com.spiramindscape.android.ui.util.appendResourceToken
import com.spiramindscape.android.ui.util.InlineSegment
import com.spiramindscape.android.ui.util.isSafeHttpUrl
import com.spiramindscape.android.ui.util.namesToTokens
import com.spiramindscape.android.ui.util.referencesResource
import com.spiramindscape.android.ui.util.resourceDisplayName
import com.spiramindscape.android.ui.util.splitInline
import com.spiramindscape.android.ui.util.tokensToNames

/**
 * Inline resource attachments on Android — the mirror of the web
 * `src/components/spira/inline-resources.tsx` (spec:
 * `specs/2026-07-28-inline-resource-attachments/requirements.md`).
 *
 * Any inline field's text may carry `{{res:<id>}}` tokens. They are stored verbatim as plain text
 * and rendered as **links**: the resource's own type icon, its name underlined in teal, and a
 * diagonal jump-out arrow. Tapping one opens the resource; tapping anywhere else in the field
 * starts editing, where the tokens read as names rather than ids.
 */

/** Goal-scoped services every inline field needs to render and manage attached resources. */
data class InlineResourcesValue(
    val resources: List<ResourceItem>,
    /** Open the resource: a link goes to the site, a note/file opens its own screen. */
    val openResource: (id: String) -> Unit,
)

/** Null outside a goal workspace — inline fields must degrade to plain text there. */
val LocalInlineResources = staticCompositionLocalOf<InlineResourcesValue?> { null }

@Composable
fun ProvideInlineResources(value: InlineResourcesValue, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalInlineResources provides value, content = content)
}

/** Icon + label per resource type — shared by the picker and the inline links. */
internal fun resourceTypeIcon(type: String): ImageVector = when (type) {
    "note" -> SpiraIcons.FileText
    "link" -> SpiraIcons.Link
    "file" -> SpiraIcons.Paperclip
    else -> SpiraIcons.Mail
}

internal fun resourceTypeLabel(type: String): String = when (type) {
    "note" -> "Note"
    "link" -> "Link"
    "file" -> "File"
    else -> "Email"
}

private const val TAG_RESOURCE = "res"
private const val TAG_URL = "url"

/**
 * Inline-editable text that renders attached resources and bare URLs as links.
 *
 * Read mode draws the links; a tap on one opens the resource (or the URL), a tap anywhere else
 * swaps the field for [InlineEditText] with the caret already in it. Editing is plain text, with
 * tokens shown as `{{res:<name>}}` so they read — they map back to ids on commit. Commit happens
 * on blur and on the Done key, never per keystroke, and a [required] field never saves empty.
 */
@Composable
fun InlineRichText(
    value: String,
    onCommit: (String) -> Unit,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    placeholder: String = "",
    required: Boolean = false,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLength: Int? = null,
    /** A done task strikes its own words through — never the resource link inside them. */
    strikeThrough: Boolean = false,
    color: Color = MaterialTheme.colorScheme.onSurface,
    /** Once a target is achieved its links drop from teal to Salt-800: a reference, not a call
     *  to action. */
    linkColor: Color = MaterialTheme.colorScheme.primary,
    maxLines: Int = Int.MAX_VALUE,
    editable: Boolean = true,
    textAlign: TextAlign = TextAlign.Start,
    /** Long-pressing the read view — used by rows that reveal their own kebab that way. Without
     *  it a long press falls through to a plain tap and would open the editor. */
    onLongPress: (() -> Unit)? = null,
    /**
     * Swallow the next tap. For a row whose parent drags on long-press (an Options card): the
     * parent's drag detector sits OUTSIDE this text, so it only sees each pointer event after this
     * tap detector has — releasing the finger at the end of a drag would otherwise register as a
     * tap here and drop the card into edit mode.
     */
    tapSuppressed: () -> Boolean = { false },
) {
    val ctx = LocalInlineResources.current
    val resources = ctx?.resources ?: emptyList()
    var editing by remember { mutableStateOf(false) }

    if (editing) {
        // A guard against the initial unfocused callback closing the editor before it opens.
        var everFocused by remember { mutableStateOf(false) }
        InlineEditText(
            value = tokensToNames(value, resources),
            onCommit = { edited ->
                val stored = namesToTokens(edited, resources)
                // Never write past the server's limit — an over-long value would be rejected and
                // the optimistic update would silently snap back on the next refetch.
                val capped = if (maxLength != null && stored.length > maxLength) {
                    stored.take(maxLength).trimEnd()
                } else {
                    stored
                }
                if (capped != value) onCommit(capped)
            },
            modifier = modifier,
            textStyle = textStyle.merge(TextStyle(color = color)),
            placeholder = placeholder,
            singleLine = singleLine,
            minLines = minLines,
            required = required,
            textAlign = textAlign,
            autoFocus = true,
            onFocusChanged = { focused ->
                if (focused) everFocused = true else if (everFocused) editing = false
            },
        )
        return
    }

    val muted = MaterialTheme.spiraExtras.mutedForeground
    val context = LocalContext.current
    val annotated = remember(value, resources, linkColor, strikeThrough, color) {
        buildInlineText(value, resources, linkColor, strikeThrough, color)
    }
    val inlineContent = rememberInlineIcons(linkColor)
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }

    if (value.isBlank() && placeholder.isNotEmpty()) {
        Text(
            placeholder,
            modifier = modifier.then(
                if (editable) Modifier.clickable { editing = true } else Modifier,
            ),
            style = textStyle,
            color = muted,
            textAlign = textAlign,
        )
        return
    }

    Text(
        text = annotated,
        modifier = modifier.pointerInput(annotated, layout, editable, onLongPress) {
            detectTapGestures(
                onLongPress = onLongPress?.let { press -> { _ -> press() } },
                onTap = { position ->
                    val offset = layout?.getOffsetForPosition(position)
                    val resourceId = offset?.let {
                        annotated.getStringAnnotations(TAG_RESOURCE, it, it).firstOrNull()?.item
                    }
                    val url = offset?.let {
                        annotated.getStringAnnotations(TAG_URL, it, it).firstOrNull()?.item
                    }
                    when {
                        tapSuppressed() -> Unit
                        resourceId != null -> ctx?.openResource(resourceId)
                        url != null -> openHttpUrl(context, url)
                        editable -> editing = true
                    }
                },
            )
        },
        style = textStyle,
        color = color,
        textAlign = textAlign,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        inlineContent = inlineContent,
        onTextLayout = { layout = it },
    )
}

/** Follow a bare URL out to the browser. Only http/https — never javascript:/data:. */
private fun openHttpUrl(context: Context, url: String) {
    if (!isSafeHttpUrl(url)) return
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}

/**
 * The text as a styled, annotated string: plain words in [color], bare URLs underlined in
 * [linkColor], and each attached resource as `[icon] name [arrow]` — the icons are inline-content
 * placeholders resolved by [rememberInlineIcons].
 */
private fun buildInlineText(
    value: String,
    resources: List<ResourceItem>,
    linkColor: Color,
    strikeThrough: Boolean,
    color: Color,
): AnnotatedString = buildAnnotatedString {
    val plain = SpanStyle(
        color = color,
        textDecoration = if (strikeThrough) TextDecoration.LineThrough else null,
    )
    val link = SpanStyle(
        color = linkColor,
        fontWeight = FontWeight.Medium,
        textDecoration = TextDecoration.Underline,
    )

    for (segment in splitInline(value)) {
        when (segment) {
            is InlineSegment.Text -> withStyle(plain) { append(segment.value) }
            is InlineSegment.Url -> {
                pushStringAnnotation(TAG_URL, segment.url)
                withStyle(link) { append(segment.url) }
                pop()
            }
            is InlineSegment.Resource -> {
                val resource = resources.firstOrNull { it.id == segment.id }
                if (resource == null) {
                    // The reference outlived its resource (deleted on another device). Show a
                    // neutral placeholder rather than the raw token, and never a broken link.
                    withStyle(plain.copy(fontStyle = FontStyle.Italic)) { append("unavailable") }
                    continue
                }
                pushStringAnnotation(TAG_RESOURCE, resource.id)
                appendInlineContent(iconIdFor(resource.type), " ")
                withStyle(link) { append(resourceDisplayName(resource)) }
                appendInlineContent(ICON_ARROW, " ")
                pop()
            }
        }
    }
}

private const val ICON_ARROW = "inline-arrow"
private fun iconIdFor(type: String) = "inline-$type"

/** The inline icon slots referenced by [buildInlineText], sized to the surrounding text. */
@Composable
private fun rememberInlineIcons(tint: Color): Map<String, InlineTextContent> {
    val slot = Placeholder(
        width = TextUnit(1.15f, TextUnitType.Em),
        height = TextUnit(1.15f, TextUnitType.Em),
        placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
    )
    val types = listOf("note", "link", "file", "email")
    return remember(tint) {
        buildMap {
            types.forEach { type ->
                put(
                    iconIdFor(type),
                    InlineTextContent(slot) {
                        Icon(
                            resourceTypeIcon(type),
                            contentDescription = null,
                            tint = tint,
                            modifier = Modifier.fillMaxSize(),
                        )
                    },
                )
            }
            put(
                ICON_ARROW,
                InlineTextContent(slot) {
                    Icon(
                        SpiraIcons.ArrowUpRight,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.fillMaxSize(),
                    )
                },
            )
        }
    }
}

/**
 * The per-element ⋯ menu: attach a resource to this element's text, and (where the element can be
 * removed) delete it. The Android twin of the web `ElementActionsMenu`. [vertical] picks ⋮ over ⋯ —
 * a row with a fixed control column (a checklist task) reads better vertical.
 */
@Composable
fun ElementActionsMenu(
    contentDescription: String,
    onAttach: (resourceId: String) -> Unit,
    modifier: Modifier = Modifier,
    onDelete: (() -> Unit)? = null,
    deleteLabel: String = "Delete",
    /** The element's current text — resources it already references are left out of the picker. */
    attachedTo: String? = null,
    vertical: Boolean = false,
    tint: Color? = null,
) {
    val ctx = LocalInlineResources.current
    var expanded by remember { mutableStateOf(false) }
    var pickerOpen by remember { mutableStateOf(false) }

    Box(modifier) {
        Icon(
            if (vertical) SpiraIcons.MoreVertical else SpiraIcons.MoreHorizontal,
            contentDescription = contentDescription,
            tint = tint ?: MaterialTheme.spiraExtras.mutedForeground,
            modifier = Modifier
                .size(24.dp)
                .clickable { expanded = true }
                .padding(3.dp),
        )
        SpiraDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (ctx != null) {
                SpiraMenuItem(
                    label = "Attach resource",
                    onClick = { expanded = false; pickerOpen = true },
                    icon = SpiraIcons.Paperclip,
                )
            }
            if (ctx != null && onDelete != null) SpiraMenuDivider()
            if (onDelete != null) {
                SpiraMenuItem(
                    label = deleteLabel,
                    onClick = { expanded = false; onDelete() },
                    icon = SpiraIcons.Trash,
                    destructive = true,
                )
            }
        }
    }

    if (pickerOpen) {
        ResourcePickerSheet(
            attachedTo = attachedTo,
            onDismiss = { pickerOpen = false },
            onPick = { pickerOpen = false; onAttach(it) },
        )
    }
}

/**
 * "Attach resource" as a plain teal link — for places that spell their actions out rather than
 * hiding them behind a ⋯ menu (the target card). Renders nothing outside a goal workspace, where
 * there is no resource list to pick from.
 */
@Composable
fun AttachResourceButton(
    onAttach: (resourceId: String) -> Unit,
    modifier: Modifier = Modifier,
    attachedTo: String? = null,
) {
    LocalInlineResources.current ?: return
    var pickerOpen by remember { mutableStateOf(false) }

    Row(
        modifier.clickable { pickerOpen = true }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            SpiraIcons.CirclePlus,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Text(
            "Attach resource",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
    }

    if (pickerOpen) {
        ResourcePickerSheet(
            attachedTo = attachedTo,
            onDismiss = { pickerOpen = false },
            onPick = { pickerOpen = false; onAttach(it) },
        )
    }
}

/**
 * Picks one of the goal's resources to attach; the caller appends the token to its own text.
 * Resources already referenced by [attachedTo] are hidden, so the same resource can't be attached
 * to the same place twice.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResourcePickerSheet(
    onDismiss: () -> Unit,
    onPick: (resourceId: String) -> Unit,
    attachedTo: String? = null,
) {
    val ctx = LocalInlineResources.current ?: return
    val all = ctx.resources
    val attachable = if (attachedTo == null) all else all.filterNot { referencesResource(attachedTo, it.id) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.spiraExtras.surfaceRaised,
    ) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Text("Attach a resource", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(10.dp))
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    SpiraIcons.Info,
                    contentDescription = null,
                    tint = MaterialTheme.spiraExtras.mutedForeground,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    "Its name is added at the end of the text as a link — tap it to open the resource.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.spiraExtras.mutedForeground,
                )
            }
            Spacer(Modifier.height(18.dp))

            if (attachable.isEmpty()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.spiraExtras.border, RoundedCornerShape(10.dp))
                        .padding(horizontal = 16.dp, vertical = 20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (all.isNotEmpty()) {
                            "Every resource on this goal is already attached here."
                        } else {
                            "No resources on this goal yet — add one in the Resources section first."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.spiraExtras.mutedForeground,
                    )
                }
            } else {
                Column(
                    Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    attachable.forEach { resource ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .border(1.dp, MaterialTheme.spiraExtras.border, RoundedCornerShape(10.dp))
                                .clickable { onPick(resource.id) }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(
                                resourceTypeIcon(resource.type),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                resourceDisplayName(resource),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                resourceTypeLabel(resource.type),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.spiraExtras.mutedForeground,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Attach [resourceId] to [text], respecting the field's [maxLength]. Returns null when there is no
 * room — the token must never push a value past the server's limit.
 */
fun attachTo(text: String, resourceId: String, maxLength: Int): String? =
    appendResourceToken(text, resourceId, maxLength)
