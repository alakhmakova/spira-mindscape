package com.spiramindscape.android.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    /**
     * Reports whether the read view is clipped by [maxLines] — what a "Show more" toggle needs to
     * know before it can decide to exist. Only meaningful while [maxLines] actually clamps: once
     * the caller expands the text this reports false again, so callers latch the first `true`.
     */
    onOverflowChange: ((Boolean) -> Unit)? = null,
    /**
     * Reports when this field swaps between its read view and the editor. Rows that reveal a
     * control only while their text is being edited (the Options card's ⋯ menu) watch this.
     */
    onEditingChange: ((Boolean) -> Unit)? = null,
) {
    val ctx = LocalInlineResources.current
    val resources = ctx?.resources ?: emptyList()
    var editing by remember { mutableStateOf(false) }
    LaunchedEffect(editing) { onEditingChange?.invoke(editing) }

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
        onTextLayout = {
            layout = it
            onOverflowChange?.invoke(it.hasVisualOverflow)
        },
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
                // A link needs no leading glyph — the trailing open arrow after the name already
                // says it opens out (owner, 2026-08-17). Other kinds keep their type icon.
                if (resource.type != "link") appendInlineContent(iconIdFor(resource.type), " ")
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
                    // The **same mark the Resources page puts on "Open link"** — an arrow leaving a
                    // square, not a bare arrow. A plain arrow says "up and to the right"; this one
                    // says "this opens somewhere else", which is what tapping the chip does.
                    Icon(
                        SpiraIcons.ExternalLink,
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
 *
 * Both are the owner's *Kebab* (`specs/icons.md`); the collection draws it vertical, and the
 * horizontal default is that same glyph rotated a quarter-turn, not a second icon.
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
    /**
     * Reports whether the dropdown (or its resource picker) is open. A caller that only *shows*
     * this menu in some state — the Options card reveals it while its text is being edited — must
     * keep showing it while it is open, or the composable holding the dropdown would be removed
     * out from under the user's finger.
     */
    onOpenChange: ((Boolean) -> Unit)? = null,
) {
    val ctx = LocalInlineResources.current
    var expanded by remember { mutableStateOf(false) }
    var pickerOpen by remember { mutableStateOf(false) }
    LaunchedEffect(expanded, pickerOpen) { onOpenChange?.invoke(expanded || pickerOpen) }

    Box(modifier) {
        Icon(
            if (vertical) SpiraIcons.EllipsisVertical else SpiraIcons.Ellipsis,
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
 * hiding them behind a ⋯ menu (the target card, the create form). Renders nothing outside a goal
 * workspace, where there is no resource list to pick from.
 *
 * [iconOnly] is the same button reduced to its paperclip, for a row that is already a compact
 * strip carrying its own remove control — a task inside the create form. The web twin takes the
 * same choice as `variant="icon"` (`inline-resources.tsx`).
 */
@Composable
fun AttachResourceButton(
    onAttach: (resourceId: String) -> Unit,
    modifier: Modifier = Modifier,
    attachedTo: String? = null,
    iconOnly: Boolean = false,
    /** Names the control when the paperclip stands alone and there is no word to read. */
    contentDescription: String = "Attach resource",
) {
    LocalInlineResources.current ?: return
    var pickerOpen by remember { mutableStateOf(false) }

    if (iconOnly) {
        Icon(
            SpiraIcons.Paperclip,
            contentDescription = contentDescription,
            tint = MaterialTheme.spiraExtras.mutedForeground,
            modifier = modifier
                .size(24.dp)
                .clickable { pickerOpen = true }
                .padding(4.dp),
        )
    } else {
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
                // Trimmed leading, so the word sits on the plus's centre instead of riding above
                // it — the line box reserves descender room this label never uses. See
                // addActionTextStyle.
                style = addActionTextStyle(),
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
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
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.spiraExtras.surfaceRaised,
        // The head is the sheet's own band, so a drag handle would sit on top of teal as a grey
        // smudge. The X in the head closes it, plus the usual drag and back gesture.
        dragHandle = null,
    ) {
        ResourcePickerSheetContent(onDismiss, onPick, attachedTo)
    }
}

/**
 * The picker's card, without the [ModalBottomSheet] around it.
 *
 * Separate for the same reason `SpiraFilterSheetContent` is: a modal sheet renders in its **own
 * window**, which the `VisualCheck*` screenshot helper (it draws the activity's decor view) cannot
 * capture — so an open picker is simply absent from the PNG, and the check that should catch a
 * search field crowding the head silently checks nothing.
 */
@Composable
fun ResourcePickerSheetContent(
    onDismiss: () -> Unit,
    onPick: (resourceId: String) -> Unit,
    attachedTo: String? = null,
) {
    val ctx = LocalInlineResources.current ?: return
    val all = ctx.resources
    val attachable = if (attachedTo == null) all else all.filterNot { referencesResource(attachedTo, it.id) }
    // A goal accumulates resources faster than anything else on it, and this list has no sort and
    // no filter — so on a real goal the one you want is somewhere below the fold (owner,
    // 2026-08-18). It starts empty on every visit, like every other search in the app.
    var query by remember { mutableStateOf("") }
    val shown = attachable.filter { matchesResource(it, query) }

    Column {
        // **A Kale head** (owner, 2026-08-17) — white type on teal, the same band the app header
        // carries, so the sheet reads as part of the app rather than a white box over it. The web
        // picker is the same sheet with the same head now (`inline-resources.tsx`).
        Row(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primary)
                .padding(start = 20.dp, end = 12.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Attach a resource",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.weight(1f),
            )
            Icon(
                SpiraIcons.X,
                contentDescription = "Close",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onDismiss)
                    .padding(6.dp)
                    .size(18.dp),
            )
        }
        Column(Modifier.padding(horizontal = 20.dp).padding(top = 16.dp, bottom = 28.dp)) {
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
            Spacer(Modifier.height(14.dp))

            // Offered from two resources up: below that the field is a control to read past on the
            // way to a list you can already see whole.
            if (attachable.size > 1) {
                SpiraSearchField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = "Search resources",
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
            }

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
            } else if (shown.isEmpty()) {
                // The list is not empty — the search emptied it, which is a warning rather than an
                // empty state (CLAUDE.md § "Notices").
                SpiraNoticeCard(
                    message = "No resources match that search.",
                    kind = SpiraNoticeKind.Warning,
                )
            } else {
                Column(
                    Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    shown.forEach { resource ->
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
 * Does [resource] match [query]?
 *
 * The fields a person actually remembers a resource by: the name it is shown under, its title, a
 * link's address, and a contact's name, address and role. **Not** a note's body — the picker shows
 * only names, so matching on hidden text would offer rows with nothing in them to explain why.
 */
private fun matchesResource(resource: ResourceItem, query: String): Boolean {
    val q = query.trim()
    if (q.isEmpty()) return true
    return listOf(
        resourceDisplayName(resource),
        resource.title,
        resource.url,
        resource.name,
        resource.email,
        resource.role,
    ).any { it != null && it.contains(q, ignoreCase = true) }
}

/**
 * Attach [resourceId] to [text], respecting the field's [maxLength]. Returns null when there is no
 * room — the token must never push a value past the server's limit.
 */
fun attachTo(text: String, resourceId: String, maxLength: Int): String? =
    appendResourceToken(text, resourceId, maxLength)
