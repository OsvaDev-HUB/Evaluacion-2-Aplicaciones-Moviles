package com.example.climapulse.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Tema de la app elegido en Perfil. SYSTEM sigue el ajuste del teléfono. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Destino que decide el Router detrás de la Splash Screen. */
sealed interface StartDestination {
    data object Onboarding : StartDestination
    data class Login(val biometricUnlock: Boolean) : StartDestination
    data object Home : StartDestination
}

/**
 * Sesión, onboarding, perfil y preferencias. Las cuentas se guardan en SQLite (tabla usuarios)
 * con la contraseña convertida en hash PBKDF2. El token de sesión se guarda cifrado.
 */
class SessionRepository(
    private val dataStore: DataStore<Preferences>,
    private val cipher: TokenCipher,
    private val database: AppDatabase
) {
    // Control de acceso: tras 5 intentos fallidos se bloquea el login por 30 segundos.
    private var failedAttempts = 0
    private var blockedUntil = 0L

    val profile: Flow<UserProfile> = dataStore.data.map {
        UserProfile(
            name = it[USER_NAME].orEmpty(),
            email = it[USER_EMAIL].orEmpty(),
            phone = it[USER_PHONE].orEmpty()
        )
    }
    val biometricEnabled: Flow<Boolean> = dataStore.data.map { it[BIOMETRIC_ENABLED] ?: false }
    val notificationsEnabled: Flow<Boolean> = dataStore.data.map { it[NOTIFICATIONS_ENABLED] ?: false }
    val autoRefreshEnabled: Flow<Boolean> = dataStore.data.map { it[AUTO_REFRESH_ENABLED] ?: true }

    /**
     * true si el usuario que tiene la sesión abierta ya vio los permisos y el tutorial.
     * Se guarda por correo: cada cuenta nueva los ve en su primer ingreso.
     */
    val introDone: Flow<Boolean> = dataStore.data.map { prefs ->
        val email = prefs[USER_EMAIL].orEmpty()
        prefs[introKey(email)] ?: false
    }

    val themeMode: Flow<ThemeMode> = dataStore.data.map { prefs ->
        prefs[THEME_MODE]?.let { saved -> ThemeMode.entries.firstOrNull { it.name == saved } } ?: ThemeMode.SYSTEM
    }

    suspend fun resolveStartDestination(): StartDestination {
        val prefs = dataStore.data.first()
        if (prefs[ONBOARDING_DONE] != true) return StartDestination.Onboarding
        val token = prefs[AUTH_TOKEN]?.let(cipher::decrypt)
        val expiry = prefs[TOKEN_EXPIRY] ?: 0L
        val validSession = token != null && expiry > System.currentTimeMillis()
        return when {
            !validSession -> StartDestination.Login(biometricUnlock = false)
            prefs[BIOMETRIC_ENABLED] == true -> StartDestination.Login(biometricUnlock = true)
            else -> StartDestination.Home
        }
    }

    suspend fun completeOnboarding(notificationsGranted: Boolean) {
        dataStore.edit {
            it[ONBOARDING_DONE] = true
            it[NOTIFICATIONS_ENABLED] = notificationsGranted
        }
    }

    suspend fun login(email: String, password: String) {
        val now = System.currentTimeMillis()
        if (now < blockedUntil) {
            val seconds = (blockedUntil - now) / 1000 + 1
            throw AuthException("Demasiados intentos. Espera $seconds segundos e intenta de nuevo.")
        }
        val user = withContext(Dispatchers.IO) { database.findUser(email.lowercase()) }
        val valid = user != null && withContext(Dispatchers.Default) {
            PasswordHasher.matches(password, user.salt, user.passwordHash)
        }
        if (!valid || user == null) {
            failedAttempts++
            if (failedAttempts >= MAX_ATTEMPTS) {
                failedAttempts = 0
                blockedUntil = now + BLOCK_MILLIS
            }
            throw AuthException("El correo o la contraseña no coinciden.")
        }
        failedAttempts = 0
        storeSession(email = user.email, name = user.name, phone = user.phone)
    }

    suspend fun register(name: String, email: String, phone: String, password: String) {
        if (password.length < 6) throw AuthException("La contraseña debe tener al menos 6 caracteres.")
        val cleanEmail = email.lowercase()
        val saved = withContext(Dispatchers.IO) {
            if (database.findUser(cleanEmail) != null) return@withContext false
            val salt = PasswordHasher.newSalt()
            database.insertUser(UserRow(name, cleanEmail, phone, PasswordHasher.hash(password, salt), salt))
        }
        if (!saved) throw AuthException("Ya existe una cuenta con ese correo.")
        storeSession(email = cleanEmail, name = name, phone = phone)
    }

    /** Sin servidor de correo: solo se valida que la cuenta exista en este teléfono. */
    suspend fun requestPasswordReset(email: String) {
        val user = withContext(Dispatchers.IO) { database.findUser(email.lowercase()) }
        if (user == null) throw AuthException("No hay una cuenta registrada con ese correo.")
    }

    /** Llamado tras una autenticación biométrica exitosa: renueva la vigencia del token. */
    suspend fun unlockSession(): Boolean {
        val prefs = dataStore.data.first()
        if (prefs[AUTH_TOKEN]?.let(cipher::decrypt) == null) return false
        dataStore.edit { it[TOKEN_EXPIRY] = System.currentTimeMillis() + SESSION_DURATION }
        return true
    }

    suspend fun hasStoredSession(): Boolean =
        dataStore.data.first()[AUTH_TOKEN]?.let(cipher::decrypt) != null

    suspend fun logout() {
        dataStore.edit {
            it.remove(AUTH_TOKEN)
            it.remove(TOKEN_EXPIRY)
            it[BIOMETRIC_ENABLED] = false
        }
    }

    suspend fun updateProfile(profile: UserProfile) {
        val oldEmail = dataStore.data.first()[USER_EMAIL].orEmpty()
        val newEmail = profile.email.lowercase()
        val updated = withContext(Dispatchers.IO) {
            database.updateUser(oldEmail, profile.name, newEmail, profile.phone)
        }
        if (!updated) throw AuthException("No se pudo guardar. Puede que ese correo ya esté en uso.")
        dataStore.edit {
            it[USER_NAME] = profile.name
            it[USER_EMAIL] = newEmail
            it[USER_PHONE] = profile.phone
        }
    }

    suspend fun setBiometricEnabled(enabled: Boolean) = dataStore.edit { it[BIOMETRIC_ENABLED] = enabled }
    suspend fun setNotificationsEnabled(enabled: Boolean) = dataStore.edit { it[NOTIFICATIONS_ENABLED] = enabled }
    suspend fun setAutoRefreshEnabled(enabled: Boolean) = dataStore.edit { it[AUTO_REFRESH_ENABLED] = enabled }
    suspend fun setIntroDone() = dataStore.edit {
        val email = it[USER_EMAIL].orEmpty()
        it[introKey(email)] = true
    }

    suspend fun setThemeMode(mode: ThemeMode) = dataStore.edit { it[THEME_MODE] = mode.name }

    private suspend fun storeSession(email: String, name: String, phone: String? = null) {
        val token = UUID.randomUUID().toString()
        dataStore.edit {
            it[AUTH_TOKEN] = cipher.encrypt(token)
            it[TOKEN_EXPIRY] = System.currentTimeMillis() + SESSION_DURATION
            it[USER_EMAIL] = email
            it[USER_NAME] = name
            if (phone != null) it[USER_PHONE] = phone
        }
    }

    private companion object {
        const val MAX_ATTEMPTS = 5
        const val BLOCK_MILLIS = 30_000L
        val SESSION_DURATION = TimeUnit.DAYS.toMillis(7)

        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val AUTH_TOKEN = stringPreferencesKey("auth_token_encrypted")
        val TOKEN_EXPIRY = longPreferencesKey("auth_token_expiry")
        val USER_NAME = stringPreferencesKey("user_name")
        val USER_EMAIL = stringPreferencesKey("user_email")
        val USER_PHONE = stringPreferencesKey("user_phone")
        val BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val AUTO_REFRESH_ENABLED = booleanPreferencesKey("auto_refresh_enabled")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        fun introKey(email: String) = booleanPreferencesKey("intro_done_$email")
    }
}
