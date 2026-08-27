package com.example.waybag.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.waybag.ui.screens.*

import com.example.waybag.data.supabase
import io.github.jan.supabase.auth.auth

sealed class Screen(val route: String) {
    object Welcome : Screen("welcome")
    object Login : Screen("login")
    object ForgotPassword : Screen("forgot_password")
    object SignUp : Screen("sign_up")
    object Dashboard : Screen("dashboard")
    object Profile : Screen("profile")
}

@Composable
fun WayBagNavHost() {
    val navController = rememberNavController()
    val currentUser = androidx.compose.runtime.remember { 
        com.example.waybag.data.supabase.auth.currentUserOrNull() 
    }
    
    val startRoute = if (currentUser != null) Screen.Dashboard.route else Screen.Welcome.route

    NavHost(
        navController = navController,
        startDestination = startRoute
    ) {
        composable(Screen.Welcome.route) {
            WelcomeScreen(
                onLoginClick = { navController.navigate(Screen.Login.route) },
                onSignUpClick = { navController.navigate(Screen.SignUp.route) },
                onAutoLogin = {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.Welcome.route) { inclusive = true }
                    }
                }
            )
        }
        composable(Screen.Login.route) {
            LoginScreen(
                onBackClick = { navController.popBackStack() },
                onForgotPasswordClick = { navController.navigate(Screen.ForgotPassword.route) },
                onLoginClick = { navController.navigate(Screen.Dashboard.route) {
                    popUpTo(Screen.Welcome.route) { inclusive = true }
                }}
            )
        }
        composable(Screen.ForgotPassword.route) {
            ForgotPasswordScreen(
                onBackClick = { navController.popBackStack() },
                onConfirmClick = { navController.popBackStack() }
            )
        }
        composable(Screen.SignUp.route) {
            SignUpScreen(
                onBackClick = { navController.popBackStack() },
                onSignUpClick = { navController.navigate(Screen.Dashboard.route) {
                    popUpTo(Screen.Welcome.route) { inclusive = true }
                }}
            )
        }
        composable(Screen.Dashboard.route) {
            DashboardScreen(
                onExitClick = {
                    // Navigate back to Welcome screen and clear the entire history
                    navController.navigate(Screen.Welcome.route) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onProfileClick = {
                    navController.navigate(Screen.Profile.route)
                }
            )
        }
        composable(Screen.Profile.route) {
            ProfileScreen(
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}
