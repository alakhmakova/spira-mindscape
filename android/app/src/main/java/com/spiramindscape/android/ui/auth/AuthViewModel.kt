package com.spiramindscape.android.ui.auth

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.spiramindscape.android.BuildConfig
import com.spiramindscape.android.data.auth.AuthApi
import com.spiramindscape.android.data.auth.AuthClient
import com.spiramindscape.android.data.auth.AuthException
import com.spiramindscape.android.data.auth.AuthUser
import com.spiramindscape.android.data.auth.GoogleSignInClient
import com.spiramindscape.android.data.auth.IdTokenProvider
import com.spiramindscape.android.data.net.Network
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Coarse auth status the UI switches on. */
sealed interface AuthState {
    data object Loading : AuthState
    data object Anonymous : AuthState
    data class Authed(val user: AuthUser) : AuthState
}

/**
 * Owns the sign-in flow and the current session. On start it probes `/api/auth/me` (the cookie
 * jar may already hold a valid session from a previous launch). Sign-in gets a Google ID token
 * and posts it to the mobile endpoint; sign-out clears the server session and local cookies.
 */
class AuthViewModel(
    private val authClient: AuthClient,
    private val idTokenProvider: IdTokenProvider,
) : ViewModel() {

    private val _state = MutableStateFlow<AuthState>(AuthState.Loading)
    val state: StateFlow<AuthState> = _state.asStateFlow()

    private val _signingIn = MutableStateFlow(false)
    val signingIn: StateFlow<Boolean> = _signingIn.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        refresh()
    }

    /** Re-check the session (used on start and on app resume). */
    fun refresh() {
        viewModelScope.launch {
            try {
                // A genuine answer: a user (authed) or null (real 401 → anonymous). We must
                // honour null even when currently Authed, so an expired session returns the
                // user to login.
                val user = authClient.me()
                _state.value = if (user != null) AuthState.Authed(user) else AuthState.Anonymous
            } catch (e: Exception) {
                // Transient network/server error (not a 401): don't drop an active session.
                // Only fall back to Anonymous if we never established one (initial load).
                if (_state.value is AuthState.Loading) {
                    _state.value = AuthState.Anonymous
                }
            }
        }
    }

    fun signIn(activityContext: Context) {
        // Flip the guard synchronously (this runs on the main thread) so two rapid taps can't
        // both pass the check before the coroutine starts and launch two sign-in flows.
        if (_signingIn.value) return
        _signingIn.value = true
        _error.value = null
        viewModelScope.launch {
            try {
                val idToken = idTokenProvider.getIdToken(activityContext)
                val user = authClient.mobileLogin(idToken)
                _state.value = AuthState.Authed(user)
            } catch (e: GetCredentialCancellationException) {
                // User dismissed the Google sheet — not an error.
            } catch (e: AuthException) {
                // A token WAS obtained on-device, but the backend rejected it (audience,
                // email_verified, or signature). This is a server-side rejection.
                Log.w(TAG, "Mobile sign-in rejected by backend: HTTP ${e.code}", e)
                _error.value = "Sign-in failed (server ${e.code})."
            } catch (e: Exception) {
                // Failed before/without reaching the backend — Credential Manager on the
                // device (no Google account, SHA-1 / OAuth-client mismatch, Play Services).
                Log.w(TAG, "Google sign-in failed on device", e)
                _error.value = "Google sign-in failed. Please try again."
            } finally {
                _signingIn.value = false
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            try {
                authClient.logout()
            } catch (_: Exception) {
                // Best-effort; we clear locally regardless.
            }
            Network.cookieJar.clear()
            _state.value = AuthState.Anonymous
        }
    }

    companion object {
        private const val TAG = "AuthViewModel"

        /**
         * Production wiring: builds the real [AuthApi] (over the shared OkHttp client) and the
         * real Google sign-in. Tests construct [AuthViewModel] directly with fakes instead.
         */
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                AuthViewModel(
                    authClient = AuthApi(Network.okHttp, BuildConfig.API_BASE_URL),
                    idTokenProvider = GoogleSignInClient(BuildConfig.WEB_CLIENT_ID),
                )
            }
        }
    }
}
