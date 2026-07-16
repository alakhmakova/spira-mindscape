package com.spiramindscape.android.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.spiramindscape.android.R
import com.spiramindscape.android.data.push.PushManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Receives FCM events.
 *
 * <ul>
 *   <li>{@link #onNewToken} — FCM rotated this device's token; re-register it with the backend
 *       (owner-scoped by the session cookie; a no-op 401 when signed out).</li>
 *   <li>{@link #onMessageReceived} — a message arrived while the app is in the foreground (or a
 *       data message); show it as a notification. Background <em>notification</em> messages are
 *       drawn by the system using the manifest's default channel/icon and don't reach here.</li>
 * </ul>
 */
class SpiraMessagingService : FirebaseMessagingService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        scope.launch { PushManager.registerToken(token) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val notification = message.notification ?: return
        showNotification(
            title = notification.title ?: getString(R.string.app_name),
            body = notification.body.orEmpty(),
        )
    }

    private fun showNotification(title: String, body: String) {
        val channelId = getString(R.string.default_notification_channel_id)
        ensureChannel(channelId)

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        val manager = NotificationManagerCompat.from(this)
        if (manager.areNotificationsEnabled()) {
            manager.notify(System.currentTimeMillis().toInt(), builder.build())
        }
    }

    private fun ensureChannel(channelId: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(channelId) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    channelId,
                    getString(R.string.default_notification_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
            )
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
