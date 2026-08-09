package com.spiramindscape.android.data.net

import android.content.Context
import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.network.okHttpClient
import com.spiramindscape.android.BuildConfig
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor

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
}
