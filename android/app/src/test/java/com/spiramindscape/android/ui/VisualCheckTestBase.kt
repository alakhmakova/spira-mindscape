package com.spiramindscape.android.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.spiramindscape.android.data.auth.AuthUser
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/**
 * Shared rule/helper for the `VisualCheck*Test` classes: renders key surfaces to PNGs
 * (app/build/reports/visual/) so layout/color regressions can be checked by eye — added after
 * the drawer once rendered with its lower half pushed off-screen while existence-only assertions
 * stayed green. Draws the decor view directly (Robolectric NATIVE graphics), no PixelCopy needed.
 *
 * One test per class (not one class with many `@Test` methods): Robolectric/Compose test state
 * leaked between test *methods* sharing a JVM (some ended with a still-focused text field, whose
 * cursor-blink coroutine never got cancelled in time), causing `AppNotIdleException` in whichever
 * test ran next — every test passed fine alone, only the combination was flaky. Gradle's
 * `forkEvery` (see `app/build.gradle.kts`) restarts the test JVM per *class*, not per method, so
 * splitting into one class per test is what actually gives each one a clean JVM. See
 * `backlog/android-visual-test-suite-flaky-appnotidle.md` (BUG-009).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class VisualCheckTestBase {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    protected val user = AuthUser(1, "tester@example.com", "Tester", null)

    protected fun saveWindow(name: String) {
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(maxOf(view.width, 1), maxOf(view.height, 1), Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val file = File("build/reports/visual/$name.png")
        file.parentFile?.mkdirs()
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        // Native-mode bitmaps aren't cheap; recycle promptly so several screenshot tests in one
        // JVM run don't pile up native memory pressure.
        bitmap.recycle()
    }
}
