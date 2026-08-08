package com.spiramindscape.android.data.net

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.spiramindscape.android.BuildConfig
import okhttp3.logging.HttpLoggingInterceptor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Verifies the shared network holder initialises its clients once and is idempotent.
 */
@RunWith(RobolectricTestRunner::class)
class NetworkTest {

    @Test
    fun `init builds the clients and is idempotent`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        Network.init(context)

        assertNotNull(Network.cookieJar)
        assertNotNull(Network.okHttp)
        assertNotNull(Network.apollo)
        assertEquals(BuildConfig.API_BASE_URL, Network.baseUrl)

        val apollo = Network.apollo
        val okHttp = Network.okHttp

        // A second init must not rebuild the clients.
        Network.init(context)
        assertSame(apollo, Network.apollo)
        assertSame(okHttp, Network.okHttp)
    }

    @Test
    fun `debug HTTP logging never logs bodies`() {
        // Request and response bodies carry goal text, note contents and whole AI
        // conversations, and logcat is readable by anything with adb. BASIC logs one line
        // per call; BODY would put the user's journal on the wire. This guards against
        // someone flipping the level "just to debug something" and shipping it.
        Network.init(ApplicationProvider.getApplicationContext<Context>())

        val logging = Network.okHttp.interceptors.filterIsInstance<HttpLoggingInterceptor>()
        if (BuildConfig.DEBUG) {
            assertEquals("debug builds should log HTTP calls", 1, logging.size)
            assertEquals(HttpLoggingInterceptor.Level.BASIC, logging.single().level)
        } else {
            assertEquals("release builds must not log HTTP at all", 0, logging.size)
        }
    }
}
