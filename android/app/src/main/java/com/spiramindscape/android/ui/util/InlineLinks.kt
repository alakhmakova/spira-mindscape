package com.spiramindscape.android.ui.util

import com.spiramindscape.android.data.goals.ResourceItem

// Inline text is stored verbatim as plain text. Two things inside it get special rendering:
//   - a bare http(s) URL → a plain clickable link (no naming, no chip);
//   - a resource token `{{res:<id>}}` → a titled link for an attached Resource
//     (see specs/2026-07-28-inline-resource-attachments/requirements.md).
// Editing stays plain-text, so removing either one is just deleting text.
//
// Ported from the web `src/lib/spira/links.ts` — keep the two in step.

sealed interface InlineSegment {
    data class Text(val value: String) : InlineSegment
    data class Url(val url: String) : InlineSegment
    data class Resource(val id: String) : InlineSegment
}

// A bare http(s) URL. `[^\s<]` stops at whitespace/tags; trailing sentence punctuation is trimmed
// back out below so "see https://x.com." doesn't swallow the period into the link.
private const val URL_SOURCE = """https?://[^\s<]+"""

// Resource ids are server ids (numeric) or optimistic local ones (`local-xxxx`).
private const val RESOURCE_ID_SOURCE = """[A-Za-z0-9_-]+"""

private val SEGMENT_RE = Regex("""$URL_SOURCE|\{\{res:($RESOURCE_ID_SOURCE)\}\}""")
private val RESOURCE_TOKEN_RE = Regex("""\{\{res:($RESOURCE_ID_SOURCE)\}\}""")
private val TRAILING_PUNCT_RE = Regex("""[.,;:!?)\]}'"]+$""")

/**
 * A token as it appears while **editing**, where the braces may hold either the stored id or the
 * resource's name (which can contain spaces and punctuation) — see [rewriteResourceTokens].
 */
private val LOOSE_TOKEN_RE = Regex("""\{\{res:([^{}]+)\}\}""")

/** The inline token that references a resource by id. */
fun resourceToken(id: String): String = "{{res:$id}}"

/** Split a string into plain text, URL, and resource-token segments for rendering. */
fun splitInline(text: String): List<InlineSegment> {
    val segments = mutableListOf<InlineSegment>()
    var last = 0
    for (match in SEGMENT_RE.findAll(text)) {
        if (match.range.first > last) {
            segments += InlineSegment.Text(text.substring(last, match.range.first))
        }
        last = match.range.last + 1

        val resourceId = match.groupValues[1]
        if (resourceId.isNotEmpty()) {
            segments += InlineSegment.Resource(resourceId)
            continue
        }
        var url = match.value
        val punct = TRAILING_PUNCT_RE.find(url)
        if (punct != null) {
            url = url.dropLast(punct.value.length)
            segments += InlineSegment.Url(url)
            segments += InlineSegment.Text(punct.value)
            continue
        }
        segments += InlineSegment.Url(url)
    }
    if (last < text.length) segments += InlineSegment.Text(text.substring(last))
    return segments
}

/** True when the text carries at least one `{{res:id}}` reference — to any resource. */
fun hasResourceToken(text: String): Boolean = RESOURCE_TOKEN_RE.containsMatchIn(text)

/** True when the text references this specific resource. */
fun referencesResource(text: String, resourceId: String): Boolean =
    text.contains(resourceToken(resourceId))

/** Every resource id referenced by the text, in order, without duplicates. */
fun resourceIdsIn(text: String): List<String> =
    RESOURCE_TOKEN_RE.findAll(text).map { it.groupValues[1] }.distinct().toList()

/**
 * Replace every reference to one resource with plain text — used when the resource is deleted,
 * so the sentence keeps reading (its title stays) and no dangling token remains.
 */
fun replaceResourceToken(text: String, resourceId: String, replacement: String): String =
    text.split(resourceToken(resourceId)).joinToString(replacement)

/**
 * Replace every resource tag with plain text — for the places a value is read as prose rather
 * than rendered as a field (confirmation dialogs, search).
 */
fun stripResourceTokens(text: String, label: (String) -> String): String =
    LOOSE_TOKEN_RE.replace(text) { label(it.groupValues[1].trim()) }
        .replace(Regex("""\s{2,}"""), " ")
        .trim()

/**
 * Rewrite every resource tag through [resolve], which receives what is inside the braces (an id
 * or a name) and returns the replacement — or null to drop the braces and keep the bare text.
 * Used to swap ids for names when a field enters edit mode, and back again when it commits, so a
 * tag never survives as a dangling reference to something that can't be resolved.
 */
fun rewriteResourceTokens(text: String, resolve: (String) -> String?): String =
    LOOSE_TOKEN_RE.replace(text) { match ->
        val trimmed = match.groupValues[1].trim()
        resolve(trimmed)?.let { resourceToken(it) } ?: trimmed
    }

/** Only http/https URLs may be opened from inline text — blocks javascript:/data: schemes. */
fun isSafeHttpUrl(url: String): Boolean {
    val lower = url.lowercase()
    return lower.startsWith("http://") || lower.startsWith("https://")
}

/** Short human label for a bare URL — the site's name, e.g. "github" for github.com/x. */
fun titleFromUrl(url: String): String {
    val host = url
        .substringAfter("://", "")
        .substringBefore('/')
        .substringBefore('?')
        .removePrefix("www.")
        .lowercase()
    if (host.isEmpty()) return url
    return host.substringBefore('.').ifEmpty { host }
}

/** The name a resource shows under — on its card, in a picker, and inside an inline link. */
fun resourceDisplayName(resource: ResourceItem): String = when (resource.type) {
    "note" -> resource.title?.trim().orEmpty().ifEmpty { "Untitled note" }
    "link" -> resource.title?.trim().orEmpty().ifEmpty { titleFromUrl(resource.url.orEmpty()) }
    "file" -> resource.title?.trim().orEmpty().ifEmpty { "Untitled file" }
    else -> resource.name?.trim().orEmpty().ifEmpty { resource.email?.trim().orEmpty().ifEmpty { "Email" } }
}

/**
 * The text a field shows while being edited: each tag carries the resource's **name** instead of
 * its id, so `Read {{res:Job ad}} first` is readable. A tag whose id can't be resolved is left
 * untouched rather than guessed at.
 */
fun tokensToNames(text: String, resources: List<ResourceItem>): String =
    rewriteResourceTokens(text) { inner ->
        resources.firstOrNull { it.id == inner }?.let { resourceDisplayName(it) } ?: inner
    }

/**
 * The text a field stores: each tag's name (or id, if the user left one in place) is mapped back
 * to the resource id. A tag naming something that no longer exists degrades to plain text — the
 * words stay and no dangling reference is written.
 */
fun namesToTokens(text: String, resources: List<ResourceItem>): String =
    rewriteResourceTokens(text) { inner ->
        resources.firstOrNull { it.id == inner }?.id
            ?: resources.firstOrNull { resourceDisplayName(it).equals(inner, ignoreCase = true) }?.id
    }

/**
 * A field's text as plain prose: resource tags become the resource's name (or vanish, if it no
 * longer exists). Use wherever a value is quoted rather than rendered — dialogs, toasts — so a raw
 * `{{res:42}}` never reaches the user.
 */
fun readableText(text: String, resources: List<ResourceItem>): String =
    stripResourceTokens(text) { inner ->
        resources.firstOrNull { it.id == inner }?.let { resourceDisplayName(it) } ?: ""
    }

/**
 * Append a `{{res:id}}` reference to a field's text. Returns null when the field has no room
 * left — the token must never push a value past the server's limit.
 */
fun appendResourceToken(text: String, resourceId: String, maxLength: Int? = null): String? {
    val base = text.trim()
    val next = if (base.isEmpty()) resourceToken(resourceId) else "$base ${resourceToken(resourceId)}"
    if (maxLength != null && next.length > maxLength) return null
    return next
}
