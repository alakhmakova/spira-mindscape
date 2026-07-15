package com.spiramindscape.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spiramindscape.android.data.auth.AuthUser
import com.spiramindscape.android.ui.auth.AuthState
import com.spiramindscape.android.ui.auth.AuthViewModel
import com.spiramindscape.android.ui.auth.LoginScreen

/**
 * Root of the app: shows a spinner while the session is being checked, the login screen when
 * anonymous, and the (placeholder) home when signed in. The goals dashboard replaces
 * [HomePlaceholder] in Step 4.
 */
@Composable
fun SpiraApp(authViewModel: AuthViewModel) {
    val state by authViewModel.state.collectAsStateWithLifecycle()
    val signingIn by authViewModel.signingIn.collectAsStateWithLifecycle()
    val error by authViewModel.error.collectAsStateWithLifecycle()
    val context = LocalContext.current

    when (val current = state) {
        AuthState.Loading -> LoadingScreen()
        AuthState.Anonymous -> LoginScreen(
            signingIn = signingIn,
            error = error,
            onSignIn = { authViewModel.signIn(context) },
        )
        is AuthState.Authed -> HomePlaceholder(
            user = current.user,
            onLogout = authViewModel::logout,
        )
    }
}

@Composable
private fun LoadingScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun HomePlaceholder(user: AuthUser, onLogout: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Signed in",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = user.email,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Goals dashboard comes next (Step 4).",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
        OutlinedButton(onClick = onLogout) {
            Text("Sign out")
        }
    }
}
