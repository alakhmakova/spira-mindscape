package com.spiramindscape.android.push

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spiramindscape.android.data.push.PushManager
import com.spiramindscape.android.ui.auth.AuthState
import com.spiramindscape.android.ui.auth.AuthViewModel

/**
 * Ties push notifications to the auth lifecycle, kept in the UI layer so [AuthViewModel] stays
 * free of Firebase:
 *
 * <ul>
 *   <li>On becoming signed-in: request the notification permission (Android 13+) and register
 *       this device's FCM token with the backend (idempotent, so re-emits are cheap).</li>
 *   <li>On becoming signed-out: delete the local FCM token so the device stops receiving
 *       pushes (the backend prunes the stale token on its next failed send).</li>
 * </ul>
 */
@Composable
fun PushNotificationsEffect(authViewModel: AuthViewModel) {
    val state by authViewModel.state.collectAsStateWithLifecycle()

    // The permission only gates *showing* notifications; token registration works regardless,
    // so we request best-effort and don't block on the result.
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* granted or not — nothing else to do here */ }

    LaunchedEffect(state) {
        when (state) {
            is AuthState.Authed -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                PushManager.registerDevice()
            }
            AuthState.Anonymous -> PushManager.disableLocally()
            AuthState.Loading -> {}
        }
    }
}
