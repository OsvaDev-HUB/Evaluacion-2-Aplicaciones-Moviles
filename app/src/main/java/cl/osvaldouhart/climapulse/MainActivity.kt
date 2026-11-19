package cl.osvaldouhart.climapulse

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
import cl.osvaldouhart.climapulse.data.ThemeMode
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cl.osvaldouhart.climapulse.ui.navigation.ClimaPulseNavHost
import cl.osvaldouhart.climapulse.ui.theme.ClimaPulseTheme

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
        hideSystemBars()
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
                hideSystemBars()
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

    // Al volver de otra pantalla (huella, selector de Google, ajustes) Android puede mostrar
    // las barras de nuevo, por eso se vuelven a ocultar cada vez que la app recupera el foco.
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    // Pantalla completa: se ocultan la barra de estado y la de navegación. Deslizando desde
    // el borde aparecen un momento y se vuelven a esconder solas.
    private fun hideSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }
}
