package com.spiramindscape.android.ui.auth

import android.content.Context
import com.spiramindscape.android.data.auth.AuthClient
import com.spiramindscape.android.data.auth.AuthUser
import com.spiramindscape.android.data.auth.IdTokenProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

/**
 * Unit tests for the auth state machine. The ViewModel takes its collaborators as interfaces,
 * so we drive it with fakes — no Android runtime, no network. These lock in the session logic,
 * including the "expired session returns to login, but a transient error does not" rule.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val alice = AuthUser(1L, "alice@example.com", "Alice", null)

    /** Fake auth client whose `me()` result can be swapped between calls. */
    private inner class FakeAuthClient(var meResult: suspend () -> AuthUser?) : AuthClient {
        override suspend fun me(): AuthUser? = meResult()
        override suspend fun mobileLogin(idToken: String): AuthUser = alice
        override suspend fun logout() {}
    }

    private class NoopIdTokenProvider : IdTokenProvider {
        override suspend fun getIdToken(activityContext: Context): String = "token"
    }

    private fun viewModel(client: AuthClient) = AuthViewModel(client, NoopIdTokenProvider())

    @Test
    fun `starts Authed when a session already exists`() = runTest(dispatcher) {
        val vm = viewModel(FakeAuthClient(meResult = { alice }))
        advanceUntilIdle()
        assertEquals(AuthState.Authed(alice), vm.state.value)
    }

    @Test
    fun `starts Anonymous when there is no session`() = runTest(dispatcher) {
        val vm = viewModel(FakeAuthClient(meResult = { null }))
        advanceUntilIdle()
        assertEquals(AuthState.Anonymous, vm.state.value)
    }

    @Test
    fun `refresh returns an authed user to login when the session expired (genuine 401)`() =
        runTest(dispatcher) {
            val client = FakeAuthClient(meResult = { alice })
            val vm = viewModel(client)
            advanceUntilIdle()
            assertTrue(vm.state.value is AuthState.Authed)

            client.meResult = { null } // session no longer valid → me() returns null (401)
            vm.refresh()
            advanceUntilIdle()

            assertEquals(AuthState.Anonymous, vm.state.value)
        }

    @Test
    fun `refresh keeps an authed session on a transient error`() = runTest(dispatcher) {
        val client = FakeAuthClient(meResult = { alice })
        val vm = viewModel(client)
        advanceUntilIdle()

        client.meResult = { throw IOException("network down") } // transient, not a 401
        vm.refresh()
        advanceUntilIdle()

        assertTrue(vm.state.value is AuthState.Authed)
    }
}
