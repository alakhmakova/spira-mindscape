package com.spiramindscape.android.data.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

/**
 * Wraps Android's Credential Manager "Sign in with Google". Returns a Google **ID token** whose
 * audience is the project's web client ID ([webClientId]) — the backend verifies against that
 * same ID at `POST /api/auth/google/mobile`.
 *
 * Must be called with an **Activity** context (Credential Manager shows UI).
 */
class GoogleSignInClient(private val webClientId: String) {

    suspend fun getIdToken(activityContext: Context): String {
        val googleIdOption = GetGoogleIdOption.Builder()
            .setServerClientId(webClientId)
            // Show all Google accounts on the device, not only previously-authorized ones.
            .setFilterByAuthorizedAccounts(false)
            .setAutoSelectEnabled(false)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        val result = CredentialManager.create(activityContext).getCredential(activityContext, request)
        val credential = result.credential

        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            return GoogleIdTokenCredential.createFrom(credential.data).idToken
        }
        throw IllegalStateException("Unexpected credential type: ${credential.type}")
    }
}
