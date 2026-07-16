package com.spiramindscape.android.data.push

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * REST calls to the backend's FCM device-token endpoints. Uses the shared OkHttp client, so the
 * session cookie + CSRF header flow through exactly like the auth and GraphQL calls.
 *
 * <p>These are best-effort: registration is idempotent (the backend upserts), and a call made
 * while signed out just returns 401, which we ignore. The methods return the HTTP status so
 * callers/tests can inspect the outcome.
 */
class PushApi(
    private val client: OkHttpClient,
    private val baseUrl: String,
) {
    private val json = "application/json".toMediaType()

    /** Register (or refresh) this device's FCM token for the signed-in user. */
    suspend fun register(token: String, platform: String = "android"): Int =
        post("/api/push/register", token, platform)

    /** Remove this device's token from the signed-in user. */
    suspend fun unregister(token: String): Int =
        post("/api/push/unregister", token, null)

    private suspend fun post(path: String, token: String, platform: String?): Int =
        withContext(Dispatchers.IO) {
            val payload = JSONObject().put("token", token)
            if (platform != null) payload.put("platform", platform)
            val request = Request.Builder()
                .url("$baseUrl$path")
                .post(payload.toString().toRequestBody(json))
                .build()
            client.newCall(request).execute().use { response -> response.code }
        }
}
