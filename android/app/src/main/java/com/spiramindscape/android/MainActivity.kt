package com.spiramindscape.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.spiramindscape.android.data.net.Network
import com.spiramindscape.android.ui.SpiraApp
import com.spiramindscape.android.ui.auth.AuthViewModel
import com.spiramindscape.android.ui.theme.SpiraTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // One-time init of the shared OkHttp + Apollo clients (cookie jar, CSRF header).
        Network.init(applicationContext)
        enableEdgeToEdge()
        setContent {
            SpiraTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val authViewModel: AuthViewModel = viewModel()
                    SpiraApp(authViewModel)
                }
            }
        }
    }
}
