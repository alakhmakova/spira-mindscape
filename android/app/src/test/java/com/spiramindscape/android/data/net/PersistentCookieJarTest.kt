package com.spiramindscape.android.data.net

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests the SharedPreferences-backed cookie jar with a real Android context (Robolectric):
 * save/load, name lookup, persistence across instances (app restarts), and clear (logout).
 */
@RunWith(RobolectricTestRunner::class)
class PersistentCookieJarTest {

    private val baseUrl = "https://example.com".toHttpUrl()
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    private fun newJar() = PersistentCookieJar(context, baseUrl)

    private fun cookie(name: String, value: String): Cookie =
        Cookie.Builder().name(name).value(value).domain("example.com").path("/").build()

    @Test
    fun `saves cookies and loads them for a matching url`() {
        val jar = newJar()
        jar.saveFromResponse(baseUrl, listOf(cookie("SESSION", "abc"), cookie("XSRF-TOKEN", "tok")))

        val loaded = jar.loadForRequest(baseUrl).associate { it.name to it.value }

        assertEquals("abc", loaded["SESSION"])
        assertEquals("tok", loaded["XSRF-TOKEN"])
    }

    @Test
    fun `value looks a cookie up by name`() {
        val jar = newJar()
        jar.saveFromResponse(baseUrl, listOf(cookie("XSRF-TOKEN", "tok")))

        assertEquals("tok", jar.value("XSRF-TOKEN"))
        assertNull(jar.value("NOPE"))
    }

    @Test
    fun `cookies persist across instances (survive an app restart)`() {
        newJar().saveFromResponse(baseUrl, listOf(cookie("SESSION", "persist-me")))

        // A brand-new jar reloads from SharedPreferences, like the next app launch.
        val reloaded = PersistentCookieJar(context, baseUrl)

        assertEquals("persist-me", reloaded.value("SESSION"))
    }

    @Test
    fun `clear removes everything, in memory and persisted`() {
        val jar = newJar()
        jar.saveFromResponse(baseUrl, listOf(cookie("SESSION", "abc")))

        jar.clear()

        assertNull(jar.value("SESSION"))
        assertTrue(jar.loadForRequest(baseUrl).isEmpty())
        // A fresh instance also sees nothing (prefs were wiped).
        assertNull(PersistentCookieJar(context, baseUrl).value("SESSION"))
    }
}
