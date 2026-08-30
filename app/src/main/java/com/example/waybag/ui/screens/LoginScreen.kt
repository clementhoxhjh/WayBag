package com.example.waybag.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.waybag.ui.components.*
import com.example.waybag.ui.theme.Apple_Blue
import com.example.waybag.ui.theme.Premium_Gray
import com.example.waybag.data.supabase
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.from
import androidx.compose.foundation.shape.RoundedCornerShape
import kotlinx.coroutines.launch
import androidx.compose.runtime.saveable.rememberSaveable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onBackClick: () -> Unit,
    onLoginClick: () -> Unit
) {
    val context = LocalContext.current
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }

    if (errorMessage != null) {
        AlertDialog(
            onDismissRequest = { errorMessage = null },
            confirmButton = {
                TextButton(onClick = { errorMessage = null }) {
                    Text("OK", color = Color.Red)
                }
            },
            title = { Text("Login Error", color = Color.Red) },
            text = { Text(errorMessage!!) },
            shape = RoundedCornerShape(28.dp),
            containerColor = Color.White
        )
    }

    GlassBackground {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("Login", style = MaterialTheme.typography.titleLarge) },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.Black)
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = Color.Black
                    )
                )
            },
            containerColor = Color.Transparent
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(20.dp))

                WayBagLogo(modifier = Modifier.size(140.dp))

                Spacer(modifier = Modifier.height(40.dp))
                
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(28.dp)
                ) {
                    WayBagTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = "Email",
                        placeholder = "name@example.com"
                    )

                    WayBagTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = "Password",
                        placeholder = "Enter your password",
                        isPassword = true
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                val scope = rememberCoroutineScope()
                var isLoading by remember { mutableStateOf(false) }

                WayBagButton(
                    text = if (isLoading) "Logging in..." else "Login",
                    onClick = {
                        if (email.isBlank() || password.isBlank()) {
                            errorMessage = "Please enter both email and password"
                            return@WayBagButton
                        }
                        isLoading = true
                        scope.launch {
                            try {
                                supabase.auth.signInWith(Email) {
                                    this.email = email
                                    this.password = password
                                }
                                
                                // NEW: Save session locally after successful login
                                val user = supabase.auth.currentUserOrNull()
                                if (user != null) {
                                    val profile = supabase.from("profiles").select {
                                        filter { eq("id", user.id) }
                                    }.decodeSingleOrNull<com.example.waybag.data.model.UserProfile>()
                                    
                                    if (profile != null) {
                                        com.example.waybag.data.saveLoginStatus(context, true)
                                        com.example.waybag.data.saveUserSession(
                                            context,
                                            com.example.waybag.data.UserSession(
                                                userId = user.id,
                                                email = user.email ?: "",
                                                name = profile.name
                                            )
                                        )
                                    }
                                }
                                
                                onLoginClick()
                            } catch (e: Exception) {
                                val message = e.message ?: ""
                                when {
                                    message.contains("Invalid login credentials", ignoreCase = true) -> {
                                        // To distinguish between "email not found" and "wrong password",
                                        // we check if the user exists in our profiles table.
                                        try {
                                            val userExists = supabase.from("profiles").select {
                                                filter { eq("email", email) }
                                            }.decodeList<com.example.waybag.data.model.UserProfile>().isNotEmpty()
                                            
                                            if (userExists) {
                                                errorMessage = "password error"
                                            } else {
                                                errorMessage = "email haven't sign in, please go to sign in, tq"
                                            }
                                        } catch (dbEx: Exception) {
                                            errorMessage = "password error" // Fallback if DB check fails
                                        }
                                    }
                                    else -> {
                                        errorMessage = e.localizedMessage ?: "An unexpected error occurred"
                                    }
                                }
                            } finally {
                                isLoading = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(60.dp))
            }
        }
    }
}
