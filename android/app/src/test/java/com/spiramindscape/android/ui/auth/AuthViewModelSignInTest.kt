package com.spiramindscape.android.ui.auth

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.spiramindscape.android.data.auth.AuthClient
import com.spiramindscape.android.data.auth.AuthException
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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests the sign-in orchestration in the ViewModel (get token → exchange → Authed / error), with
 * fakes standing in for Credential Manager and the backend. The real `GoogleSignInClient` /
 * Credential Manager call needs a device + Google account and is covered by Maestro E2E instead.
 *
 * Robolectric is used because `signIn` takes a `Context` and the ViewModel touches the global
 * `Network`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AuthViewModelSignInTest {

    private val dispatcher = StandardTestDispatcher()
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        Network.init(context)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun idTokens(onGet: () -> Unit = {}) = object : IdTokenProvider {
        override suspend fun getIdToken(activityContext: Context): String {
            onGet()
            return "id-token"
        }
    }

    @Test
    fun `signIn exchanges the token and moves to Authed`() = runTest(dispatcher) {
        val user = AuthUser(1L, "a@example.com", "A", null)
        val client = object : AuthClient {
            override suspend fun me(): AuthUser? = null
            override suspend fun mobileLogin(idToken: String): AuthUser = user
            override suspend fun logout() {}
        }
        val vm = AuthViewModel(client, idTokens())
        advanceUntilIdle() // initial refresh() → Anonymous

        vm.signIn(context)
        advanceUntilIdle()

        assertEquals(AuthState.Authed(user), vm.state.value)
    }

    @Test
    fun `signIn surfaces an error and stays anonymous when the backend rejects the token`() =
        runTest(dispatcher) {
            val client = object : AuthClient {
                override suspend fun me(): AuthUser? = null
                override suspend fun mobileLogin(idToken: String): AuthUser = throw AuthException(401)
                override suspend fun logout() {}
            }
            val vm = AuthViewModel(client, idTokens())
            advanceUntilIdle()

            vm.signIn(context)
            advanceUntilIdle()

            assertTrue(vm.state.value is AuthState.Anonymous)
            assertNotNull(vm.error.value)
        }

    @Test
    fun `signIn ignores a second tap while one is in progress`() = runTest(dispatcher) {
        var tokenRequests = 0
        val client = object : AuthClient {
            override suspend fun me(): AuthUser? = null
            override suspend fun mobileLogin(idToken: String): AuthUser =
                AuthUser(1L, "a@example.com", null, null)
            override suspend fun logout() {}
        }
        val vm = AuthViewModel(client, idTokens(onGet = { tokenRequests++ }))
        advanceUntilIdle()

        vm.signIn(context) // sets _signingIn synchronously
        vm.signIn(context) // must be ignored
        advanceUntilIdle()

        assertEquals(1, tokenRequests)
    }
}
