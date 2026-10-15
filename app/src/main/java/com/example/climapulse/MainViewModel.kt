package com.example.climapulse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.climapulse.data.SessionRepository
import com.example.climapulse.data.StartDestination
import com.example.climapulse.data.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import com.example.climapulse.ui.appViewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Router de arranque: mientras resuelve el destino, la Splash Screen permanece visible
 * (sin Thread.sleep ni temporizadores).
 */
class MainViewModel(private val session: SessionRepository) : ViewModel() {
    private val _startDestination = MutableStateFlow<StartDestination?>(null)
    val startDestination: StateFlow<StartDestination?> = _startDestination.asStateFlow()

    /** Tema elegido en Perfil (Sistema, Claro u Oscuro). */
    val themeMode: StateFlow<ThemeMode> = session.themeMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM)

    init {
        viewModelScope.launch {
            val inicio = System.currentTimeMillis()
            val destino = session.resolveStartDestination()
            // El logo se ve al menos 1,2 segundos, como en cualquier app (sin bloquear el hilo).
            val restante = MIN_SPLASH_MILLIS - (System.currentTimeMillis() - inicio)
            if (restante > 0) delay(restante)
            _startDestination.value = destino
        }
    }

    companion object {
        const val MIN_SPLASH_MILLIS = 1_200L

        val Factory = appViewModelFactory { container, _ -> MainViewModel(container.sessionRepository) }
    }
}
