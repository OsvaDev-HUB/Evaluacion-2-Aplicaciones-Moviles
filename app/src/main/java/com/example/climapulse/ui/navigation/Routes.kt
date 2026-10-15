package com.example.climapulse.ui.navigation

import com.example.climapulse.data.Metric
import kotlinx.serialization.Serializable

// Rutas tipadas del Navigation Graph (equivalente en Compose a SafeArgs):
// los argumentos se validan en compilación y nunca llegan nulos a la pantalla.

@Serializable data object OnboardingRoute
@Serializable data class LoginRoute(val biometricUnlock: Boolean = false)
@Serializable data object RegisterRoute
@Serializable data class RecoveryRoute(val email: String = "")
@Serializable data object HomeRoute
@Serializable data class MetricDetailRoute(val metric: Metric)
@Serializable data object EditProfileRoute
@Serializable data object TermsRoute

// Destinos de nivel superior dentro del Home (barra inferior).
@Serializable data object DashboardTab
@Serializable data object HistoryTab
@Serializable data object SensorTab
@Serializable data object ReportsTab
@Serializable data object SettingsTab
