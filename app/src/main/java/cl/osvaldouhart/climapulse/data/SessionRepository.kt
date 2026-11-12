package cl.osvaldouhart.climapulse.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.userProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/** Tema de la app elegido en Perfil. SYSTEM sigue el ajuste del teléfono. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Destino que decide el Router detrás de la Splash Screen. */
sealed interface StartDestination {
    data object Onboarding : StartDestination
    data class Login(val biometricUnlock: Boolean) : StartDestination
    data object VerifyEmail : StartDestination
    data object Home : StartDestination
}

/**
 * Sesión y preferencias. Las cuentas las maneja Firebase Authentication: la contraseña
 * nunca pasa por nuestra base de datos y Firebase limita los intentos fallidos.
 * El nombre y el teléfono se guardan en Firestore, en el documento users/{uid}.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionRepository(
    private val dataStore: DataStore<Preferences>,
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) {
    init {
        // Los correos de verificación y de recuperación llegan en español.
        auth.setLanguageCode("es")
    }

    /** Usuario con sesión abierta (null si no hay sesión). Se actualiza solo al entrar o salir. */
    private val currentUser: Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    val profile: Flow<UserProfile> = currentUser.flatMapLatest { user ->
        if (user == null) flowOf(UserProfile("", "", "")) else userDocument(user)
    }

    val biometricEnabled: Flow<Boolean> = dataStore.data.map { it[BIOMETRIC_ENABLED] ?: false }
    val notificationsEnabled: Flow<Boolean> = dataStore.data.map { it[NOTIFICATIONS_ENABLED] ?: false }
    val autoRefreshEnabled: Flow<Boolean> = dataStore.data.map { it[AUTO_REFRESH_ENABLED] ?: true }

    /**
     * true si el usuario que tiene la sesión abierta ya vio los permisos y el tutorial.
     * Se guarda por cuenta (uid): cada cuenta nueva los ve en su primer ingreso.
     */
    val introDone: Flow<Boolean> = combine(currentUser, dataStore.data) { user, prefs ->
        user != null && prefs[introKey(user.uid)] == true
    }

    val themeMode: Flow<ThemeMode> = dataStore.data.map { prefs ->
        prefs[THEME_MODE]?.let { saved -> ThemeMode.entries.firstOrNull { it.name == saved } } ?: ThemeMode.SYSTEM
    }

    /** true si el usuario marcó "No volver a mostrar" en la bienvenida. */
    val welcomeHidden: Flow<Boolean> = dataStore.data.map { it[WELCOME_HIDDEN] ?: false }

    /** La bienvenida sale en cada apertura, salvo que el usuario la haya ocultado. */
    suspend fun resolveStartDestination(): StartDestination {
        removeLegacyKeys()
        val prefs = dataStore.data.first()
        if (prefs[WELCOME_HIDDEN] != true) return StartDestination.Onboarding
        return resolveAfterWelcome()
    }

    /** A dónde ir después de la bienvenida: login (con o sin huella), verificar correo o Inicio. */
    suspend fun resolveAfterWelcome(): StartDestination {
        val user = auth.currentUser ?: return StartDestination.Login(biometricUnlock = false)
        if (!user.isEmailVerified) {
            // Puede que lo haya verificado desde otro dispositivo: se consulta, pero sin trabar el arranque.
            withTimeoutOrNull(3_000) { runCatching { user.reload().await() } }
            if (auth.currentUser?.isEmailVerified != true) return StartDestination.VerifyEmail
        }
        return if (dataStore.data.first()[BIOMETRIC_ENABLED] == true) {
            StartDestination.Login(biometricUnlock = true)
        } else {
            StartDestination.Home
        }
    }

    suspend fun setWelcomeHidden(hidden: Boolean) {
        dataStore.edit { it[WELCOME_HIDDEN] = hidden }
    }

    /** Devuelve true si la cuenta ya tiene el correo verificado. */
    suspend fun login(email: String, password: String): Boolean {
        val user = try {
            auth.signInWithEmailAndPassword(email, password).await().user
        } catch (e: Exception) {
            throw e.toAuthException(invalidCredentials = "El correo o la contraseña no coinciden.")
        } ?: throw AuthException(GENERIC_MESSAGE)
        return user.isEmailVerified
    }

    suspend fun register(name: String, email: String, phone: String, password: String) {
        validateNewPassword(password)?.let { throw AuthException(it) }
        val user = try {
            auth.createUserWithEmailAndPassword(email, password).await().user
        } catch (e: Exception) {
            throw e.toAuthException(invalidCredentials = "Revisa el formato del correo.")
        } ?: throw AuthException(GENERIC_MESSAGE)

        // El nombre también queda en Firebase Auth para que aparezca en los correos.
        runCatching { user.updateProfile(userProfileChangeRequest { displayName = name }).await() }
        saveUserDocument(user.uid, name, phone)
        // Si el envío falla, el usuario puede pedir que se reenvíe desde la pantalla de verificación.
        runCatching { user.sendEmailVerification().await() }
    }

    suspend fun resendVerificationEmail() {
        val user = auth.currentUser ?: throw AuthException(SESSION_EXPIRED)
        try {
            user.sendEmailVerification().await()
        } catch (e: Exception) {
            throw e.toAuthException(invalidCredentials = GENERIC_MESSAGE)
        }
    }

    /** Vuelve a consultar a Firebase si el usuario ya abrió el enlace del correo. */
    suspend fun checkEmailVerified(): Boolean {
        val user = auth.currentUser ?: throw AuthException(SESSION_EXPIRED)
        try {
            user.reload().await()
        } catch (e: Exception) {
            throw e.toAuthException(invalidCredentials = SESSION_EXPIRED)
        }
        return auth.currentUser?.isEmailVerified == true
    }

    /**
     * Envía el enlace para crear una nueva contraseña. Si el correo no tiene cuenta no se avisa,
     * así nadie puede usar esta pantalla para averiguar qué correos están registrados.
     */
    suspend fun requestPasswordReset(email: String) {
        try {
            auth.sendPasswordResetEmail(email).await()
        } catch (e: FirebaseAuthInvalidUserException) {
            // El correo no tiene cuenta: se responde igual que si existiera.
        } catch (e: Exception) {
            throw e.toAuthException(invalidCredentials = "Revisa el formato del correo.")
        }
    }

    /** Llamado tras una autenticación biométrica exitosa. */
    fun unlockSession(): Boolean = auth.currentUser != null

    fun hasStoredSession(): Boolean = auth.currentUser != null

    suspend fun logout() {
        auth.signOut()
        dataStore.edit { it[BIOMETRIC_ENABLED] = false }
    }

    /** Cambia nombre y teléfono. El correo se cambiará en la sección de seguridad de la cuenta. */
    suspend fun updateProfile(name: String, phone: String) {
        val user = auth.currentUser ?: throw AuthException(SESSION_EXPIRED)
        try {
            user.updateProfile(userProfileChangeRequest { displayName = name }).await()
        } catch (e: Exception) {
            throw e.toAuthException(invalidCredentials = SESSION_EXPIRED)
        }
        saveUserDocument(user.uid, name, phone)
    }

    suspend fun setBiometricEnabled(enabled: Boolean) = dataStore.edit { it[BIOMETRIC_ENABLED] = enabled }
    suspend fun setNotificationsEnabled(enabled: Boolean) = dataStore.edit { it[NOTIFICATIONS_ENABLED] = enabled }
    suspend fun setAutoRefreshEnabled(enabled: Boolean) = dataStore.edit { it[AUTO_REFRESH_ENABLED] = enabled }
    suspend fun setIntroDone() {
        val uid = auth.currentUser?.uid ?: return
        dataStore.edit { it[introKey(uid)] = true }
    }

    suspend fun setThemeMode(mode: ThemeMode) = dataStore.edit { it[THEME_MODE] = mode.name }

    /** Nombre y teléfono desde Firestore. Si no hay internet se usa la copia local de Firestore. */
    private fun userDocument(user: FirebaseUser): Flow<UserProfile> = callbackFlow {
        val fallback = UserProfile(user.displayName.orEmpty(), user.email.orEmpty(), "")
        // Firestore responde primero con su copia local (aunque no haya internet) y luego con la del servidor.
        val registration = firestore.collection(USERS).document(user.uid).addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null) {
                trySend(fallback)
                return@addSnapshotListener
            }
            trySend(
                UserProfile(
                    name = snapshot.getString("nombre") ?: fallback.name,
                    email = user.email.orEmpty(),
                    phone = snapshot.getString("telefono").orEmpty()
                )
            )
        }
        awaitClose { registration.remove() }
    }

    private fun saveUserDocument(uid: String, name: String, phone: String) {
        // Sin await: Firestore lo guarda primero en el teléfono y lo sube apenas haya conexión.
        firestore.collection(USERS).document(uid)
            .set(mapOf("nombre" to name, "telefono" to phone), SetOptions.merge())
    }

    /** Borra lo que guardaba la versión anterior (sesión local y datos del usuario en texto plano). */
    private suspend fun removeLegacyKeys() {
        val prefs = dataStore.data.first()
        if (LEGACY_KEYS.none { prefs.contains(it) }) return
        dataStore.edit { editable -> LEGACY_KEYS.forEach { editable.remove(it) } }
    }

    /** Traduce los errores de Firebase a mensajes claros, sin mostrar códigos técnicos. */
    private fun Exception.toAuthException(invalidCredentials: String): Exception = when (this) {
        is AuthException -> this
        is FirebaseAuthWeakPasswordException -> AuthException("La contraseña es muy débil. Usa al menos 8 caracteres.")
        is FirebaseAuthUserCollisionException -> AuthException("Ya existe una cuenta con ese correo.")
        is FirebaseAuthInvalidUserException -> AuthException(invalidCredentials)
        is FirebaseAuthInvalidCredentialsException -> AuthException(invalidCredentials)
        is FirebaseTooManyRequestsException -> AuthException("Demasiados intentos. Espera unos minutos e intenta de nuevo.")
        is FirebaseNetworkException -> AuthException("No hay conexión a internet. Revisa tu conexión e intenta de nuevo.")
        else -> AuthException(GENERIC_MESSAGE)
    }

    private companion object {
        const val USERS = "users"
        const val GENERIC_MESSAGE = "No pudimos completar la acción. Intenta de nuevo en un momento."
        const val SESSION_EXPIRED = "Tu sesión expiró. Ingresa de nuevo con tu correo y contraseña."

        val WELCOME_HIDDEN = booleanPreferencesKey("welcome_hidden")
        val BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val AUTO_REFRESH_ENABLED = booleanPreferencesKey("auto_refresh_enabled")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        fun introKey(uid: String) = booleanPreferencesKey("intro_done_$uid")

        val LEGACY_KEYS = listOf(
            stringPreferencesKey("auth_token_encrypted"),
            longPreferencesKey("auth_token_expiry"),
            stringPreferencesKey("user_name"),
            stringPreferencesKey("user_email"),
            stringPreferencesKey("user_phone")
        )
    }
}

/** Regla de contraseña para cuentas nuevas. Devuelve el error o null si la contraseña sirve. */
fun validateNewPassword(password: String): String? = when {
    password.length < 8 -> "Usa al menos 8 caracteres"
    password.length > 128 -> "Usa como máximo 128 caracteres"
    password.toSet().size < 3 -> "Evita repetir el mismo carácter"
    password.lowercase() in COMMON_PASSWORDS -> "Esa contraseña es muy común, elige otra"
    else -> null
}

// Las más usadas según las filtraciones públicas: Firebase no las bloquea por sí solo.
private val COMMON_PASSWORDS = setOf(
    "12345678", "123456789", "1234567890", "87654321", "password", "password1", "contraseña",
    "qwertyui", "qwerty123", "abcd1234", "abc12345", "iloveyou", "11223344", "climapulse"
)
