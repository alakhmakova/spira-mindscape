package com.spiramindscape.android.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.spiramindscape.android.ui.icons.SpiraArt
import com.spiramindscape.android.ui.theme.SpiraTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The drawn plus on its own, big, so its two paths can be checked for registration. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VisualCheckPlusArtTest : VisualCheckTestBase() {

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun `the drawn plus, large`() {
        compose.activityRule.scenario.onActivity { }
        compose.setContent {
            SpiraTheme {
                Box(Modifier.fillMaxSize().background(Color.White), Alignment.Center) {
                    Image(
                        imageVector = SpiraArt.plusMark(),
                        contentDescription = null,
                        modifier = Modifier.size(260.dp),
                    )
                }
            }
        }
        compose.waitForIdle()
        saveWindow("plus-art")
    }
}
