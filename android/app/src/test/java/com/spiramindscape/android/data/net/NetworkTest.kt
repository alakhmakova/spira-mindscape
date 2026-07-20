package com.spiramindscape.android.data.net

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.spiramindscape.android.BuildConfig
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
}
