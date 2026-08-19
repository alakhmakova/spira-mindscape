package com.spiramindscape.android.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay

/**
 * What happened, which decides only the glyph and its colour — never the card or the type.
 *
 * The same four kinds an inline notice takes, because a toast and a notice inside a block are the
 * one card (see [SpiraNoticeCard]).
 */
typealias SpiraToastKind = SpiraNoticeKind

/**
 * Spira's toast — the owner's reference (2026-08-17, re-confirmed 2026-08-18), and the twin of the
 * web's sonner styling (`src/components/ui/sonner.tsx`).
 *
 * The card itself is [SpiraNoticeCard]; a toast is that card floating over the page and dismissing
 * itself after [durationMs]. The X is for getting rid of it sooner.
 */
@Composable
fun SpiraToast(
    message: String?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    kind: SpiraToastKind = SpiraToastKind.Success,
    durationMs: Long = 3_500,
) {
    LaunchedEffect(message) {
        if (message != null) {
            delay(durationMs)
            onDismiss()
        }
    }
    AnimatedVisibility(
        visible = message != null,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
        modifier = modifier,
    ) {
        val shown = message ?: return@AnimatedVisibility
        SpiraNoticeCard(message = shown, kind = kind, onDismiss = onDismiss)
    }
}
