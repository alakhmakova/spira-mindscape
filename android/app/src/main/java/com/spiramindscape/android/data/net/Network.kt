package com.spiramindscape.android.data.net

import android.content.Context
import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.network.okHttpClient
import com.spiramindscape.android.BuildConfig
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

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
        okHttp = OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .addInterceptor(CsrfInterceptor { cookieJar.value("XSRF-TOKEN") })
            .build()
        apollo = ApolloClient.Builder()
            .serverUrl("${BuildConfig.API_BASE_URL}/graphql")
            .okHttpClient(okHttp)
            .build()
    }
}
