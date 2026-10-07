package com.example.fintrack.ui

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.example.fintrack.util.Prefs

fun canBio(a: FragmentActivity) =
    BiometricManager.from(a).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS

fun showBio(a: FragmentActivity, onOk: () -> Unit) {
    val bp = BiometricPrompt(a, ContextCompat.getMainExecutor(a), object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onOk()
    })
    bp.authenticate(
        BiometricPrompt.PromptInfo.Builder().setTitle("Unlock FinTrack")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
            .setNegativeButtonText("Use PIN").build()
    )
}

@Composable
fun LockScreen(a: FragmentActivity, prefs: Prefs, onUnlock: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var bad by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { if (prefs.bioOn && canBio(a)) showBio(a, onUnlock) }
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("FinTrack locked", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(pin, { pin = it.filter(Char::isDigit).take(12); bad = false }, label = { Text("PIN") }, singleLine = true, isError = bad,
            visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
        Spacer(Modifier.height(12.dp))
        Button(onClick = { if (prefs.checkPin(pin)) onUnlock() else { bad = true; pin = "" } }) { Text("Unlock") }
        if (prefs.bioOn && canBio(a)) TextButton(onClick = { showBio(a, onUnlock) }) { Icon(Icons.Filled.Fingerprint, null); Spacer(Modifier.width(8.dp)); Text("Use biometrics") }
    }
}
