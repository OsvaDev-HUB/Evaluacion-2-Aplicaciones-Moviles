package com.example.climapulse

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import com.example.climapulse.data.ThemeMode
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.climapulse.ui.navigation.ClimaPulseNavHost
import com.example.climapulse.ui.theme.ClimaPulseTheme

// FragmentActivity es requerido por BiometricPrompt.
class MainActivity : FragmentActivity() {
    private val viewModel: MainViewModel by viewModels { MainViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        splashScreen.setKeepOnScreenCondition { viewModel.startDestination.value == null }
        splashScreen.setOnExitAnimationListener { provider ->
            provider.view.animate()
                .alpha(0f)
                .setDuration(250)
                .withEndAction { provider.remove() }
                .start()
        }
        enableEdgeToEdge()
        setContent {
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            // Barras del sistema transparentes, con íconos claros u oscuros según el tema elegido.
            DisposableEffect(darkTheme) {
                val style = if (darkTheme) SystemBarStyle.dark(Color.TRANSPARENT)
                else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose { }
            }
            ClimaPulseTheme(darkTheme = darkTheme) {
                val startDestination by viewModel.startDestination.collectAsStateWithLifecycle()
                // Surface raíz: define fondo y color de contenido por defecto (onBackground).
                Surface(color = MaterialTheme.colorScheme.background) {
                    startDestination?.let { ClimaPulseNavHost(startDestination = it) }
                }
            }
        }
    }
}
