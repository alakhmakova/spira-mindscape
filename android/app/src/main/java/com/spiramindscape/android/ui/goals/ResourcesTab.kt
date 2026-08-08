package com.spiramindscape.android.ui.goals

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spiramindscape.android.data.goals.GoalDetail
import com.spiramindscape.android.data.goals.ResourceItem
import com.spiramindscape.android.ui.components.InlineEditText
import com.spiramindscape.android.ui.icons.SpiraIcons
import com.spiramindscape.android.ui.theme.Guava100
import com.spiramindscape.android.ui.theme.Guava200
import com.spiramindscape.android.ui.theme.Guava600
import com.spiramindscape.android.ui.theme.Kale100
import com.spiramindscape.android.ui.theme.Kale300
import com.spiramindscape.android.ui.theme.Kale600
import com.spiramindscape.android.ui.theme.Kale500
import com.spiramindscape.android.ui.theme.Parsnip100
import com.spiramindscape.android.ui.theme.Parsnip200
import com.spiramindscape.android.ui.theme.Salt400
import com.spiramindscape.android.ui.theme.spiraExtras

/** Normalise the stored type to one of the four card kinds the UI renders. */
private fun resourceKind(type: String): String = when (type) {
    "img", "image" -> "file"
    else -> type
}

private fun typeIcon(kind: String): ImageVector = when (kind) {
    "note" -> SpiraIcons.FileText
    "link" -> SpiraIcons.Link
    "email" -> SpiraIcons.Mail
    else -> SpiraIcons.File
}

private fun typeLabel(kind: String): String = when (kind) {
    "note" -> "Note"
    "link" -> "Link"
    "email" -> "Contact"
    else -> "File"
}

/** Update one field of a resource, preserving the rest (the mutation takes the whole resource). */
internal fun commitResource(
    actions: GoalWorkspaceActions,
    res: ResourceItem,
    title: String? = res.title,
    body: String? = res.body,
    url: String? = res.url,
    name: String? = res.name,
    email: String? = res.email,
    role: String? = res.role,
    phone: String? = res.phone,
    mime: String? = res.mime,
    dataUrl: String? = res.dataUrl,
) = actions.onUpdateResource(res.id, title, body, url, name, email, role, phone, mime, dataUrl)

/**
 * The Resources tab (design mockup): a white page with a title/description head and a list of
 * collapsible cards. Each card shows a type icon, its name and type; tapping expands it into a
 * type-specific editor (rich-text note, link/contact fields, file preview). Notes and files can be
 * opened full screen via [onOpenFull]. Creating happens via the guava "+" FAB → [NewResourceSheet].
 */
@Composable
fun ResourcesTabContent(
    goal: GoalDetail,
    actions: GoalWorkspaceActions,
    onOpenFull: (String) -> Unit = {},
) {
    var openId by remember { mutableStateOf<String?>(null) }

    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
    ) {
        Column(
            // 28dp of air under the tab row and the shared heading size — the same block every
            // phase screen opens with, so nothing shifts as the user moves between them.
            Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Resources",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Everything that supports this goal in one place — notes, files, links and people you can return to as you go.",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 22.sp),
                color = MaterialTheme.spiraExtras.mutedForeground,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }

        if (goal.resources.isEmpty()) {
            Text(
                "No resources yet — tap the plus button to add a note, link, file or contact.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.spiraExtras.mutedForeground,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }

        goal.resources.forEach { res ->
            ResourceCard(
                res = res,
                expanded = openId == res.id,
                onToggle = { openId = if (openId == res.id) null else res.id },
                actions = actions,
                onOpenFull = onOpenFull,
            )
        }
    }
}

@Composable
private fun ResourceCard(
    res: ResourceItem,
    expanded: Boolean,
    onToggle: () -> Unit,
    actions: GoalWorkspaceActions,
    onOpenFull: (String) -> Unit,
) {
    val kind = resourceKind(res.type)
    val chevronRotation by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")
    val cardBg = if (expanded) MaterialTheme.spiraExtras.surfaceRaised else Parsnip200
    // Soft hairline: Salt-400 collapsed, a light teal (Kale-300) when open — matches the mockup.
    val cardBorder = if (expanded) Kale300 else Salt400

    Box(
        Modifier
            .fillMaxWidth()
            // Soft, teal-tinted shadow only when open (mockup uses a low, spread teal glow).
            .then(
                if (expanded) Modifier.shadow(
                    elevation = 8.dp,
                    shape = RoundedCornerShape(16.dp),
                    clip = false,
                    ambientColor = Kale500.copy(alpha = 0.10f),
                    spotColor = Kale500.copy(alpha = 0.20f),
                ) else Modifier,
            )
            .clip(RoundedCornerShape(16.dp))
            .background(cardBg)
            .border(1.dp, cardBorder, RoundedCornerShape(16.dp)),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = 20.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    Modifier.size(40.dp).clip(RoundedCornerShape(11.dp))
                        .background(if (expanded) Guava100 else Kale100),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        typeIcon(kind),
                        contentDescription = null,
                        tint = if (expanded) Guava600 else Kale600,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        res.title?.takeIf { it.isNotBlank() }
                            ?: res.name?.takeIf { it.isNotBlank() }
                            ?: "Untitled",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = 19.sp, fontWeight = FontWeight.SemiBold,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        typeLabel(kind).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.spiraExtras.mutedForeground,
                    )
                }
                Icon(
                    SpiraIcons.ChevronDown,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.spiraExtras.mutedForeground,
                    modifier = Modifier.size(22.dp).rotate(chevronRotation),
                )
            }

            AnimatedVisibility(expanded) {
                // Each body renders its own single action row (primary + compact icon actions,
                // including an icon-only Delete), matching the mockup.
                Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 20.dp)) {
                    when (kind) {
                        "note" -> NoteBody(res, actions)
                        "link" -> LinkBody(res, actions)
                        "email" -> EmailBody(res, actions)
                        else -> FileBody(res, actions, onOpenFull)
                    }
                }
            }
        }
    }
}

@Composable
private fun NoteBody(res: ResourceItem, actions: GoalWorkspaceActions) {
    val context = LocalContext.current
    BoxedField("Title", res.title ?: "", "Note title") { commitResource(actions, res, title = it.ifBlank { null }) }
    Spacer(Modifier.height(13.dp))
    // A muted plain-text preview so the note's text is visible in the list. Full editing happens in
    // the dedicated NoteEditorActivity (a real window where the WebView reliably gets keyboard focus).
    val preview = htmlToPlainText(res.body)
    Text(
        text = preview.ifBlank { "Empty note — tap Open note to write it." },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.spiraExtras.mutedForeground,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
    )
    Spacer(Modifier.height(14.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        ResourcePrimaryButton("Open note", SpiraIcons.Maximize, Modifier.weight(1f)) {
            context.startActivity(
                NoteEditorActivity.intent(context, res.id, res.title ?: "", res.body ?: ""),
            )
        }
        ResourceIconButton(SpiraIcons.Copy, "Copy note") { copyPlainText(context, "Note", preview) }
        ResourceIconButton(SpiraIcons.Trash, "Delete resource", danger = true) { actions.onRemoveResource(res.id) }
    }
}

@Composable
private fun LinkBody(res: ResourceItem, actions: GoalWorkspaceActions) {
    val context = LocalContext.current
    BoxedField("Title", res.title ?: "", "Link title") { commitResource(actions, res, title = it.ifBlank { null }) }
    Spacer(Modifier.height(13.dp))
    BoxedField("URL", res.url ?: "", "https://…", keyboardType = KeyboardType.Uri) {
        commitResource(actions, res, url = it.ifBlank { null })
    }
    Spacer(Modifier.height(15.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        ResourcePrimaryButton("Open link", SpiraIcons.ExternalLink, Modifier.weight(1f), enabled = !res.url.isNullOrBlank()) {
            openUri(context, res.url)
        }
        ResourceIconButton(SpiraIcons.Copy, "Copy link") { copyPlainText(context, "Link", res.url ?: "") }
        ResourceIconButton(SpiraIcons.Trash, "Delete resource", danger = true) { actions.onRemoveResource(res.id) }
    }
}

@Composable
private fun EmailBody(res: ResourceItem, actions: GoalWorkspaceActions) {
    val context = LocalContext.current
    BoxedField("Name", res.name ?: "", "Full name", copyValue = res.name) { commitResource(actions, res, name = it.ifBlank { null }) }
    Spacer(Modifier.height(13.dp))
    BoxedField("Role or relationship", res.role ?: "", "e.g. Lawyer", copyValue = res.role) { commitResource(actions, res, role = it.ifBlank { null }) }
    Spacer(Modifier.height(13.dp))
    BoxedField("Email", res.email ?: "", "name@example.com", keyboardType = KeyboardType.Email, copyValue = res.email) {
        commitResource(actions, res, email = it.ifBlank { null })
    }
    Spacer(Modifier.height(13.dp))
    BoxedField("Phone (optional)", res.phone ?: "", "+351 …", keyboardType = KeyboardType.Phone, copyValue = res.phone) {
        commitResource(actions, res, phone = it.ifBlank { null })
    }
    Spacer(Modifier.height(15.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        ResourcePrimaryButton("Email", SpiraIcons.Mail, Modifier.weight(1f), enabled = !res.email.isNullOrBlank()) {
            openUri(context, "mailto:${res.email}")
        }
        if (!res.phone.isNullOrBlank()) {
            ResourceIconButton(SpiraIcons.Phone, "Call") { openUri(context, "tel:${res.phone}") }
        }
        ResourceIconButton(SpiraIcons.Copy, "Copy all fields") {
            val all = listOfNotNull(
                res.name?.takeIf { it.isNotBlank() },
                res.role?.takeIf { it.isNotBlank() },
                res.email?.takeIf { it.isNotBlank() },
                res.phone?.takeIf { it.isNotBlank() },
            ).joinToString("\n")
            copyPlainText(context, "Contact", all)
        }
        ResourceIconButton(SpiraIcons.Trash, "Delete resource", danger = true) { actions.onRemoveResource(res.id) }
    }
}

@Composable
private fun FileBody(res: ResourceItem, actions: GoalWorkspaceActions, onOpenFull: (String) -> Unit) {
    val context = LocalContext.current
    val bytes = remember(res.dataUrl) { decodeDataUrl(res.dataUrl) }

    when {
        isImageMime(res.mime) && bytes != null -> {
            val img = remember(bytes) { decodeImageBitmap(bytes) }
            if (img != null) {
                Image(
                    bitmap = img,
                    contentDescription = res.title,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onOpenFull(res.id) },
                    contentScale = ContentScale.Fit,
                )
                Spacer(Modifier.height(12.dp))
            }
        }
        isPdfMime(res.mime) && bytes != null -> {
            Box(
                Modifier.fillMaxWidth().aspectRatio(1.6f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Parsnip100)
                    .border(1.dp, Salt400, RoundedCornerShape(12.dp))
                    .clickable { onOpenFull(res.id) },
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(SpiraIcons.File, contentDescription = null, tint = Kale600, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.height(6.dp))
                    Text("PDF", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.spiraExtras.mutedForeground)
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }

    Text(
        res.title ?: res.name ?: "File",
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
    )
    // Friendly meta: "Image · 820 KB" / "PDF · 1.2 MB · 4 pages" (no raw "application/pdf").
    if (bytes != null) {
        val isPdf = isPdfMime(res.mime)
        val pages by produceState(0, bytes, isPdf) { value = if (isPdf) pdfPageCount(context, bytes) else 0 }
        val meta = buildString {
            append(if (isPdf) "PDF" else "Image")
            append(" · ").append(humanFileSize(bytes.size))
            if (isPdf && pages > 0) append(" · ").append(pages).append(if (pages == 1) " page" else " pages")
        }
        Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.spiraExtras.mutedForeground)
    }
    Spacer(Modifier.height(14.dp))
    when {
        bytes != null -> {
            val saveFile = rememberFileSaver()
            // One row: Open (primary), then Download / Copy(image only) / Delete icons.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                ResourcePrimaryButton("Open", SpiraIcons.Maximize, Modifier.weight(1f)) { onOpenFull(res.id) }
                ResourceIconButton(SpiraIcons.Download, "Download") {
                    saveFile(downloadFileName(res.title, res.mime), res.mime ?: "application/octet-stream", bytes)
                }
                if (isImageMime(res.mime)) {
                    ResourceIconButton(SpiraIcons.Copy, "Copy image") { copyImageToClipboard(context, res.mime, bytes) }
                }
                ResourceIconButton(SpiraIcons.Trash, "Delete resource", danger = true) { actions.onRemoveResource(res.id) }
            }
        }
        !res.driveWebViewLink.isNullOrBlank() ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                ResourcePrimaryButton("Open in Drive", SpiraIcons.ExternalLink, Modifier.weight(1f)) { openUri(context, res.driveWebViewLink) }
                ResourceIconButton(SpiraIcons.Trash, "Delete resource", danger = true) { actions.onRemoveResource(res.id) }
            }
        else ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (!res.url.isNullOrBlank()) {
                    ResourcePrimaryButton("Open", SpiraIcons.ExternalLink, Modifier.weight(1f)) { openUri(context, res.url) }
                } else {
                    Spacer(Modifier.weight(1f))
                }
                ResourceIconButton(SpiraIcons.Trash, "Delete resource", danger = true) { actions.onRemoveResource(res.id) }
            }
    }
}

/** A labelled, boxed inline field (commits on blur / Done) styled like the mockup's inputs. When
 *  [copyValue] is non-blank a small copy button sits to the right of the field. */
@Composable
private fun BoxedField(
    label: String,
    value: String,
    placeholder: String,
    singleLine: Boolean = true,
    minLines: Int = 1,
    keyboardType: KeyboardType = KeyboardType.Text,
    copyValue: String? = null,
    onCommit: (String) -> Unit,
) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.spiraExtras.mutedForeground,
        )
        // The copy button lives INSIDE the field box, trailing the text.
        Row(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(11.dp))
                .background(Parsnip100)
                .border(1.dp, Salt400, RoundedCornerShape(11.dp))
                .padding(start = 14.dp, end = 6.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f).padding(vertical = 10.dp)) {
                InlineEditText(
                    value = value,
                    onCommit = onCommit,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.5.sp),
                    placeholder = placeholder,
                    singleLine = singleLine,
                    minLines = minLines,
                    keyboardType = keyboardType,
                )
            }
            if (!copyValue.isNullOrBlank()) {
                Box(
                    Modifier.size(34.dp).clip(RoundedCornerShape(8.dp))
                        .clickable { copyPlainText(context, label, copyValue) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(SpiraIcons.Copy, contentDescription = "Copy $label", tint = MaterialTheme.spiraExtras.mutedForeground, modifier = Modifier.size(17.dp))
                }
            }
        }
    }
}

@Composable
private fun ResourcePrimaryButton(
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        modifier
            .clip(RoundedCornerShape(11.dp))
            .background(if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.spiraExtras.border)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(17.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimary)
    }
}

/** A compact square, outlined icon action (Download / Copy / Call / Delete). Delete uses the
 *  danger (guava) treatment. Sits in the same row as the primary action. */
@Composable
private fun ResourceIconButton(icon: ImageVector, contentDescription: String, danger: Boolean = false, onClick: () -> Unit) {
    val tint = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    val borderColor = if (danger) Guava200 else MaterialTheme.spiraExtras.border
    Box(
        Modifier.size(44.dp)
            .clip(RoundedCornerShape(11.dp))
            .border(1.dp, borderColor, RoundedCornerShape(11.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(18.dp))
    }
}

private fun openUri(context: android.content.Context, uri: String?) {
    if (uri.isNullOrBlank()) return
    val normalized = if (uri.startsWith("http") || uri.contains(":")) uri else "https://$uri"
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(normalized))) }
}
