package com.spiramindscape.android.ui.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.spiramindscape.android.BuildConfig
import com.spiramindscape.android.data.auth.AuthApi
import com.spiramindscape.android.data.auth.AuthUser
import com.spiramindscape.android.data.auth.GoogleSignInClient
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
class AuthViewModel : ViewModel() {

    private val authApi = AuthApi(Network.okHttp, BuildConfig.API_BASE_URL)
    private val googleSignIn = GoogleSignInClient(BuildConfig.WEB_CLIENT_ID)

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
                val user = authApi.me()
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
                val idToken = googleSignIn.getIdToken(activityContext)
                val user = authApi.mobileLogin(idToken)
                _state.value = AuthState.Authed(user)
            } catch (e: GetCredentialCancellationException) {
                // User dismissed the Google sheet — not an error.
            } catch (e: Exception) {
                _error.value = "Sign-in failed. Please try again."
            } finally {
                _signingIn.value = false
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            try {
                authApi.logout()
            } catch (_: Exception) {
                // Best-effort; we clear locally regardless.
            }
            Network.cookieJar.clear()
            _state.value = AuthState.Anonymous
        }
    }
}
