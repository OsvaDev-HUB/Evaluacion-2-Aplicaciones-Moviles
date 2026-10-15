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
            _startDestination.value = session.resolveStartDestination()
        }
    }

    companion object {
        val Factory = appViewModelFactory { container, _ -> MainViewModel(container.sessionRepository) }
    }
}
