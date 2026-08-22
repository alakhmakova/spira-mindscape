package com.spiramindscape.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spiramindscape.android.ui.icons.SpiraIcons
import com.spiramindscape.android.ui.theme.Kale600
import com.spiramindscape.android.ui.theme.Salt400
import com.spiramindscape.android.ui.theme.SpiraRadii
import com.spiramindscape.android.ui.theme.spiraExtras

/**
 * The goal-workspace chrome: a teal header (home / goal search / delete), the GROW tab bar that
 * sits under it on every phase screen, and the three-action footer (menu / AI / resources).
 *
 * It is deliberately separate from [SpiraTopBar] — the All-goals dashboard keeps that header
 * unchanged; only inside a goal does the chrome turn into this navigation.
 */

/** A white icon inside a translucent circle — the header's two round buttons. */
@Composable
private fun HeaderCircleButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(HEADER_BUTTON_SIZE)
            .clip(CircleShape)
            .background(MaterialTheme.spiraExtras.surfaceRaised)
            .clickable(onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp),
        )
    }
}

/**
 * The header band's shared measurements. The circular buttons are **solid white with a teal
 * glyph** — the reference's treatment. They used to be a 16%-white wash with a white mark, which
 * on the teal band read as barely-there and made the delete cross easy to miss; a white disc is
 * unmistakably a button.
 */
// Back to 38dp. Matching the 48dp field made the two discs the loudest thing in the bar — the
// field is what the header is for, and the buttons should sit beside it, not compete with it.
private val HEADER_BUTTON_SIZE = 38.dp
private val SEARCH_FIELD_HEIGHT = 48.dp
private val SEARCH_FIELD_RADIUS = 10.dp

/** The header's circle button for callers in other files (the dashboard's search bar). */
@Composable
fun HeaderCircleClose(contentDescription: String, onClick: () -> Unit) =
    HeaderCircleButton(SpiraIcons.X, contentDescription, onClick)

/**
 * The workspace header: **chevron-in-a-circle** on the left (back to All goals), a **goal search**
 * field in the middle, and an **X-in-a-circle** on the right that deletes the goal.
 *
 * The search box is a switcher: [onQueryChange] filters the caller's goal list and the caller
 * renders the results underneath. It starts empty on every visit — a search typed on one screen
 * must never follow the user into another.
 */
@Composable
fun GoalWorkspaceTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onHome: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .background(Kale600)
            .windowInsetsPadding(WindowInsets.statusBars)
            .height(64.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        HeaderCircleButton(SpiraIcons.ChevronLeft, "All goals", onHome)
        SpiraSearchField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = "Search goals",
            modifier = Modifier.weight(1f),
        )
        HeaderCircleButton(SpiraIcons.X, "Delete goal", onDelete)
    }
}

/** [HeaderCircleButton] for callers outside this file — the resource headers' right-hand action. */
@Composable
fun HeaderCircleAction(icon: ImageVector, contentDescription: String, onClick: () -> Unit) =
    HeaderCircleButton(icon, contentDescription, onClick)

/**
 * The header a resource opens under — one bar for **both** the note editor and the file viewer, so
 * opening either feels like the same place rather than two screens that happen to be nearby.
 *
 * It is the workspace header's twin: the same Kale-600 band, the same height, the same
 * chevron-in-a-circle on the left. The middle carries the resource's own name, editable in place.
 * What differs is only the right-hand action, which is the caller's to supply — "Done" for a note,
 * a delete cross for a file.
 */
@Composable
fun ResourceTopBar(
    title: String,
    onTitleCommit: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    action: @Composable () -> Unit = {},
) {
    Row(
        modifier
            .fillMaxWidth()
            .background(Kale600)
            .windowInsetsPadding(WindowInsets.statusBars)
            .height(64.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        HeaderCircleButton(SpiraIcons.ChevronLeft, "Back", onBack)
        // The name is edited where it is shown, so there is no second title field competing with
        // it. On the teal band it is set in white and carries no box — the header IS its frame.
        InlineEditText(
            value = title,
            onCommit = onTitleCommit,
            modifier = Modifier.weight(1f),
            textStyle = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimary,
            ),
            placeholder = "Untitled",
            singleLine = true,
            required = true,
        )
        action()
    }
}

/**
 * The one search input in the app — the workspace header's goal switcher and the dashboard's
 * search mode both render this, so the two never drift apart.
 *
 * Shaped after the reference: a **48dp, softly rounded (10dp) well** — not the 40dp pill it used
 * to be. A pill on a teal band read as a chip; a rounded rectangle reads as a field you type in.
 * The fill is the sunken grey rather than white, which is what separates the field from the pure
 * white circular buttons beside it. The magnifier leads in **teal**, at a size you can actually
 * see (it was a 15dp grey mark that disappeared into the placeholder).
 *
 * Boxed (not inline) because it is header chrome, not a field that edits the goal; it carries no
 * floating label, only a placeholder.
 */
@Composable
fun SpiraSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .height(SEARCH_FIELD_HEIGHT)
            .clip(RoundedCornerShape(SEARCH_FIELD_RADIUS))
            .background(MaterialTheme.spiraExtras.surfaceSunken),
        singleLine = true,
        textStyle = LocalTextStyle.current.merge(
            MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
        decorationBox = { field ->
            Row(
                Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    SpiraIcons.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        Text(
                            placeholder,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.spiraExtras.mutedForeground,
                            maxLines = 1,
                        )
                    }
                    field()
                }
                if (value.isNotEmpty()) {
                    // **The word "Clear", never a cross** (owner, 2026-08-22). On a phone the bar
                    // this field sits in already closes with an X — a second cross inside the field
                    // put two identical marks a few dp apart, and neither said which one emptied
                    // the query and which one dismissed the search.
                    Text(
                        "Clear",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        modifier = Modifier
                            .clip(RoundedCornerShape(SpiraRadii.sm))
                            .clickable { onValueChange("") }
                            .semantics { contentDescription = "Clear search" }
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                    )
                }
            }
        },
    )
}

/**
 * The GROW phase tabs that sit under the header on every workspace screen. The tab the user is on
 * is marked by a **Guava underline** (the brand accent used as a small mark, never a fill); the
 * same row also switches by swiping the pager, so tapping and swiping always agree.
 *
 * [selectedIndex] of `-1` means "none of these" — that is how the Resources page shows the row
 * without claiming one of the phases, so a tap still leads straight back into the flow.
 */
/**
 * Marks the tab row for tests. The drawer lists the same four phase names, and its content stays
 * in the semantics tree while it is closed, so "Options" alone matches two nodes — a test has to
 * say which one it means.
 */
const val GROW_TABS_TAG = "grow-tabs"

@Composable
fun GrowTabsRow(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .testTag(GROW_TABS_TAG)
            .background(MaterialTheme.spiraExtras.surfaceRaised),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            labels.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                Column(
                    Modifier
                        .weight(1f)
                        // No ripple: the underline is the feedback, and a Material ripple would put a
                        // grey wash over the white bar.
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onSelect(index) },
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // This inner column wraps the WORD, and the marker fills it — so the underline is
                    // as wide as the label rather than as wide as the quarter-screen cell. Filling the
                    // cell ran the mark from the screen edge under "Goal", which read as a stray rule
                    // rather than as a mark belonging to that tab.
                    //
                    // IntrinsicSize.Max is what makes that work: `fillMaxWidth` on the marker resolves
                    // against the incoming MAX constraint, not against its sibling, so inside a plain
                    // wrap-content column it still stretched to the whole cell. Asking the column for
                    // its children's intrinsic width pins it to the text.
                    Column(
                        Modifier.width(IntrinsicSize.Max),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.5.sp),
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.spiraExtras.mutedForeground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 14.dp, bottom = 10.dp, start = 4.dp, end = 4.dp),
                        )
                        // The marker appears and disappears with the selection rather than animating:
                        // the pager swipe is already the movement, and a spring here kept Compose's test
                        // clock from ever going idle under Robolectric.
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(if (selected) 3.dp else 0.dp)
                                .background(MaterialTheme.colorScheme.tertiary),
                        )
                        }
                    }
            }
        }
        // Resources is not a GROW phase, so no single tab is marked. Instead the mark runs under
        // the whole row: the flow is still there and one tap leads back into it, but none of the
        // four is claiming to be where the user is.
        if (selectedIndex < 0) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(MaterialTheme.colorScheme.tertiary),
            )
        }
    }
}

/**
 * The workspace footer: **main menu** on the left, the **AI assistant** in the middle and the
 * **Resources page** on the right. The assistant also opens by swiping up from this bar — pass
 * that gesture in as [swipeUpGesture] (see `AiChatHost`), so the whole footer is the grab area
 * rather than a hidden strip somewhere on the page.
 */
@Composable
fun GoalWorkspaceBottomBar(
    onMenu: () -> Unit,
    onAssistant: () -> Unit,
    onResources: () -> Unit,
    resourcesSelected: Boolean,
    modifier: Modifier = Modifier,
    swipeUpGesture: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.spiraExtras.surfaceRaised)
            .then(swipeUpGesture),
    ) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.spiraExtras.border))
        Row(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .height(64.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FooterAction(
                icon = SpiraIcons.Menu,
                label = "Menu",
                onClick = onMenu,
                modifier = Modifier.weight(1f),
            )
            // The assistant is the footer's primary action, and the sparkle sits a little larger
            // for it — but in the same ink as its neighbours. It used to carry the palette's
            // `intelligence` violet, which made one item in a row of three read as a badge rather
            // than as a place; the bar's job is to look like one bar.
            FooterAction(
                icon = SpiraIcons.Sparkles,
                label = "AI coach",
                onClick = onAssistant,
                modifier = Modifier.weight(1f),
                size = 23.dp,
            )
            FooterAction(
                icon = SpiraIcons.FolderOpen,
                label = "Resources",
                onClick = onResources,
                modifier = Modifier.weight(1f),
                selected = resourcesSelected,
                selectedIcon = SpiraIcons.FolderOpenFilled,
            )
        }
    }
}

/**
 * One footer action: its mark with its **name underneath**. A bottom bar of bare glyphs makes the
 * user guess; the label is what turns three icons into three named places.
 *
 * The [selected] one takes the brand teal — glyph and label both — while every other item stays
 * near-black, and a soft grey disc sits behind its mark. The labels keep one weight throughout,
 * because a bar whose type also thickens on select makes the row jitter as the user moves between
 * pages.
 */
/** The disc behind a footer glyph — sized so the mark sits in it with an even margin. */
private val DISC = 34.dp

@Composable
private fun FooterAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    /**
     * The **filled** twin of [icon], shown while [selected] — the way a bottom bar normally marks
     * its place. Only some Iconoir glyphs have a solid version; an item without one falls back to
     * the grey disc below.
     */
    selectedIcon: ImageVector? = null,
    tint: Color? = null,
    size: androidx.compose.ui.unit.Dp = 21.dp,
) {
    // Idle items are the page's own ink, not a grey: in the reference bar the unselected marks are
    // near-black and it is the teal that stands out. Muting them instead made the whole footer
    // recede and left "selected" reading as merely a darker grey.
    val resolved = tint ?: if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Column(
        modifier
            .fillMaxSize()
            .clickable(onClick = onClick)
            .semantics { this.contentDescription = label },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // The current item switches to the **filled** version of its glyph — the ordinary way a
        // bottom bar marks its place. The grey disc is the fallback for a mark that has no solid
        // twin in Iconoir, which is what every item used to rely on.
        val filled = selected && selectedIcon != null
        Box(
            Modifier
                .size(DISC)
                .clip(CircleShape)
                .then(if (selected && !filled) Modifier.background(Salt400) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (filled) selectedIcon!! else icon,
                contentDescription = null,
                tint = resolved,
                modifier = Modifier.size(size),
            )
        }
        Spacer(Modifier.height(3.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Medium,
            color = resolved,
        )
    }
}
