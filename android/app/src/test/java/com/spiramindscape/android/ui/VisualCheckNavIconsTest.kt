package com.spiramindscape.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.spiramindscape.android.ui.icons.SpiraIcons
import com.spiramindscape.android.ui.theme.SpiraTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the filled navigation glyphs on their own. A path that fails to parse draws *nothing*
 * while every existence assertion stays green, so the only way to know an icon is really there is
 * to look at it — this is the sheet to look at when one is added or changed.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VisualCheckNavIconsTest : VisualCheckTestBase() {

    @Test
    fun `the navigation icons all draw`() {
        val icons = listOf(
            "chevron" to SpiraIcons.NavChevronLeft,
            "search" to SpiraIcons.NavSearch,
            "menu" to SpiraIcons.NavMenu,
            "resources" to SpiraIcons.NavResources,
            "home" to SpiraIcons.NavHome,
            "ai" to SpiraIcons.NavAi,
            "trophy" to SpiraIcons.NavTrophy,
        )
        compose.activityRule.scenario.onActivity { }
        compose.setContent {
            SpiraTheme {
                Column(
                    Modifier.fillMaxSize().background(Color.White).padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    icons.forEach { (name, icon) ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(48.dp),
                            )
                            Icon(
                                icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(21.dp),
                            )
                            Text(name, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        saveWindow("nav-icons")
    }
}
