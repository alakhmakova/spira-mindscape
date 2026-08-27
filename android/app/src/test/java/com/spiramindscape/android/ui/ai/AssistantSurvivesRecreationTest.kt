package com.spiramindscape.android.ui.ai

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **The AI panel must stay open across an activity recreation** (BUG-046).
 *
 * `MainActivity` declares no `android:configChanges`, which is right for a Compose app — but it
 * means a camera app opening in landscape recreates the activity essentially every time someone
 * takes a photo. The flag holding the panel open was a plain `remember`, so it reset to `false`
 * and the panel shut itself the moment the camera appeared. Nothing had crashed; the owner
 * reasonably described it as the app restarting on its own.
 *
 * ## Why this is a source scan and not a Compose test
 *
 * A Compose test that declares its own `rememberSaveable` flag and recreates the activity proves
 * only that `rememberSaveable` works — it would stay green with the app fully broken, because it
 * never touches the two declarations that matter. Driving the real screens instead means standing
 * up a view model, a loaded goal and a navigation host for the sake of one boolean.
 *
 * So this reads the source, the way `LoggingConventionTest` does on the backend. It is blunt, and
 * it fails for exactly the change that caused the bug.
 */
class AssistantSurvivesRecreationTest {

    private val screens = listOf(
        "src/main/java/com/spiramindscape/android/ui/goals/GoalWorkspaceScreen.kt",
        "src/main/java/com/spiramindscape/android/ui/goals/GoalsDashboardScreen.kt",
    )

    @Test
    fun `the assistant's open flag is saveable, on every screen that owns one`() {
        for (path in screens) {
            val file = File(path)
            assertTrue("Missing source file: $path — has it moved?", file.exists())
            val source = file.readText()

            val declarations = Regex("""var\s+assistantOpen\s+by\s+(\w+)\s*\{""")
                .findAll(source)
                .map { it.groupValues[1] }
                .toList()

            assertTrue(
                "$path no longer declares `assistantOpen` — if the panel's open state moved " +
                    "somewhere else, move this check with it rather than deleting it.",
                declarations.isNotEmpty(),
            )
            for (how in declarations) {
                assertTrue(
                    "$path declares `assistantOpen by $how { … }`. It must be rememberSaveable: " +
                        "a plain remember is lost when the activity is recreated, which the " +
                        "camera does every time it opens in landscape, and the panel then closes " +
                        "itself mid-task (BUG-046).",
                    how == "rememberSaveable",
                )
            }
        }
    }
}
