package com.spiramindscape.android.data.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/** The signed-in user, as returned by `/api/auth/me` and `/api/auth/google/mobile`. */
data class AuthUser(
    val id: Long,
    val email: String,
    val name: String?,
    val pictureUrl: String?,
)

/** Non-2xx (other than the 401 that `me()` maps to null) from an auth endpoint. */
class AuthException(val code: Int) : Exception("Auth request failed with HTTP $code")

/**
 * REST calls to the backend's auth endpoints. Uses the shared OkHttp client, so the session
 * cookie set by [mobileLogin] is reused by every later request (REST and GraphQL alike).
 */
class AuthApi(
    private val client: OkHttpClient,
    private val baseUrl: String,
) {
    private val json = "application/json".toMediaType()

    /** Exchange a Google ID token for a server session. Returns the user, or throws. */
    suspend fun mobileLogin(idToken: String): AuthUser = withContext(Dispatchers.IO) {
        val body = JSONObject().put("idToken", idToken).toString().toRequestBody(json)
        val request = Request.Builder()
            .url("$baseUrl/api/auth/google/mobile")
            .post(body)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw AuthException(response.code)
            parseUser(response.body?.string().orEmpty())
        }
    }

    /** Current session user, or null if anonymous (HTTP 401). */
    suspend fun me(): AuthUser? = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("$baseUrl/api/auth/me").get().build()
        client.newCall(request).execute().use { response ->
            when {
                response.isSuccessful -> parseUser(response.body?.string().orEmpty())
                response.code == 401 -> null
                else -> throw AuthException(response.code)
            }
        }
    }

    /** Invalidate the server session. Best-effort — the client also clears its own cookies. */
    suspend fun logout(): Unit = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/api/auth/logout")
            .post(ByteArray(0).toRequestBody(null))
            .build()
        client.newCall(request).execute().close()
    }

    private fun parseUser(payload: String): AuthUser {
        val o = JSONObject(payload)
        return AuthUser(
            id = o.optLong("id"),
            email = o.optString("email"),
            name = if (o.isNull("name")) null else o.optString("name"),
            pictureUrl = if (o.isNull("pictureUrl")) null else o.optString("pictureUrl"),
        )
    }
}
