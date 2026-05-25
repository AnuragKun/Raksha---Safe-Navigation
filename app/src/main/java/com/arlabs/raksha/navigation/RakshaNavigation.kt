package com.arlabs.raksha.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navDeepLink
import com.arlabs.raksha.features.main.MainScreen
import com.arlabs.raksha.features.auth.AuthenticationScreen
import com.arlabs.raksha.features.onboarding.OnBoardingScreen

@Composable
fun RakshaNavigation(
    startDestination: String,
    deepLinkTarget: String? = null
) {
    val navController = rememberNavController()

    // Handle deep link from notification
    LaunchedEffect(deepLinkTarget) {
        if (deepLinkTarget == "safety_timer" && startDestination == Routes.MainScreen) {
            // Navigate to safety timer after the nav graph is ready
            navController.navigate(Routes.SafetyTimerScreen)
        }
    }

    NavHost(navController = navController, startDestination = startDestination) {

        composable(Routes.OnBoardingScreen) {
            OnBoardingScreen(
                onGetStartedClicked = {
                    navController.navigate(Routes.AuthenticationScreen) {
                        popUpTo(Routes.OnBoardingScreen) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.AuthenticationScreen) {
            AuthenticationScreen(navHostController = navController)
        }

        composable(Routes.MainScreen) {
            MainScreen(navController = navController)
        }

        composable(Routes.PhoneNumberScreen) {
            val viewModel = androidx.hilt.navigation.compose.hiltViewModel<com.arlabs.raksha.features.auth.AuthViewModel>()
            com.arlabs.raksha.features.auth.PhoneNumberScreen(navController, viewModel)
        }

        composable(Routes.OtpScreen) {
            val viewModel = androidx.hilt.navigation.compose.hiltViewModel<com.arlabs.raksha.features.auth.AuthViewModel>()
            com.arlabs.raksha.features.auth.OtpScreen(navController, viewModel)
        }

        composable(Routes.ProfileScreen) {
            com.arlabs.raksha.features.profile.ProfileScreen(navController = navController)
        }

        composable(Routes.VerifyAccountScreen) {
            val viewModel = androidx.hilt.navigation.compose.hiltViewModel<com.arlabs.raksha.features.auth.AuthViewModel>()
            com.arlabs.raksha.features.profile.VerifyAccountScreen(navController = navController, authViewModel = viewModel)
        }

        composable(Routes.SafetyTimerScreen) {
            com.arlabs.raksha.features.safetytimer.SafetyTimerScreen(navController = navController)
        }

        composable(Routes.EmergencyHubScreen) {
            com.arlabs.raksha.features.emergencyhub.EmergencyHubScreen(navController = navController)
        }

        composable(
            route = "live_location/{sessionId}",
            deepLinks = listOf(
                navDeepLink { uriPattern = "https://raksha-97818.web.app/live/{sessionId}" },
                navDeepLink { uriPattern = "http://raksha-97818.web.app/live/{sessionId}" },
                navDeepLink { uriPattern = "raksha://live/{sessionId}" }
            )
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getString("sessionId") ?: return@composable
            com.arlabs.raksha.features.livelocation.ui.LiveLocationViewerScreen(
                sessionId = sessionId,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}