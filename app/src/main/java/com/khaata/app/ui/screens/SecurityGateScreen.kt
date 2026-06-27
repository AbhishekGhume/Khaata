package com.khaata.app.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.biometric.BiometricPrompt.PromptInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.khaata.app.ui.theme.Gold
import com.khaata.app.ui.theme.Green
import com.khaata.app.ui.theme.Ink
import com.khaata.app.ui.theme.Muted
import com.khaata.app.ui.theme.Paper
import com.khaata.app.ui.theme.PaperCard
import com.khaata.app.ui.theme.PaperLine

@Composable
fun SecurityGateScreen(
    onUnlocked: () -> Unit,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val unlockHandler by rememberUpdatedState(onUnlocked)
    val signOutHandler by rememberUpdatedState(onSignOut)
    val focusManager = LocalFocusManager.current

    val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
    val canAuthenticate = remember(context) {
        BiometricManager.from(context).canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS
    }

    var errorMessage by remember { mutableStateOf<String?>(null) }
    var autoPrompted by remember { mutableStateOf(false) }

    val promptInfo = remember {
        PromptInfo.Builder()
            .setTitle("Unlock Khaata")
            .setSubtitle("Use your phone's PIN, pattern, password, or biometrics.")
            .setAllowedAuthenticators(authenticators)
            .build()
    }

    val prompt = remember(activity) {
        activity?.let {
            val executor = ContextCompat.getMainExecutor(it)
            BiometricPrompt(it, executor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    errorMessage = null
                    unlockHandler()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    errorMessage = errString.toString()
                }

                override fun onAuthenticationFailed() {
                    errorMessage = "Authentication failed. Try again."
                }
            })
        }
    }

    fun launchPrompt() {
        focusManager.clearFocus(force = true)
        if (activity == null || prompt == null) {
            errorMessage = "This screen needs a FragmentActivity host."
            return
        }
        prompt.authenticate(promptInfo)
    }

    LaunchedEffect(canAuthenticate) {
        if (canAuthenticate && !autoPrompted) {
            autoPrompted = true
            launchPrompt()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = PaperCard,
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 2.dp,
            shadowElevation = 2.dp,
            modifier = Modifier.widthIn(max = 420.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Security check", color = Ink, fontWeight = FontWeight.Bold, fontSize = 24.sp)
                Text(
                    "Khaata stays locked until you unlock it with your phone's PIN or biometrics.",
                    color = Muted,
                    fontSize = 13.sp,
                    modifier = Modifier.fillMaxWidth()
                )

                if (canAuthenticate) {
                    Button(
                        onClick = { launchPrompt() },
                        colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Ink),
                        modifier = Modifier.fillMaxWidth().height(46.dp)
                    ) {
                        Text("Unlock")
                    }
                } else {
                    Text(
                        "No secure screen lock is available on this device. Set up a phone PIN, pattern, password, or fingerprint in system settings first.",
                        color = Muted,
                        fontSize = 12.5.sp
                    )
                    Button(
                        onClick = {
                            context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Paper),
                        modifier = Modifier.fillMaxWidth().height(46.dp)
                    ) {
                        Text("Open security settings")
                    }
                }

                errorMessage?.let {
                    Text(it, color = com.khaata.app.ui.theme.Rust, fontSize = 12.5.sp)
                }

                TextButton(onClick = signOutHandler) {
                    Text("Sign out", fontSize = 12.5.sp)
                }
                Spacer(Modifier.height(2.dp))
            }
        }
    }
}