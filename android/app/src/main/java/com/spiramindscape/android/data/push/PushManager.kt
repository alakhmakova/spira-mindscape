package com.spiramindscape.android.data.push

import com.google.firebase.messaging.FirebaseMessaging
import com.spiramindscape.android.core.SpiraLog
import com.spiramindscape.android.data.net.Network
import kotlinx.coroutines.tasks.await

/**
 * Bridges Firebase Cloud Messaging and our backend. Kept out of the ViewModels so they stay
 * unit-testable without Firebase/Play Services on the JVM.
 *
 * <p>Everything here is best-effort and guarded: on a device without Google Play Services (or
 * without google-services.json) the Firebase calls throw, and we simply log and move on — the
 * app works fine without push.
 */
object PushManager {

    private const val TAG = "PushManager"

    private val api by lazy { PushApi(Network.okHttp, Network.baseUrl) }

    /** Fetch this device's current FCM token and register it for the signed-in user. */
    suspend fun registerDevice() {
        try {
            val token = FirebaseMessaging.getInstance().token.await()
            api.register(token)
        } catch (e: Exception) {
            SpiraLog.w(TAG, "fcm_registration_skipped", e)
        }
    }

    /** Register a token the messaging service just received (on rotation). */
    suspend fun registerToken(token: String) {
        try {
            api.register(token)
        } catch (e: Exception) {
            SpiraLog.w(TAG, "fcm_token_registration_failed", e)
        }
    }

    /**
     * Stop this device from receiving pushes after sign-out. Deleting the FCM token invalidates
     * it, so the backend's stored token goes stale and is pruned the next time a send fails
     * ({@code UNREGISTERED}). This needs no session, so it's safe to call after logout clears
     * the cookies.
     */
    suspend fun disableLocally() {
        try {
            FirebaseMessaging.getInstance().deleteToken().await()
        } catch (e: Exception) {
            SpiraLog.w(TAG, "fcm_token_deletion_failed", e)
        }
    }
}
