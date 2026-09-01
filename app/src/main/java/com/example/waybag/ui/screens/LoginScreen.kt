package com.example.waybag.ui.screens

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.waybag.ui.components.*
import com.example.waybag.ui.theme.Apple_Blue
import com.example.waybag.ui.theme.Premium_Gray
import com.example.waybag.ui.viewmodel.LoginViewModel
import androidx.compose.foundation.shape.RoundedCornerShape

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onBackClick: () -> Unit,
    onLoginClick: () -> Unit,
    viewModel: LoginViewModel = viewModel()
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    val scrollState = rememberScrollState()

    if (state.errorMessage != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearError() },
            confirmButton = {
                TextButton(onClick = { viewModel.clearError() }) {
                    Text("OK", color = Color.Red)
                }
            },
            title = { Text("Login Error", color = Color.Red) },
            text = { Text(state.errorMessage!!) },
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
                    .verticalScroll(scrollState)
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
                        value = state.email,
                        onValueChange = { viewModel.onEmailChange(it) },
                        label = "Email",
                        placeholder = "name@example.com"
                    )

                    WayBagTextField(
                        value = state.password,
                        onValueChange = { viewModel.onPasswordChange(it) },
                        label = "Password",
                        placeholder = "Enter your password",
                        isPassword = true
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))
                Spacer(modifier = Modifier.weight(1f))

                WayBagButton(
                    text = if (state.isLoading) "Logging in..." else "Login",
                    onClick = {
                        viewModel.login(context, onLoginClick)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(60.dp))
            }
        }
    }
}
