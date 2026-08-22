package com.spiramindscape.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.spiramindscape.android.data.goals.ResourceItem
import com.spiramindscape.android.ui.components.AttachResourceButton
import com.spiramindscape.android.ui.components.FieldLabel
import com.spiramindscape.android.ui.components.InlineResourcesValue
import com.spiramindscape.android.ui.components.ProvideInlineResources
import com.spiramindscape.android.ui.components.SpiraTextField
import com.spiramindscape.android.ui.icons.SpiraIcons
import com.spiramindscape.android.ui.theme.SpiraTheme
import com.spiramindscape.android.ui.util.namesToTokens
import com.spiramindscape.android.ui.util.readableText
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The two controls the "New target" form gained on 2026-08-20 (GRO-144): "Attach resource" under
 * the title, and the paperclip on a task row.
 *
 * Rendered rather than asserted because what can go wrong here is entirely visual — a worded link
 * too heavy under the field, or a paperclip crowding the task's own X. The form itself is a
 * `SpiraFormSheet`, and a `ModalBottomSheet` renders in **its own window**, which the screenshot
 * helper (it draws the activity's decor view) cannot capture — so the body's two new rows are laid
 * out here directly, the same way `VisualCheckToolbarMenusTest` renders a menu surface.
 *
 * Look at `app/build/reports/visual/new-target-attach.png`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp")
class VisualCheckNewTargetAttachTest : VisualCheckTestBase() {

    private val resources = listOf(
        ResourceItem(id = "r1", type = "link", title = "Job ad", url = "https://solita.fi"),
        ResourceItem(id = "r2", type = "note", title = "Interview notes"),
    )

    @Test
    fun `new target form — attach controls`() {
        compose.setContent {
            SpiraTheme {
                ProvideInlineResources(
                    InlineResourcesValue(resources = resources, openResource = {}),
                ) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        SpiraTextField(
                            "Send the signed contract {{res:Job ad}}",
                            {},
                            "Title",
                        )
                        AttachResourceButton(attachedTo = "", onAttach = {})

                        FieldLabel("Tasks")
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            // Exactly what the sheet draws: the tag as the resource's NAME, with
                            // no braces — the row is a read-only strip.
                            Text(
                                "• " + readableText(
                                    namesToTokens("Read the brief {{res:Interview notes}}", resources),
                                    resources,
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            AttachResourceButton(
                                iconOnly = true,
                                contentDescription = "Attach a resource to this task",
                                attachedTo = "",
                                onAttach = {},
                            )
                            IconButton(onClick = {}) {
                                Icon(SpiraIcons.X, contentDescription = "Remove task")
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        saveWindow("new-target-attach")
    }
}
