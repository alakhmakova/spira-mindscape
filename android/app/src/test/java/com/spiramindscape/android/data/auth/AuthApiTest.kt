package com.spiramindscape.android.data.auth

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Exercises the REST auth client against a MockWebServer (no Android runtime): request shape,
 * JSON parsing, and status-code handling (200 / 401 → null / other → AuthException).
 */
class AuthApiTest {

    private lateinit var server: MockWebServer
    private lateinit var api: AuthApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = AuthApi(OkHttpClient(), server.url("/").toString().trimEnd('/'))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `mobileLogin posts the token and parses the user on 200`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody("""{"id":7,"email":"alice@example.com","name":"Alice","pictureUrl":null}"""),
        )

        val user = api.mobileLogin("id-token-123")

        assertEquals(7L, user.id)
        assertEquals("alice@example.com", user.email)
        assertEquals("Alice", user.name)
        assertNull(user.pictureUrl)

        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/api/auth/google/mobile", recorded.path)
        assertTrue(recorded.body.readUtf8().contains("id-token-123"))
    }

    @Test
    fun `mobileLogin throws AuthException with the status code on rejection`() {
        server.enqueue(MockResponse().setResponseCode(401))
        val e = assertThrows(AuthException::class.java) { runBlocking { api.mobileLogin("bad") } }
        assertEquals(401, e.code)
    }

    @Test
    fun `me returns the user on 200`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody("""{"id":1,"email":"m@example.com","name":null,"pictureUrl":null}"""),
        )
        val user = api.me()
        assertEquals("m@example.com", user?.email)
        assertNull(user?.name)
    }

    @Test
    fun `me returns null on 401 (anonymous)`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401))
        assertNull(api.me())
    }

    @Test
    fun `me throws AuthException on a server error`() {
        server.enqueue(MockResponse().setResponseCode(500))
        val e = assertThrows(AuthException::class.java) { runBlocking { api.me() } }
        assertEquals(500, e.code)
    }
}
