@file:OptIn(SavedStateHandleSaveableApi::class)

package cl.osvaldouhart.climapulse.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.SavedStateHandleSaveableApi
import androidx.lifecycle.viewmodel.compose.saveable
import cl.osvaldouhart.climapulse.data.AuthException
import cl.osvaldouhart.climapulse.data.SessionRepository
import cl.osvaldouhart.climapulse.data.ThemeMode
import cl.osvaldouhart.climapulse.data.UserProfile
import cl.osvaldouhart.climapulse.ui.appViewModelFactory
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val session: SessionRepository) : ViewModel() {
    val profile: StateFlow<UserProfile?> = session.profile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val biometricEnabled = session.biometricEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val notificationsEnabled = session.notificationsEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val autoRefreshEnabled = session.autoRefreshEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
    val themeMode = session.themeMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.SYSTEM)

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { session.setThemeMode(mode) }

    // Interruptor de Perfil: true = la bienvenida sale al abrir la app.
    val showWelcome = session.welcomeHidden.map { !it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun setShowWelcome(show: Boolean) = viewModelScope.launch { session.setWelcomeHidden(!show) }

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    fun setNotifications(enabled: Boolean) = viewModelScope.launch {
        session.setNotificationsEnabled(enabled)
        _messages.send(if (enabled) "Alertas activadas" else "Alertas desactivadas")
    }

    fun setAutoRefresh(enabled: Boolean) = viewModelScope.launch {
        session.setAutoRefreshEnabled(enabled)
    }

    fun setBiometric(enabled: Boolean) = viewModelScope.launch {
        session.setBiometricEnabled(enabled)
        _messages.send(if (enabled) "Desbloqueo con huella activado" else "Desbloqueo con huella desactivado")
    }

    fun logout(onDone: () -> Unit) = viewModelScope.launch {
        session.logout()
        onDone()
    }

    companion object {
        val Factory = appViewModelFactory { c, _ -> SettingsViewModel(c.sessionRepository) }
    }
}

class EditProfileViewModel(
    private val session: SessionRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private var _name by savedStateHandle.saveable { mutableStateOf("") }
    var name: String
        get() = _name
        set(value) {
            _name = value
            nameError = null
        }
    // El correo solo se muestra: cambiarlo requiere verificar el correo nuevo.
    var email by savedStateHandle.saveable { mutableStateOf("") }
        private set
    private var _phone by savedStateHandle.saveable { mutableStateOf("") }
    var phone: String
        get() = _phone
        set(value) {
            _phone = value
            phoneError = null
        }
    private var loaded by savedStateHandle.saveable { mutableStateOf(false) }

    var nameError by mutableStateOf<String?>(null)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var phoneError by mutableStateOf<String?>(null)
        private set
    var saving by mutableStateOf(false)
        private set

    private val _saved = Channel<Unit>(Channel.BUFFERED)
    val saved = _saved.receiveAsFlow()

    init {
        // Solo se carga una vez: si el usuario giró el teléfono, se conserva lo que estaba escribiendo.
        if (!loaded) {
            viewModelScope.launch {
                val profile = session.profile.first()
                name = profile.name
                email = profile.email
                phone = profile.phone
                loaded = true
            }
        }
    }

    fun save() {
        nameError = if (name.isBlank()) "Ingresa tu nombre" else null
        phoneError = if (phone.isNotBlank() && phone.count(Char::isDigit) < 8) "Revisa el número de teléfono" else null
        if (nameError != null || phoneError != null) return
        viewModelScope.launch {
            saving = true
            errorMessage = null
            try {
                session.updateProfile(name.trim(), phone.trim())
                _saved.send(Unit)
            } catch (e: AuthException) {
                errorMessage = e.message
            } finally {
                saving = false
            }
        }
    }

    companion object {
        val Factory = appViewModelFactory { c, handle -> EditProfileViewModel(c.sessionRepository, handle) }
    }
}
