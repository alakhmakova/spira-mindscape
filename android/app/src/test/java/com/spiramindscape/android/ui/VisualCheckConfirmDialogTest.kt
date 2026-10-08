package com.spiramindscape.android.ui

import com.spiramindscape.android.ui.components.ConfirmDialogContent
import com.spiramindscape.android.ui.theme.SpiraTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * The delete confirm, rendered as pixels.
 *
 * `ConfirmDialogContent` rather than `ConfirmDialog`: a `Dialog` renders in its own window, which
 * the screenshot helper (it draws the activity's decor view) cannot capture — an open dialog is
 * simply absent from the PNG, exactly as an open `ModalBottomSheet` is.
 *
 * **What it is here to catch** is the emphasis on the name of the thing being deleted. That cannot
 * be asserted: the span either reads as a different thing from the sentence around it or it does
 * not. The web shipped bold-only first and the owner rejected it on sight — one weight step is all
 * the body face has, so the name also takes the full-strength ink against a message at 80% of it
 * (CLAUDE.md → Design → 3d-bis). The long case is the one that matters, because a target named
 * after a job advert carries the whole URL and the name is then longer than the sentence.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VisualCheckConfirmDialogTest : VisualCheckTestBase() {

    @Test
    fun `the deleted target's name stands out from the sentence around it`() {
        val name =
            "Apply varbi link https://profile.varbi.com/errors/notloggedin/" +
                "?redirect=/user/mypage/?type=position&cid=318"
        compose.activityRule.scenario.onActivity { }
        compose.setContent {
            SpiraTheme {
                ConfirmDialogContent(
                    title = "Delete this target?",
                    message = "\"$name\" will be permanently deleted. Progress and checklist " +
                        "tasks inside it will be removed. You can't undo this.",
                    onConfirm = {},
                    onDismiss = {},
                    confirmLabel = "Yes, delete",
                    subject = "\"$name\"",
                )
            }
        }
        compose.waitForIdle()
        saveWindow("confirm-delete-target")
    }

    @Test
    fun `a short goal name, the ordinary case`() {
        compose.activityRule.scenario.onActivity { }
        compose.setContent {
            SpiraTheme {
                ConfirmDialogContent(
                    title = "Delete this goal?",
                    message = "\"Get a job as a Software Developer\" will be permanently " +
                        "deleted. Targets, options and everything inside it will be removed. " +
                        "You can't undo this.",
                    onConfirm = {},
                    onDismiss = {},
                    confirmLabel = "Yes, delete",
                    subject = "\"Get a job as a Software Developer\"",
                )
            }
        }
        compose.waitForIdle()
        saveWindow("confirm-delete-goal")
    }
}
