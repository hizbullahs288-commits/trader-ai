package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.firebase.configureAppCheck
import com.example.ui.TradePulseApp
import com.example.ui.screens.AuthScreen
import com.example.ui.theme.TradePulseTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configure Firebase App Check before UI, ViewModels, or Firebase operations.
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        setContent {
            TradePulseTheme {
                AppRoot()
            }
        }
    }
}

@Composable
private fun AppRoot(auth: FirebaseAuth = Firebase.auth) {
    var currentUser by remember { mutableStateOf(auth.currentUser) }
    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            currentUser = firebaseAuth.currentUser
        }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }

    val user = currentUser
    if (user == null) {
        AuthScreen(
            onAuthSuccess = { currentUser = auth.currentUser }
        )
    } else {
        key(user.uid) {
            TradePulseApp(
                currentUserId = user.uid,
                userEmail = user.email,
                onSignedOut = { currentUser = null }
            )
        }
    }
}
