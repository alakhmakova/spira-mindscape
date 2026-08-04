package com.spiramindscape.android.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.spiramindscape.android.ui.theme.Salt600
import com.spiramindscape.android.ui.theme.spiraExtras

/**
 * Anonymous state — the same screen the web shows on a phone (`src/routes/login.tsx`, the
 * `max-width: 820px` branch): the teal **spira** wordmark pinned to the top-left, and a centred
 * block with a serif "Sign in to Spira", a muted line under it, the white outlined
 * **Continue with Google** button, and the legal line with underlined links.
 *
 * The brand (teal) panel that sits beside it on desktop is deliberately absent here, exactly as
 * the web hides it below 820px.
 */
@Composable
fun LoginScreen(
    signingIn: Boolean,
    error: String?,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        // The wordmark is pinned to the page's top-left corner; the block below stays centred on
        // the whole screen, not on the space left under the logo.
        Text(
            "spira",
            modifier = Modifier.align(Alignment.TopStart).padding(start = 20.dp, top = 20.dp),
            // The wordmark is set in the body sans (GCentra), not the headline serif.
            style = MaterialTheme.typography.bodyLarge,
            fontSize = 30.sp,
            lineHeight = 32.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.01).em,
            color = MaterialTheme.colorScheme.primary,
        )

        Column(
            // Panel padding first, then the block's own max width — the same 32px gutter and
            // 360px block the web uses, so the button lands at the same width on a phone.
            Modifier
                .align(Alignment.Center)
                .padding(horizontal = 32.dp)
                .widthIn(max = 360.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Sign in to Spira",
                style = MaterialTheme.typography.headlineMedium,
                fontSize = 28.sp,
                lineHeight = 31.sp,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Use your Google account to continue.",
                style = MaterialTheme.typography.bodyMedium,
                fontSize = 15.sp,
                color = MaterialTheme.spiraExtras.mutedForeground,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(28.dp))
            GoogleButton(signingIn = signingIn, onClick = onSignIn)

            if (error != null) {
                Spacer(Modifier.height(16.dp))
                Text(
                    error,
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(28.dp))
            LegalLine()
        }
    }
}

/** The white outlined Google button: a 52dp-tall pill-less card with the multicolour G. */
@Composable
private fun GoogleButton(signingIn: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.spiraExtras.surfaceRaised)
            .border(1.dp, Salt600, RoundedCornerShape(10.dp))
            .clickable(enabled = !signingIn, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (signingIn) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    GoogleG,
                    contentDescription = null,
                    tint = Color.Unspecified, // the mark carries its own four brand colours
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    "Continue with Google",
                    style = MaterialTheme.typography.bodyLarge,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }
    }
}

/** "By continuing, you agree to Spira's Terms and Privacy Policy." — the two names underlined. */
@Composable
private fun LegalLine() {
    val muted = MaterialTheme.spiraExtras.mutedForeground
    val strong = MaterialTheme.colorScheme.onBackground
    Text(
        buildAnnotatedString {
            append("By continuing, you agree to Spira's ")
            withStyle(SpanStyle(color = strong, textDecoration = TextDecoration.Underline)) {
                append("Terms")
            }
            append(" and ")
            withStyle(SpanStyle(color = strong, textDecoration = TextDecoration.Underline)) {
                append("Privacy Policy")
            }
            append(".")
        },
        style = MaterialTheme.typography.bodySmall,
        fontSize = 12.5.sp,
        lineHeight = 19.sp,
        // Brand rule: body tracking is 0. The Material default (0.4sp) widens this sentence by
        // ~20dp, which is exactly enough to wrap it onto a second line the web keeps on one.
        letterSpacing = 0.sp,
        color = muted,
        textAlign = TextAlign.Center,
    )
}

/**
 * Google's "G" mark, the same four-path SVG the web login uses. Not a Lucide icon (it is a brand
 * logo, drawn in Google's own colours), so it lives here rather than in `SpiraIcons`.
 */
private val GoogleG: ImageVector = ImageVector.Builder(
    name = "GoogleG",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    addPath(
        pathData = addPathNodes(
            "M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57" +
                "c2.08-1.92 3.28-4.74 3.28-8.09z",
        ),
        fill = SolidColor(Color(0xFF4285F4)),
    )
    addPath(
        pathData = addPathNodes(
            "M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53" +
                "H2.18v2.84C3.99 20.53 7.7 23 12 23z",
        ),
        fill = SolidColor(Color(0xFF34A853)),
    )
    addPath(
        pathData = addPathNodes(
            "M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12" +
                "s.43 3.45 1.18 4.93l2.85-2.22.81-.62z",
        ),
        fill = SolidColor(Color(0xFFFBBC05)),
    )
    addPath(
        pathData = addPathNodes(
            "M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07" +
                "l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z",
        ),
        fill = SolidColor(Color(0xFFEA4335)),
    )
}.build()
