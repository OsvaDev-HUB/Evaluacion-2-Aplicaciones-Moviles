package cl.osvaldouhart.climapulse.ui.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import kotlinx.coroutines.launch
import cl.osvaldouhart.climapulse.data.StartDestination
import cl.osvaldouhart.climapulse.ui.auth.LoginScreen
import cl.osvaldouhart.climapulse.ui.auth.RecoveryScreen
import cl.osvaldouhart.climapulse.ui.auth.RegisterScreen
import cl.osvaldouhart.climapulse.ui.auth.VerifyEmailScreen
import cl.osvaldouhart.climapulse.ui.components.LocalMessenger
import cl.osvaldouhart.climapulse.ui.components.Messenger
import cl.osvaldouhart.climapulse.ui.detail.MetricDetailScreen
import cl.osvaldouhart.climapulse.ui.home.HomeScaffold
import cl.osvaldouhart.climapulse.ui.onboarding.OnboardingScreen
import cl.osvaldouhart.climapulse.ui.settings.ChangePasswordScreen
import cl.osvaldouhart.climapulse.ui.settings.DeleteAccountScreen
import cl.osvaldouhart.climapulse.ui.settings.EditProfileScreen
import cl.osvaldouhart.climapulse.ui.settings.TermsScreen

@Composable
fun ClimaPulseNavHost(
    startDestination: StartDestination,
    onAppStopped: () -> Unit = {},
    shouldLockOnReturn: suspend () -> Boolean = { false }
) {
    val navController = rememberNavController()
    val lockScope = rememberCoroutineScope()

    // Si la app estuvo un minuto o mas en segundo plano, se vuelve a pedir la huella.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { onAppStopped() }
    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        lockScope.launch {
            val destination = navController.currentDestination
            if (destination != null && destination.isInsideApp() && shouldLockOnReturn()) {
                navController.clearAndNavigate(LoginRoute(biometricUnlock = true))
            }
        }
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val messengerScope = rememberCoroutineScope()
    val messenger = remember { Messenger(snackbarHostState, messengerScope) }

    val start: Any = routeFor(startDestination)

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
                    onFinished = { next -> navController.clearAndNavigate(routeFor(next)) }
                )
            }
            composable<LoginRoute> { entry ->
                LoginScreen(
                    biometricUnlock = entry.toRoute<LoginRoute>().biometricUnlock,
                    onLoggedIn = { verified ->
                        navController.clearAndNavigate(if (verified) HomeRoute else VerifyEmailRoute)
                    },
                    onRegister = dropUnlessResumed { navController.navigate(RegisterRoute) },
                    onForgotPassword = { email -> navController.navigate(RecoveryRoute(email)) }
                )
            }
            composable<RegisterRoute> {
                RegisterScreen(
                    onRegistered = { navController.clearAndNavigate(VerifyEmailRoute) },
                    onOpenTerms = dropUnlessResumed { navController.navigate(TermsRoute) },
                    onBack = dropUnlessResumed { navController.popBackStack() }
                )
            }
            composable<RecoveryRoute> {
                RecoveryScreen(onBack = dropUnlessResumed { navController.popBackStack() })
            }
            composable<VerifyEmailRoute> {
                VerifyEmailScreen(
                    onVerified = { navController.clearAndNavigate(HomeRoute) },
                    onLoggedOut = { navController.clearAndNavigate(LoginRoute()) }
                )
            }
            composable<HomeRoute> {
                HomeScaffold(
                    onOpenMetric = { metric -> navController.navigate(MetricDetailRoute(metric)) },
                    onEditProfile = dropUnlessResumed { navController.navigate(EditProfileRoute) },
                    onChangePassword = dropUnlessResumed { navController.navigate(ChangePasswordRoute) },
                    onDeleteAccount = dropUnlessResumed { navController.navigate(DeleteAccountRoute) },
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
            composable<ChangePasswordRoute> {
                ChangePasswordScreen(onBack = dropUnlessResumed { navController.popBackStack() })
            }
            composable<DeleteAccountRoute> {
                DeleteAccountScreen(
                    onBack = dropUnlessResumed { navController.popBackStack() },
                    onDeleted = { navController.clearAndNavigate(LoginRoute()) }
                )
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

// Pantallas que se ven con la sesion abierta (en login, registro o bienvenida no hay nada que bloquear).
private fun NavDestination.isInsideApp(): Boolean =
    !hasRoute<OnboardingRoute>() && !hasRoute<LoginRoute>() && !hasRoute<RegisterRoute>() &&
        !hasRoute<RecoveryRoute>() && !hasRoute<VerifyEmailRoute>()

/** Convierte el destino que decide el Router en la ruta de navegación. */
private fun routeFor(destination: StartDestination): Any = when (destination) {
    StartDestination.Onboarding -> OnboardingRoute
    is StartDestination.Login -> LoginRoute(biometricUnlock = destination.biometricUnlock)
    StartDestination.VerifyEmail -> VerifyEmailRoute
    StartDestination.Home -> HomeRoute
}
