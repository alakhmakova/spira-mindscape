package com.spiramindscape.android.data.push

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Exercises the FCM device-token REST client against a MockWebServer (no Android runtime):
 * request path/method/body for register + unregister, and status pass-through.
 */
class PushApiTest {

    private lateinit var server: MockWebServer
    private lateinit var api: PushApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = PushApi(OkHttpClient(), server.url("/").toString().trimEnd('/'))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `register posts token and platform to the register endpoint`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(204))

        val code = api.register("fcm-token-abc")

        assertEquals(204, code)
        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/api/push/register", recorded.path)
        val body = JSONObject(recorded.body.readUtf8())
        assertEquals("fcm-token-abc", body.getString("token"))
        assertEquals("android", body.getString("platform"))
    }

    @Test
    fun `unregister posts only the token to the unregister endpoint`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(204))

        val code = api.unregister("fcm-token-abc")

        assertEquals(204, code)
        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/api/push/unregister", recorded.path)
        val body = JSONObject(recorded.body.readUtf8())
        assertEquals("fcm-token-abc", body.getString("token"))
        assertEquals(false, body.has("platform"))
    }

    @Test
    fun `register returns the status code so callers can ignore a 401 when signed out`() =
        runBlocking {
            server.enqueue(MockResponse().setResponseCode(401))
            assertEquals(401, api.register("fcm-token-abc"))
        }
}
