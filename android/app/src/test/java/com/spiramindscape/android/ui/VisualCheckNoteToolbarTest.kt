package com.spiramindscape.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.spiramindscape.android.ui.icons.SpiraIcons
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.spiramindscape.android.ui.goals.NoteEditorState
import com.spiramindscape.android.ui.goals.NoteToolbar
import com.spiramindscape.android.ui.theme.SpiraTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * The note editor's formatting toolbar, as pixels.
 *
 * The editor itself is a WebView in its own Activity, which a Robolectric render cannot show — but
 * the toolbar is plain Compose, and it is where the format painter and the paste button live. Both
 * were added on 2026-08-17 with glyphs transcribed by hand, and **a mis-transcribed Gravity path
 * draws nothing at all while every existence assertion stays green** (CLAUDE.md), so the only way
 * to know they arrived is to look.
 *
 * Rendered twice: idle, and with the painter holding a style so its lit state is visible too.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VisualCheckNoteToolbarTest : VisualCheckTestBase() {

    @Test
    fun `note toolbar renders every button`() {
        compose.activityRule.scenario.onActivity { }
        compose.setContent {
            SpiraTheme {
                Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                    // The two NEW glyphs, drawn large. The toolbar itself scrolls horizontally, so
                    // a 320px render only ever shows its first few buttons — and these two are the
                    // ones whose paths were transcribed by hand and could silently draw nothing.
                    Row(
                        Modifier.padding(start = 16.dp, bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                    ) {
                        Icon(
                            SpiraIcons.Paintbrush,
                            contentDescription = "Format painter",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp),
                        )
                        Icon(
                            SpiraIcons.Paste,
                            contentDescription = "Paste",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp),
                        )
                    }
                    NoteToolbar(
                        state = NoteEditorState(bold = true),
                        onCmd = { _, _ -> },
                        onLink = {},
                        onPaste = {},
                        modifier = Modifier.fillMaxWidth(),
                    )
                    NoteToolbar(
                        state = NoteEditorState(painter = true),
                        onCmd = { _, _ -> },
                        onLink = {},
                        onPaste = {},
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    )
                }
            }
        }
        compose.waitForIdle()
        saveWindow("note-toolbar")
    }
}
