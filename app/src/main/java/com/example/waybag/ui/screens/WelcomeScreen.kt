package com.example.waybag.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
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
import androidx.compose.runtime.saveable.rememberSaveable
import com.example.waybag.data.supabase
import io.github.jan.supabase.auth.auth

@Composable
fun WelcomeScreen(
    onLoginClick: () -> Unit,
    onSignUpClick: () -> Unit,
    onAutoLogin: () -> Unit
) {
    var visible by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    
    LaunchedEffect(Unit) {
        // Auto-login check: only if not already on the screen from a logout
        val currentUser = supabase.auth.currentUserOrNull()
        if (currentUser != null) {
            onAutoLogin() // Use the dedicated auto-login path
        } else {
            delay(300)
            visible = true
        }
    }

    GlassBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp, vertical = 60.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Logo and Title Section
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(1000)) + slideInVertically(tween(1000)) { -60 }
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    WayBagLogo(modifier = Modifier.size(160.dp))
                    Spacer(modifier = Modifier.height(32.dp))
                    Text(
                        text = "WayBag",
                        style = MaterialTheme.typography.displayLarge,
                        color = Color.Black,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Description Section (Directly on background)
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(1000, 300)) + slideInVertically(tween(1000, 300)) { 60 }
            ) {
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
            }

            // Actions Section
            AnimatedVisibility(
                visible = visible,
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
