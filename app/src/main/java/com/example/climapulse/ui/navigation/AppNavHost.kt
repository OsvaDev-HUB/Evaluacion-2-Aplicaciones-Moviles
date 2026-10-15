package com.example.climapulse.ui.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.climapulse.data.StartDestination
import com.example.climapulse.ui.auth.LoginScreen
import com.example.climapulse.ui.auth.RecoveryScreen
import com.example.climapulse.ui.auth.RegisterScreen
import com.example.climapulse.ui.components.LocalMessenger
import com.example.climapulse.ui.components.Messenger
import com.example.climapulse.ui.detail.MetricDetailScreen
import com.example.climapulse.ui.home.HomeScaffold
import com.example.climapulse.ui.onboarding.OnboardingScreen
import com.example.climapulse.ui.settings.EditProfileScreen
import com.example.climapulse.ui.settings.TermsScreen

@Composable
fun ClimaPulseNavHost(startDestination: StartDestination) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val messengerScope = rememberCoroutineScope()
    val messenger = remember { Messenger(snackbarHostState, messengerScope) }

    val start: Any = when (startDestination) {
        StartDestination.Onboarding -> OnboardingRoute
        is StartDestination.Login -> LoginRoute(biometricUnlock = startDestination.biometricUnlock)
        StartDestination.Home -> HomeRoute
    }

    CompositionLocalProvider(LocalMessenger provides messenger) {
        NavHost(
            navController = navController,
            startDestination = start,
            enterTransition = { slideInHorizontally { it / 4 } + fadeIn() },
            exitTransition = { fadeOut() },
            popEnterTransition = { fadeIn() },
            popExitTransition = { slideOutHorizontally { it / 4 } + fadeOut() }
        ) {
            composable<OnboardingRoute> {
                OnboardingScreen(
                    onFinished = dropUnlessResumed { navController.clearAndNavigate(LoginRoute()) }
                )
            }
            composable<LoginRoute> { entry ->
                LoginScreen(
                    biometricUnlock = entry.toRoute<LoginRoute>().biometricUnlock,
                    onLoggedIn = { navController.clearAndNavigate(HomeRoute) },
                    onRegister = dropUnlessResumed { navController.navigate(RegisterRoute) },
                    onForgotPassword = { email -> navController.navigate(RecoveryRoute(email)) }
                )
            }
            composable<RegisterRoute> {
                RegisterScreen(
                    onRegistered = { navController.clearAndNavigate(HomeRoute) },
                    onOpenTerms = dropUnlessResumed { navController.navigate(TermsRoute) },
                    onBack = dropUnlessResumed { navController.popBackStack() }
                )
            }
            composable<RecoveryRoute> {
                RecoveryScreen(onBack = dropUnlessResumed { navController.popBackStack() })
            }
            composable<HomeRoute> {
                HomeScaffold(
                    onOpenMetric = { metric -> navController.navigate(MetricDetailRoute(metric)) },
                    onEditProfile = dropUnlessResumed { navController.navigate(EditProfileRoute) },
                    onOpenTerms = dropUnlessResumed { navController.navigate(TermsRoute) },
                    onLoggedOut = { navController.clearAndNavigate(LoginRoute()) }
                )
            }
            composable<MetricDetailRoute> {
                MetricDetailScreen(onBack = dropUnlessResumed { navController.popBackStack() })
            }
            composable<EditProfileRoute> {
                EditProfileScreen(onBack = dropUnlessResumed { navController.popBackStack() })
            }
            composable<TermsRoute> {
                TermsScreen(onBack = dropUnlessResumed { navController.popBackStack() })
            }
        }
    }
}

/** Navega y limpia la pila: el usuario no puede volver con "atrás" al login u onboarding. */
private fun NavHostController.clearAndNavigate(route: Any) {
    navigate(route) {
        popUpTo(graph.id) { inclusive = true }
        launchSingleTop = true
    }
}
