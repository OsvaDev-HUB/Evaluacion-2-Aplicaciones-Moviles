@file:OptIn(SavedStateHandleSaveableApi::class)

package cl.osvaldouhart.climapulse.ui.auth

import android.util.Patterns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.SavedStateHandleSaveableApi
import androidx.lifecycle.viewmodel.compose.saveable
import androidx.navigation.toRoute
import cl.osvaldouhart.climapulse.data.AuthException
import cl.osvaldouhart.climapulse.data.SessionRepository
import cl.osvaldouhart.climapulse.data.validateNewPassword
import cl.osvaldouhart.climapulse.ui.appViewModelFactory
import cl.osvaldouhart.climapulse.ui.navigation.RecoveryRoute
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

private const val GENERIC_ERROR = "No pudimos completar la acción. Revisa tu conexión e intenta de nuevo."

internal fun validateEmail(email: String): String? = when {
    email.isBlank() -> "Ingresa tu correo"
    !Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches() -> "Revisa el formato del correo"
    else -> null
}

internal fun validatePassword(password: String): String? = validateNewPassword(password)

/**
 * Los campos viven en el ViewModel (y en SavedStateHandle), por lo que no se borran
 * al girar el teléfono. Las contraseñas no se guardan en el SavedStateHandle.
 */
class LoginViewModel(
    private val session: SessionRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private var _email by savedStateHandle.saveable { mutableStateOf("") }
    var email: String
        get() = _email
        set(value) {
            _email = value
            emailError = null
        }
    private var _password by mutableStateOf("")
    var password: String
        get() = _password
        set(value) {
            _password = value
            passwordError = null
        }
    var emailError by mutableStateOf<String?>(null)
        private set
    var passwordError by mutableStateOf<String?>(null)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var canUseBiometric by mutableStateOf(false)
        private set

    // true = correo verificado (va al Inicio); false = falta verificar el correo.
    private val _loggedIn = Channel<Boolean>(Channel.BUFFERED)
    val loggedIn = _loggedIn.receiveAsFlow()

    init {
        viewModelScope.launch {
            canUseBiometric = session.hasStoredSession() && session.biometricEnabled.first()
        }
    }

    fun submit() {
        emailError = validateEmail(email)
        passwordError = if (password.isEmpty()) "Ingresa tu contraseña" else null
        if (emailError != null || passwordError != null) return
        authenticate { session.login(email.trim(), password) }
    }

    fun onBiometricSuccess() {
        viewModelScope.launch {
            if (session.unlockSession()) {
                _loggedIn.send(true)
            } else {
                errorMessage = "Tu sesión expiró. Ingresa con tu correo y contraseña."
            }
        }
    }

    fun onBiometricError(message: String) {
        errorMessage = message
    }

    private fun authenticate(block: suspend () -> Boolean) {
        if (loading) return
        viewModelScope.launch {
            loading = true
            errorMessage = null
            try {
                _loggedIn.send(block())
            } catch (e: AuthException) {
                errorMessage = e.message
            } catch (e: Exception) {
                errorMessage = GENERIC_ERROR
            } finally {
                loading = false
            }
        }
    }

    companion object {
        val Factory = appViewModelFactory { c, handle -> LoginViewModel(c.sessionRepository, handle) }
    }
}

class RegisterViewModel(
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
    private var _email by savedStateHandle.saveable { mutableStateOf("") }
    var email: String
        get() = _email
        set(value) {
            _email = value
            emailError = null
        }
    private var _phone by savedStateHandle.saveable { mutableStateOf("") }
    var phone: String
        get() = _phone
        set(value) {
            _phone = value
            phoneError = null
        }
    private var _acceptedTerms by savedStateHandle.saveable { mutableStateOf(false) }
    var acceptedTerms: Boolean
        get() = _acceptedTerms
        set(value) {
            _acceptedTerms = value
            errorMessage = null
        }
    private var _password by mutableStateOf("")
    var password: String
        get() = _password
        set(value) {
            _password = value
            passwordError = null
        }
    private var _confirmPassword by mutableStateOf("")
    var confirmPassword: String
        get() = _confirmPassword
        set(value) {
            _confirmPassword = value
            confirmError = null
        }

    var nameError by mutableStateOf<String?>(null)
        private set
    var emailError by mutableStateOf<String?>(null)
        private set
    var phoneError by mutableStateOf<String?>(null)
        private set
    var passwordError by mutableStateOf<String?>(null)
        private set
    var confirmError by mutableStateOf<String?>(null)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var loading by mutableStateOf(false)
        private set

    private val _registered = Channel<Unit>(Channel.BUFFERED)
    val registered = _registered.receiveAsFlow()

    fun submit() {
        nameError = if (name.isBlank()) "Ingresa tu nombre" else null
        emailError = validateEmail(email)
        phoneError = if (phone.isNotBlank() && phone.count(Char::isDigit) < 8) "Revisa el número de teléfono" else null
        passwordError = validatePassword(password)
        confirmError = if (confirmPassword != password) "Las contraseñas no coinciden" else null
        errorMessage = if (!acceptedTerms) "Debes aceptar los términos de servicio para continuar." else null
        if (listOf(nameError, emailError, phoneError, passwordError, confirmError, errorMessage).any { it != null }) return

        viewModelScope.launch {
            loading = true
            try {
                session.register(name.trim(), email.trim(), phone.trim(), password)
                _registered.send(Unit)
            } catch (e: AuthException) {
                errorMessage = e.message
            } catch (e: Exception) {
                errorMessage = GENERIC_ERROR
            } finally {
                loading = false
            }
        }
    }

    companion object {
        val Factory = appViewModelFactory { c, handle -> RegisterViewModel(c.sessionRepository, handle) }
    }
}

class RecoveryViewModel(
    private val session: SessionRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    // El correo llega tipado desde el login para no tener que escribirlo de nuevo.
    private var _email by savedStateHandle.saveable { mutableStateOf(savedStateHandle.toRoute<RecoveryRoute>().email) }
    var email: String
        get() = _email
        set(value) {
            _email = value
            emailError = null
        }
    var emailError by mutableStateOf<String?>(null)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var sent by savedStateHandle.saveable { mutableStateOf(false) }
        private set

    fun submit() {
        emailError = validateEmail(email)
        if (emailError != null) return
        viewModelScope.launch {
            loading = true
            errorMessage = null
            try {
                session.requestPasswordReset(email.trim())
                sent = true
            } catch (e: AuthException) {
                errorMessage = e.message
            } catch (e: Exception) {
                errorMessage = GENERIC_ERROR
            } finally {
                loading = false
            }
        }
    }

    fun editEmail() {
        sent = false
    }

    companion object {
        val Factory = appViewModelFactory { c, handle -> RecoveryViewModel(c.sessionRepository, handle) }
    }
}

/**
 * Pantalla "Verifica tu correo": la cuenta no entra al Inicio hasta abrir el enlace
 * que Firebase envía al correo. Así se confirma que el correo es realmente del usuario.
 */
class VerifyEmailViewModel(private val session: SessionRepository) : ViewModel() {
    var email by mutableStateOf("")
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var infoMessage by mutableStateOf<String?>(null)
        private set
    var checking by mutableStateOf(false)
        private set
    var resending by mutableStateOf(false)
        private set

    // Espera entre reenvíos para no gastar el límite de correos de Firebase.
    var resendSeconds by mutableStateOf(0)
        private set

    private val _verified = Channel<Unit>(Channel.BUFFERED)
    val verified = _verified.receiveAsFlow()

    init {
        viewModelScope.launch { email = session.profile.first().email }
        startResendCountdown()
    }

    fun checkVerified() {
        if (checking) return
        viewModelScope.launch {
            checking = true
            errorMessage = null
            infoMessage = null
            try {
                if (session.checkEmailVerified()) {
                    _verified.send(Unit)
                } else {
                    errorMessage = "Todavía no aparece verificado. Abre el enlace del correo y vuelve a intentar."
                }
            } catch (e: AuthException) {
                errorMessage = e.message
            } finally {
                checking = false
            }
        }
    }

    fun resend() {
        if (resending || resendSeconds > 0) return
        viewModelScope.launch {
            resending = true
            errorMessage = null
            infoMessage = null
            try {
                session.resendVerificationEmail()
                infoMessage = "Te enviamos un nuevo enlace. Revisa también la carpeta de spam."
                startResendCountdown()
            } catch (e: AuthException) {
                errorMessage = e.message
            } finally {
                resending = false
            }
        }
    }

    fun logout(onDone: () -> Unit) {
        viewModelScope.launch {
            session.logout()
            onDone()
        }
    }

    private fun startResendCountdown() {
        viewModelScope.launch {
            resendSeconds = RESEND_WAIT_SECONDS
            while (resendSeconds > 0) {
                delay(1_000)
                resendSeconds--
            }
        }
    }

    companion object {
        private const val RESEND_WAIT_SECONDS = 60

        val Factory = appViewModelFactory { c, _ -> VerifyEmailViewModel(c.sessionRepository) }
    }
}
