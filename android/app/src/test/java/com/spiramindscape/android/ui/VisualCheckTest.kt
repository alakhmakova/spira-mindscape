package com.spiramindscape.android.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.spiramindscape.android.data.auth.AuthUser
import com.spiramindscape.android.ui.goals.SpiraDrawer
import com.spiramindscape.android.ui.theme.SpiraTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/**
 * Renders key surfaces to PNGs (app/build/reports/visual/) so layout/color regressions can be
 * checked by eye — added after the drawer once rendered with its lower half pushed off-screen
 * while existence-only assertions stayed green. Draws the decor view directly (Robolectric
 * NATIVE graphics), no PixelCopy needed.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VisualCheckTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val user = AuthUser(1, "tester@example.com", "Tester", null)

    private fun saveWindow(name: String) {
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(maxOf(view.width, 1), maxOf(view.height, 1), Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val file = File("build/reports/visual/$name.png")
        file.parentFile?.mkdirs()
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun `drawer renders fully`() {
        compose.activityRule.scenario.onActivity { }
        compose.setContent { SpiraTheme { SpiraDrawer(user, onLogout = {}) } }
        compose.waitForIdle()
        saveWindow("drawer")
    }
}
