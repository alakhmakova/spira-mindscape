package com.spiramindscape.android.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spiramindscape.android.ui.auth.AuthState
import com.spiramindscape.android.ui.auth.AuthViewModel
import com.spiramindscape.android.ui.auth.LoginScreen
import com.spiramindscape.android.ui.goals.GoalsRoute

/**
 * Root of the app: a spinner while the session is checked, the login screen when anonymous, and
 * the goals dashboard when signed in.
 */
@Composable
fun SpiraApp(authViewModel: AuthViewModel) {
    val state by authViewModel.state.collectAsStateWithLifecycle()
    val signingIn by authViewModel.signingIn.collectAsStateWithLifecycle()
    val error by authViewModel.error.collectAsStateWithLifecycle()
    val context = LocalContext.current

    when (state) {
        AuthState.Loading -> LoadingScreen()
        AuthState.Anonymous -> LoginScreen(
            signingIn = signingIn,
            error = error,
            onSignIn = { authViewModel.signIn(context) },
        )
        is AuthState.Authed -> GoalsRoute(onLogout = authViewModel::logout)
    }
}

@Composable
private fun LoadingScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}
