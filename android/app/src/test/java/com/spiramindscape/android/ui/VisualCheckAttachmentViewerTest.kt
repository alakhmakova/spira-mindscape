package com.spiramindscape.android.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import com.spiramindscape.android.data.ai.AiApi
import com.spiramindscape.android.data.goals.ResourceItem
import com.spiramindscape.android.ui.ai.ChatAttachmentViewerContent
import com.spiramindscape.android.ui.theme.SpiraTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * **Opening an attachment** — the viewer behind every chip, on the composer and on a sent
 * message alike (BUG-027 / BUG-030).
 *
 * An attachment the user cannot open is an attachment they cannot check: it is their only way
 * to confirm that the thing the assistant is about to read, or has just read, is the thing
 * they meant to hand it. The web had this for images only until 2026-08-23 — every note, link
 * and contact chip was inert — so the rule is now pinned on both surfaces.
 *
 * The kinds differ on purpose, and the differences are what this checks:
 *
 * | Attachment | What opens |
 * |---|---|
 * | A photo | the picture itself, on a black ground |
 * | A note | its content on a light page, like the note editor |
 * | A link | the URL with an "Open link" action |
 * | A contact | the details |
 * | A file | nothing yet (owner's call) |
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VisualCheckAttachmentViewerTest : VisualCheckTestBase() {

    private fun show(
        attachment: AiApi.ChatAttachment,
        resources: List<ResourceItem> = emptyList(),
        shot: String,
    ) {
        compose.activityRule.scenario.onActivity { }
        compose.setContent {
            SpiraTheme {
                ChatAttachmentViewerContent(
                    attachment = attachment,
                    resources = resources,
                    onClose = {},
                )
            }
        }
        compose.waitForIdle()
        saveWindow(shot)
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun `a note attachment opens its content, not a blank page`() {
        show(
            attachment = AiApi.ChatAttachment("Interview notes", "", resourceId = 1L),
            resources = listOf(
                ResourceItem(id = "1", type = "note", title = "Interview notes", body = "They asked about salary."),
            ),
            shot = "attachment-viewer-note",
        )

        compose.onNodeWithText("Interview notes").assertIsDisplayed()
        compose.onNodeWithText("They asked about salary.").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun `a link attachment shows the URL and offers to open it`() {
        show(
            attachment = AiApi.ChatAttachment("Job board", "", resourceId = 2L),
            resources = listOf(
                ResourceItem(id = "2", type = "link", title = "Job board", url = "https://example.com/jobs"),
            ),
            shot = "attachment-viewer-link",
        )

        compose.onNodeWithText("https://example.com/jobs").assertIsDisplayed()
        // The URL alone would be a dead end — the action is what makes it reachable.
        compose.onNodeWithText("Open link").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun `a contact attachment shows the details rather than an empty card`() {
        show(
            attachment = AiApi.ChatAttachment("Recruiter", "", resourceId = 3L),
            resources = listOf(
                ResourceItem(
                    id = "3",
                    type = "email",
                    title = null,
                    name = "Ann Lee",
                    role = "Recruiter",
                    email = "ann@example.com",
                ),
            ),
            shot = "attachment-viewer-contact",
        )

        // The details render as ONE text node (name, role and address joined), so this has
        // to be a substring match — an exact one silently fails on a viewer that is working.
        compose.onNodeWithText("ann@example.com", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Ann Lee", substring = true).assertIsDisplayed()
    }
}
