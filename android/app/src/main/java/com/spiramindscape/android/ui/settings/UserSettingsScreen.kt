package com.spiramindscape.android.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spiramindscape.android.data.auth.AuthUser
import com.spiramindscape.android.ui.components.ConfirmDialog
import com.spiramindscape.android.ui.components.HeaderCircleAction
import com.spiramindscape.android.ui.components.SpiraCard
import com.spiramindscape.android.ui.icons.SpiraIcons
import com.spiramindscape.android.ui.theme.AppFont
import com.spiramindscape.android.ui.theme.AppFontState
import com.spiramindscape.android.ui.theme.Kale600
import com.spiramindscape.android.ui.theme.LocalAppFont
import com.spiramindscape.android.ui.theme.spiraExtras

/**
 * The account's own page, reached by tapping the figure in the All-goals header.
 *
 * It exists because that tap used to open the navigation drawer — the same thing the hamburger on
 * the other side already did, so the app had two buttons for one action and nowhere at all to see
 * or leave your account.
 *
 * Two tabs, after the reference the owner supplied (GRO-122):
 *
 *  - **My profile** — the address, and nothing to edit. It is a Google account and Spira has no
 *    say over the address on it; a field that cannot save is worse than a plain fact.
 *  - **Fonts** — the body face the whole app is set in, while the owner picks one.
 *
 * Deliberately a **page**, not a sheet: it is somewhere you go, it has a back gesture, and the
 * Fonts tab needs room to show a specimen per candidate.
 */
@Composable
fun UserSettingsScreen(
    user: AuthUser,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    /** Open straight onto About Spira — how the drawer's "About Spira" rows arrive here. */
    startOnAbout: Boolean = false,
) {
    var tab by remember {
        mutableStateOf(if (startOnAbout) SettingsTab.About else SettingsTab.Profile)
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        SettingsTopBar(onBack = onBack)
        SettingsTabs(selected = tab, onSelect = { tab = it })

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(16.dp))
            when (tab) {
                SettingsTab.Profile -> ProfileTab(user = user, onLogout = onLogout)
                SettingsTab.Fonts -> FontsTab()
                SettingsTab.About -> AboutTab()
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private enum class SettingsTab(val label: String) {
    Profile("My profile"),
    Fonts("Fonts"),
    About("About Spira"),
}

/**
 * The tab row. The current tab carries a **Guava underline** as wide as its word — the same mark
 * the goal workspace's GROW tabs use, so a tab means the same thing everywhere in the app.
 */
@Composable
private fun SettingsTabs(selected: SettingsTab, onSelect: (SettingsTab) -> Unit) {
    Column(Modifier.fillMaxWidth().background(MaterialTheme.spiraExtras.surfaceRaised)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.Bottom) {
            SettingsTab.entries.forEach { entry ->
                val here = entry == selected
                Column(
                    Modifier
                        .width(IntrinsicSize.Max)
                        // No ripple: the underline is the feedback, and a Material wash over the
                        // white bar is exactly the grey the rest of the app avoids.
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onSelect(entry) },
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        entry.label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (here) FontWeight.Bold else FontWeight.Medium,
                        color = if (here) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.spiraExtras.mutedForeground
                        },
                        modifier = Modifier.padding(top = 14.dp, bottom = 10.dp, start = 4.dp, end = 4.dp),
                    )
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(if (here) 3.dp else 0.dp)
                            .background(MaterialTheme.colorScheme.tertiary),
                    )
                }
                Spacer(Modifier.width(24.dp))
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.spiraExtras.border))
    }
}

/** Who is signed in, and the one action on the page. */
@Composable
private fun ProfileTab(user: AuthUser, onLogout: () -> Unit) {
    var confirmSignOut by remember { mutableStateOf(false) }

    SpiraCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.spiraExtras.primarySoft),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    initialsOf(user),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.size(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    user.name?.takeIf { it.isNotBlank() } ?: "Signed in",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    user.email,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.spiraExtras.mutedForeground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }

    // The address on its own row, said plainly and NOT editable — it belongs to the Google
    // account, and Spira cannot change it.
    SettingsGroup("Email") {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(user.email, style = MaterialTheme.typography.bodyMedium)
            Text(
                "Signed in with Google. Spira can't change your address — it belongs to that account.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.spiraExtras.mutedForeground,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }

    SpiraCard(contentPadding = PaddingValues(0.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { confirmSignOut = true }
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                SpiraIcons.LogOut,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp),
            )
            Text(
                "Sign out",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }

    if (confirmSignOut) {
        ConfirmDialog(
            title = "Sign out?",
            message = "You'll need to sign in with Google again to reach your goals on this phone.",
            confirmLabel = "Yes, sign out",
            onConfirm = { confirmSignOut = false; onLogout() },
            onDismiss = { confirmSignOut = false },
        )
    }
}

/**
 * The body face the whole app is set in.
 *
 * **Every row is written in the face it offers**, so the list is itself the specimen sheet; and
 * tapping one re-fonts the page under the finger — these tabs, these labels and this very row —
 * which is the fastest way to judge a candidate.
 */
@Composable
private fun FontsTab() {
    val appFont = LocalAppFont.current

    Text(
        "Sets the body face everywhere — cards, labels, buttons and this page. Headings stay on " +
            "ITC Clearface. The choice is remembered on this phone.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.spiraExtras.mutedForeground,
    )
    // Split by alphabet: a Latin-only face draws Russian in the system sans, so those rows are
    // quietly showing two fonts at once and can't be judged as one.
    FontGroup(
        heading = "With Cyrillic",
        caption = "Draws Russian in its own letterforms.",
        fonts = AppFont.entries.filter { it.cyrillic },
        appFont = appFont,
    )
    FontGroup(
        heading = "Latin only",
        caption = "Russian falls back to the system sans, GCentra included.",
        fonts = AppFont.entries.filter { !it.cyrillic },
        appFont = appFont,
    )
}

@Composable
private fun FontGroup(
    heading: String,
    caption: String,
    fonts: List<AppFont>,
    appFont: AppFontState,
) {
    if (fonts.isEmpty()) return
    Column(Modifier.padding(top = 8.dp)) {
        Text(
            heading,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.spiraExtras.mutedForeground,
        )
        Text(
            caption,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.spiraExtras.mutedForeground,
            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            fonts.forEach { font ->
                FontRow(
                    font = font,
                    selected = font == appFont.current,
                    onSelect = { appFont.current = font },
                )
            }
        }
    }
}

@Composable
private fun FontRow(font: AppFont, selected: Boolean, onSelect: () -> Unit) {
    val family: FontFamily = font.fontFamily
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(
                if (selected) MaterialTheme.spiraExtras.primarySoft
                else MaterialTheme.spiraExtras.surfaceRaised,
            )
            .border(
                1.dp,
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.spiraExtras.border,
                MaterialTheme.shapes.medium,
            )
            .clickable(onClick = onSelect)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier
                .size(20.dp)
                .clip(CircleShape)
                .border(
                    2.dp,
                    if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.spiraExtras.borderStrong,
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                font.label,
                style = MaterialTheme.typography.bodyLarge,
                fontFamily = family,
                fontWeight = FontWeight.Bold,
            )
            // A pangram, so every letter is on show in the candidate rather than just its name.
            Text(
                "The quick brown fox jumps over the lazy dog 0123456789",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = family,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                modifier = Modifier.padding(top = 2.dp),
            )
            if (font.cyrillic) {
                Text(
                    "Съешь ещё этих мягких французских булок да выпей чаю",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = family,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Text(
                font.note,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.spiraExtras.mutedForeground,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/** The page's header: the workspace's teal band and its chevron-in-a-circle, with a plain title. */
@Composable
private fun SettingsTopBar(onBack: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Kale600)
            .windowInsetsPadding(WindowInsets.statusBars)
            .height(64.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        HeaderCircleAction(SpiraIcons.ChevronLeft, "Back", onBack)
        Text(
            "Settings",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

/** A titled block of settings rows — the rubric outside the card, the rows inside it. */
@Composable
private fun AboutTab() {
    val uriHandler = LocalUriHandler.current
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        ABOUT_SECTIONS.forEach { section ->
            SettingsGroup(section.heading) {
                Column(
                    Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    section.blocks.forEach { block ->
                        when (block) {
                            is AboutBlock.Prose -> Text(
                                block.text,
                                style = MaterialTheme.typography.bodyMedium,
                                lineHeight = 22.sp,
                            )
                            is AboutBlock.Points -> Column(
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                block.items.forEach { point ->
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(
                                            point.term,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        Text(
                                            point.text,
                                            style = MaterialTheme.typography.bodyMedium,
                                            lineHeight = 22.sp,
                                            color = MaterialTheme.spiraExtras.mutedForeground,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        SettingsGroup("Further reading") {
            Column(
                Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Two standard texts on coaching, if you want to read further. Neither is " +
                        "required to use Spira.",
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = 22.sp,
                    color = MaterialTheme.spiraExtras.mutedForeground,
                )
                ABOUT_FURTHER_READING.forEach { book ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable { uriHandler.openUri(book.href) },
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            book.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            "${'$'}{book.author} — ${'$'}{book.note}",
                            style = MaterialTheme.typography.bodyMedium,
                            lineHeight = 22.sp,
                            color = MaterialTheme.spiraExtras.mutedForeground,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.spiraExtras.mutedForeground,
            modifier = Modifier.padding(start = 4.dp),
        )
        SpiraCard(contentPadding = PaddingValues(0.dp)) { content() }
    }
}

/**
 * Initials for the avatar disc. Two words give first + last; one word gives its first two letters,
 * and an account with no name at all falls back to the address rather than to an empty circle.
 */
private fun initialsOf(user: AuthUser): String {
    val source = user.name?.takeIf { it.isNotBlank() } ?: user.email
    val parts = source.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when {
        parts.size >= 2 -> "${parts.first().first()}${parts.last().first()}".uppercase()
        else -> source.take(2).uppercase()
    }
}
