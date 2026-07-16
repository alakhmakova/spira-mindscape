package com.spiramindscape.android.ui.auth

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.spiramindscape.android.data.auth.AuthClient
import com.spiramindscape.android.data.auth.AuthUser
import com.spiramindscape.android.data.auth.IdTokenProvider
import com.spiramindscape.android.data.net.Network
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * logout() touches the global cookie jar (via Network), so this runs under Robolectric with a
 * real context. It verifies logout ends the server session, clears local cookies, and returns
 * the user to Anonymous.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AuthViewModelLogoutTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        Network.init(ApplicationProvider.getApplicationContext<Context>())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        Network.cookieJar.clear()
    }

    @Test
    fun `logout clears the session and cookies and returns to Anonymous`() = runTest(dispatcher) {
        // Seed a session cookie as if we were logged in.
        Network.cookieJar.saveFromResponse(
            "https://example.com".toHttpUrl(),
            listOf(Cookie.Builder().name("SESSION").value("x").domain("example.com").path("/").build()),
        )

        var backendLoggedOut = false
        val client = object : AuthClient {
            override suspend fun me(): AuthUser = AuthUser(1L, "a@example.com", null, null)
            override suspend fun mobileLogin(idToken: String): AuthUser = me()
            override suspend fun logout() {
                backendLoggedOut = true
            }
        }
        val idTokens = object : IdTokenProvider {
            override suspend fun getIdToken(activityContext: Context): String = "t"
        }

        val vm = AuthViewModel(client, idTokens)
        advanceUntilIdle() // initial refresh() → Authed

        vm.logout()
        advanceUntilIdle()

        assertTrue(backendLoggedOut)
        assertEquals(AuthState.Anonymous, vm.state.value)
        assertNull(Network.cookieJar.value("SESSION"))
    }
}
