package com.spiramindscape.android.data.net

import okhttp3.Interceptor
import okhttp3.Response

/**
 * The backend protects mutating requests with a double-submit CSRF token: it sets a readable
 * `XSRF-TOKEN` cookie, and every non-GET request must echo it back as the `X-XSRF-TOKEN`
 * header. This interceptor does that automatically for the whole app (GraphQL mutations and
 * REST posts alike), mirroring what the web client does in `src/lib/spira/auth.ts`.
 */
class CsrfInterceptor(private val cookieJar: PersistentCookieJar) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val isMutating = request.method != "GET" && request.method != "HEAD"
        val token = cookieJar.value("XSRF-TOKEN")
        val outgoing = if (isMutating && token != null && request.header("X-XSRF-TOKEN") == null) {
            request.newBuilder().header("X-XSRF-TOKEN", token).build()
        } else {
            request
        }
        return chain.proceed(outgoing)
    }
}
