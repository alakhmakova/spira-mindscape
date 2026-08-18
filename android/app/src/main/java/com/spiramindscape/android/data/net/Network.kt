package com.spiramindscape.android.data.net

import android.content.Context
import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.network.okHttpClient
import com.spiramindscape.android.BuildConfig
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

/**
 * App-wide HTTP + GraphQL clients. A single OkHttp client (with the cookie jar + CSRF header)
 * is shared by the REST auth calls and Apollo, so the session cookie flows through both.
 *
 * Call [init] once from the Activity/Application before anything reads these.
 */
object Network {

    lateinit var cookieJar: PersistentCookieJar
        private set
    lateinit var okHttp: OkHttpClient
        private set
    lateinit var apollo: ApolloClient
        private set

    val baseUrl: String get() = BuildConfig.API_BASE_URL

    fun init(context: Context) {
        if (::apollo.isInitialized) return

        cookieJar = PersistentCookieJar(context, BuildConfig.API_BASE_URL.toHttpUrl())
        val builder = OkHttpClient.Builder()
            .cookieJar(cookieJar)
            // **Timeouts have to be set, not left to OkHttp** (BUG-040). The backend runs on Cloud
            // Run with no minimum instance, so the first request after it has scaled to zero waits
            // for a JVM cold start — measured at about eleven seconds in production. OkHttp's
            // default read timeout is **ten**, so that first call lost the race by a second: the
            // phone reported "Google sign-in failed. Please try again." while the server logged
            // `auth_signin_success` for the very same request.
            //
            // [READ_TIMEOUT_SECONDS] is generous enough to cover a cold start several times over,
            // and [CALL_TIMEOUT_SECONDS] is the hard cap that stops anything hanging for ever —
            // the pair matters, because a long read timeout on its own turns a dead network into a
            // frozen screen. The AI chat stream overrides both to "no limit" on its own client
            // (`AiApi.streamClient`), which is the one place an unbounded wait is correct.
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .addInterceptor(CsrfInterceptor { cookieJar.value("XSRF-TOKEN") })
        if (BuildConfig.DEBUG) {
            // Debug builds only: there was previously no visibility into HTTP at all, so a
            // failing call looked identical to a bug in the UI.
            //
            // BASIC, never BODY. Request and response bodies carry goal text, note contents
            // and whole AI conversations — logcat is readable by anything with adb, so a
            // body-level log would put the user's journal on the wire. The redactions cover
            // the session cookie and the CSRF token for the same reason.
            builder.addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                    redactHeader("Cookie")
                    redactHeader("Set-Cookie")
                    redactHeader("Authorization")
                    redactHeader("X-XSRF-TOKEN")
                },
            )
        }
        okHttp = builder.build()
        apollo = ApolloClient.Builder()
            .serverUrl("${BuildConfig.API_BASE_URL}/graphql")
            .okHttpClient(okHttp)
            .build()
    }

    /** Opening the socket. Short: a network that cannot be reached should say so quickly. */
    private const val CONNECT_TIMEOUT_SECONDS = 20L

    /** Waiting for the server to answer — sized for a Cloud Run cold start, not for a warm call. */
    private const val READ_TIMEOUT_SECONDS = 45L

    /** Sending the request body. */
    private const val WRITE_TIMEOUT_SECONDS = 30L

    /** The whole call, redirects and retries included: nothing waits longer than this. */
    private const val CALL_TIMEOUT_SECONDS = 60L
}
