package com.spiramindscape.android.data.net

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Verifies the double-submit CSRF rule end-to-end through OkHttp: the X-XSRF-TOKEN header is
 * attached to mutating requests when a token is available, and never to GETs.
 */
class CsrfInterceptorTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun clientWithToken(token: String?) =
        OkHttpClient.Builder().addInterceptor(CsrfInterceptor { token }).build()

    @Test
    fun `adds X-XSRF-TOKEN on a POST when a token exists`() {
        server.enqueue(MockResponse().setResponseCode(200))
        val request = Request.Builder().url(server.url("/graphql"))
            .post("{}".toRequestBody()).build()

        clientWithToken("tok123").newCall(request).execute().close()

        assertEquals("tok123", server.takeRequest().getHeader("X-XSRF-TOKEN"))
    }

    @Test
    fun `does not add the header on a GET`() {
        server.enqueue(MockResponse().setResponseCode(200))
        val request = Request.Builder().url(server.url("/api/auth/me")).get().build()

        clientWithToken("tok123").newCall(request).execute().close()

        assertNull(server.takeRequest().getHeader("X-XSRF-TOKEN"))
    }

    @Test
    fun `does not add the header when no token is available`() {
        server.enqueue(MockResponse().setResponseCode(200))
        val request = Request.Builder().url(server.url("/graphql"))
            .post("{}".toRequestBody()).build()

        clientWithToken(null).newCall(request).execute().close()

        assertNull(server.takeRequest().getHeader("X-XSRF-TOKEN"))
    }
}
