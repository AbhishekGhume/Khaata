package com.khaata.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.khaata.app.ui.theme.Green
import com.khaata.app.ui.theme.Ink
import com.khaata.app.ui.theme.Muted
import com.khaata.app.ui.theme.Paper
import com.khaata.app.ui.theme.PaperCard
import com.khaata.app.ui.theme.Rust
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Email/password sign-in & sign-up. On success it doesn't navigate anywhere
 * itself — MainActivity is listening for Firebase's auth state and swaps to
 * the main app automatically once a user is signed in.
 */
@Composable
fun AuthScreen() {
    var isSignUp by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var infoMessage by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()
    val auth = remember { FirebaseAuth.getInstance() }

    fun submit() {
        errorMessage = null
        infoMessage = null
        val trimmedEmail = email.trim()
        if (trimmedEmail.isBlank() || password.isBlank()) {
            errorMessage = "Enter both email and password."
            return
        }
        if (isSignUp && password.length < 6) {
            errorMessage = "Password should be at least 6 characters."
            return
        }
        if (isSignUp && password != confirmPassword) {
            errorMessage = "Passwords don't match."
            return
        }
        isLoading = true
        scope.launch {
            try {
                if (isSignUp) {
                    auth.createUserWithEmailAndPassword(trimmedEmail, password).await()
                } else {
                    auth.signInWithEmailAndPassword(trimmedEmail, password).await()
                }
                // No manual navigation here \u2014 MainActivity's AuthStateListener
                // picks up the new signed-in user and swaps screens for us.
            } catch (e: Exception) {
                errorMessage = friendlyAuthError(e)
            } finally {
                isLoading = false
            }
        }
    }

    fun sendReset() {
        val trimmedEmail = email.trim()
        if (trimmedEmail.isBlank()) {
            errorMessage = "Type your email above first, then tap \u201cForgot password\u201d."
            return
        }
        errorMessage = null
        isLoading = true
        scope.launch {
            try {
                auth.sendPasswordResetEmail(trimmedEmail).await()
                infoMessage = "Password reset link sent to $trimmedEmail."
            } catch (e: Exception) {
                errorMessage = friendlyAuthError(e)
            } finally {
                isLoading = false
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(Ink).padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 360.dp)
                .fillMaxWidth()
                .background(PaperCard, RoundedCornerShape(14.dp))
                .padding(24.dp)
        ) {
            Text("Khaata", color = Ink, fontWeight = FontWeight.Bold, fontSize = 26.sp)
            Text(
                if (isSignUp) "Start your ledger" else "Welcome back to your ledger",
                color = Muted, fontSize = 13.sp,
                modifier = Modifier.padding(top = 2.dp, bottom = 18.dp)
            )

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = "Toggle password visibility"
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            if (isSignUp) {
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = { Text("Confirm password") },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            errorMessage?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, color = Rust, fontSize = 12.5.sp)
            }
            infoMessage?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, color = Green, fontSize = 12.5.sp)
            }

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { submit() },
                enabled = !isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = Ink, contentColor = Paper),
                modifier = Modifier.fillMaxWidth().height(46.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Paper, strokeWidth = 2.dp)
                } else {
                    Text(if (isSignUp) "Create account" else "Sign in")
                }
            }

            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = {
                    isSignUp = !isSignUp
                    errorMessage = null; infoMessage = null
                }) {
                    Text(if (isSignUp) "Already have an account? Sign in" else "New here? Create account", fontSize = 12.5.sp)
                }
            }
            if (!isSignUp) {
                Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = { sendReset() }) {
                        Text("Forgot password?", fontSize = 12.5.sp, color = Muted)
                    }
                }
            }
        }
    }
}

private fun friendlyAuthError(e: Exception): String = when (e) {
    is FirebaseAuthInvalidUserException -> "No account found with that email."
    is FirebaseAuthInvalidCredentialsException -> "That email or password looks wrong."
    is FirebaseAuthUserCollisionException -> "An account with that email already exists \u2014 try signing in instead."
    is FirebaseAuthWeakPasswordException -> "That password is too weak \u2014 use at least 6 characters."
    else -> e.localizedMessage ?: "Something went wrong. Please try again."
}