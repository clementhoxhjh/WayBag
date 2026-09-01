package com.example.waybag.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.waybag.ui.components.*
import com.example.waybag.ui.theme.Premium_Gray
import kotlinx.coroutines.delay
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.waybag.ui.viewmodel.WelcomeViewModel

@Composable
fun WelcomeScreen(
    onLoginClick: () -> Unit,
    onSignUpClick: () -> Unit,
    onAutoLogin: () -> Unit,
    viewModel: WelcomeViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    
    LaunchedEffect(Unit) {
        viewModel.checkAutoLogin(context, onAutoLogin)
    }

    if (state.isCheckingAutoLogin) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            // Optional: Show a splash logo or loading indicator here
        }
    } else {
        val scrollState = rememberScrollState()
        GlassBackground {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 32.dp, vertical = 60.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Logo and Title Section
                AnimatedVisibility(
                    visible = state.isVisible,
                    enter = fadeIn(tween(1000)) + slideInVertically(tween(1000)) { -60 }
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        WayBagLogo(modifier = Modifier.size(240.dp))
                    }
                }

                // Description Section (Directly on background)
                AnimatedVisibility(
                    visible = state.isVisible,
                    enter = fadeIn(tween(1000, 300)) + slideInVertically(tween(1000, 300)) { 60 }
                ) {
                    Column {
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = "The smartest way to reuse. Track your plastic-free journey and watch your green impact grow.",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                lineHeight = 24.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            color = Premium_Gray,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }

                // Actions Section
                AnimatedVisibility(
                    visible = state.isVisible,
                    enter = fadeIn(tween(1000, 600)) + slideInVertically(tween(1000, 600)) { 100 }
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        WayBagButton(
                            text = "Get Started",
                            onClick = onLoginClick,
                            modifier = Modifier.fillMaxWidth(),
                            showArrow = true
                        )
                        WayBagButton(
                            text = "Sign Up",
                            onClick = onSignUpClick,
                            modifier = Modifier.fillMaxWidth(),
                            primary = false
                        )
                    }
                }
            }
        }
    }
}
