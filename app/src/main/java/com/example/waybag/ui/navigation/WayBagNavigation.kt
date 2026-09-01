package com.example.waybag.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.waybag.ui.screens.*

sealed class Screen(val route: String) {
    object Welcome : Screen("welcome")
    object Login : Screen("login")
    object SignUp : Screen("sign_up")
    object Dashboard : Screen("dashboard_root")
    object Profile : Screen("profile")

    // Dashboard Tabs
    object HomeTab : Screen("home_tab")
    object QRScanTab : Screen("qr_scan_tab")
    object AITrackerTab : Screen("ai_tracker_tab")
    object HistoryTab : Screen("history_tab")
}

@Composable
fun WayBagNavHost() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val navController = rememberNavController()
    
    // NEW: Load user session into memory on app start
    val session = androidx.compose.runtime.remember {
        com.example.waybag.data.loadUserSession(context)
    }
    
    // Check local storage flag
    val isLocallyLoggedIn = androidx.compose.runtime.remember { 
        com.example.waybag.data.isUserLoggedIn(context) 
    }
    
    // Auto-navigate if both flag is true and session is loaded
    val startRoute = if (isLocallyLoggedIn && session != null) {
        Screen.Dashboard.route 
    } else {
        Screen.Welcome.route
    }

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
                onLoginClick = { navController.navigate(Screen.Dashboard.route) {
                    popUpTo(Screen.Welcome.route) { inclusive = true }
                }}
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
            HomeScreen(
                onProfileClick = {
                    navController.navigate(Screen.Profile.route)
                }
            )
        }
        composable(Screen.Profile.route) {
            ProfileScreen(
                onBackClick = { navController.popBackStack() },
                onLogoutClick = {
                    // Navigate back to Welcome screen and clear the entire history
                    navController.navigate(Screen.Welcome.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
    }
}
