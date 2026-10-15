package com.example.climapulse.ui.home

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.climapulse.data.Metric
import com.example.climapulse.ui.permissions.PermissionsSheet
import com.example.climapulse.ui.components.AppSnackbarHost
import com.example.climapulse.ui.components.CollectMessages
import com.example.climapulse.ui.components.OfflineBanner
import com.example.climapulse.ui.history.HistoryScreen
import com.example.climapulse.ui.navigation.DashboardTab
import com.example.climapulse.ui.navigation.HistoryTab
import com.example.climapulse.ui.navigation.ReportsTab
import com.example.climapulse.ui.navigation.SensorTab
import com.example.climapulse.ui.navigation.SettingsTab
import com.example.climapulse.ui.settings.SettingsScreen
import com.example.climapulse.ui.UiState
import com.example.climapulse.ui.tour.LocalTour
import com.example.climapulse.ui.tour.TourController
import com.example.climapulse.ui.tour.TourKeys
import com.example.climapulse.ui.tour.TourOverlay
import com.example.climapulse.ui.tour.homeTourSteps
import com.example.climapulse.ui.tour.tourTarget
import kotlinx.coroutines.delay

private data class Tab(val label: String, val selectedIcon: ImageVector, val icon: ImageVector, val route: Any)

private val tabs = listOf(
    Tab("Inicio", Icons.Rounded.Home, Icons.Outlined.Home, DashboardTab),
    Tab("Historial", Icons.AutoMirrored.Rounded.ShowChart, Icons.AutoMirrored.Outlined.ShowChart, HistoryTab),
    Tab("Sensor", Icons.Rounded.Sensors, Icons.Outlined.Sensors, SensorTab),
    Tab("Reportes", Icons.Rounded.Analytics, Icons.Outlined.Analytics, ReportsTab),
    Tab("Perfil", Icons.Rounded.Person, Icons.Outlined.Person, SettingsTab)
)

/**
 * Contenedor anfitrión: barra inferior persistente y un NavHost interno con los destinos de
 * nivel superior. Cambiar de pestaña reemplaza el destino y restaura su estado guardado.
 */
@Composable
fun HomeScaffold(
    onOpenMetric: (Metric) -> Unit,
    onEditProfile: () -> Unit,
    onOpenTerms: () -> Unit,
    onLoggedOut: () -> Unit,
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory)
) {
    val tabNavController = rememberNavController()
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    val linkState by viewModel.linkState.collectAsStateWithLifecycle()
    val introDone by viewModel.introDone.collectAsStateWithLifecycle()
    val biometricEnabled by viewModel.biometricEnabled.collectAsStateWithLifecycle()
    val dashboardReady = viewModel.uiState.collectAsStateWithLifecycle().value is UiState.Success
    val backStackEntry by tabNavController.currentBackStackEntryAsState()
    val onDashboard = backStackEntry?.destination?.hasRoute(DashboardTab::class) == true
    val tour = remember { TourController() }
    // Bienvenida: permisos → tutorial → listo. Solo en el primer ingreso de cada usuario
    // (por ejemplo, justo después de registrarse). null = todavía no se decide.
    var introPhase by rememberSaveable { mutableStateOf<IntroPhase?>(null) }
    LaunchedEffect(introDone) {
        val done = introDone ?: return@LaunchedEffect
        if (introPhase == null) introPhase = if (done) IntroPhase.Done else IntroPhase.Permissions
    }
    CollectMessages(viewModel.messages)

    // El tutorial se lanza cuando el Inicio terminó de cargar (primer ingreso o pedido desde Perfil).
    LaunchedEffect(introPhase, dashboardReady, onDashboard) {
        if (introPhase == IntroPhase.Tour && dashboardReady && onDashboard && !tour.isActive) {
            delay(600) // deja que la pantalla termine de dibujarse
            tour.start(homeTourSteps)
        }
    }

    if (introPhase == IntroPhase.Permissions && onDashboard) {
        PermissionsSheet(
            onNotificationsResult = viewModel::onNotificationPermission,
            biometricEnabled = biometricEnabled,
            onEnableBiometric = viewModel::enableBiometric,
            onDone = { introPhase = IntroPhase.Tour }
        )
    }

    CompositionLocalProvider(LocalTour provides tour) {
        Box(Modifier.fillMaxSize()) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                bottomBar = { ClimaPulseNavigation(tabNavController) },
                snackbarHost = { AppSnackbarHost() }
            ) { padding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding())
                ) {
                    OfflineBanner(visible = !isOnline)
                    NavHost(
                        navController = tabNavController,
                        startDestination = DashboardTab,
                        modifier = Modifier.weight(1f),
                        enterTransition = { fadeIn() },
                        exitTransition = { fadeOut() }
                    ) {
                        composable<DashboardTab> {
                            DashboardScreen(
                                viewModel = viewModel,
                                onOpenMetric = onOpenMetric,
                                onOpenHistory = { tabNavController.navigateToTab(HistoryTab) },
                                onOpenLink = { tabNavController.navigateToTab(SensorTab) }
                            )
                        }
                        composable<HistoryTab> { HistoryScreen() }
                        composable<SensorTab> { SensorScreen() }
                        composable<ReportsTab> { ReportsScreen(viewModel = viewModel) }
                        composable<SettingsTab> {
                            SettingsScreen(
                                linkState = linkState,
                                onRefresh = { viewModel.refresh() },
                                onEditProfile = onEditProfile,
                                onOpenTerms = onOpenTerms,
                                onLoggedOut = onLoggedOut,
                                onReplayTour = {
                                    introPhase = IntroPhase.Tour
                                    tabNavController.navigateToTab(DashboardTab)
                                }
                            )
                        }
                    }
                }
            }
            // Capa del tour por encima de todo, incluida la barra inferior.
            TourOverlay(
                controller = tour,
                onFinished = {
                    viewModel.finishIntro()
                    introPhase = IntroPhase.Done
                }
            )
        }
    }
}

private enum class IntroPhase { Permissions, Tour, Done }

private fun NavHostController.navigateToTab(route: Any) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun ClimaPulseNavigation(navController: NavHostController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination

    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.tourTarget(TourKeys.NAVIGATION)
    ) {
        tabs.forEach { tab ->
            val selected = destination?.hierarchy?.any { it.hasRoute(tab.route::class) } == true
            NavigationBarItem(
                selected = selected,
                onClick = { navController.navigateToTab(tab.route) },
                icon = { Icon(if (selected) tab.selectedIcon else tab.icon, contentDescription = null) },
                label = { Text(tab.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    }
}
