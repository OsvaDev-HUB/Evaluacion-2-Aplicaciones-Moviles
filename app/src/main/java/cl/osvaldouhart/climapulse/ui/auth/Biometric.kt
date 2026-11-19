package cl.osvaldouhart.climapulse.ui.auth

import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import javax.crypto.Cipher

// Diálogo de huella del sistema. Se usa BIOMETRIC_STRONG porque solo las huellas (o rostros)
// de clase fuerte pueden liberar una llave del Keystore a través del CryptoObject.
object BiometricAuth {
    fun isAvailable(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

    // onSuccess recibe el cifrador ya desbloqueado por la huella; solo ahí se puede usar.
    fun authenticate(
        activity: FragmentActivity,
        cipher: Cipher,
        title: String,
        subtitle: String,
        negativeText: String,
        onSuccess: (Cipher) -> Unit,
        onError: (String) -> Unit
    ) {
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    val unlocked = result.cryptoObject?.cipher
                    if (unlocked != null) onSuccess(unlocked) else onError(FAILED)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    val cancelledByUser = errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                        errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                        errorCode == BiometricPrompt.ERROR_CANCELED
                    if (!cancelledByUser) onError(FAILED)
                }
            }
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setNegativeButtonText(negativeText)
                .setAllowedAuthenticators(BIOMETRIC_STRONG)
                .build(),
            BiometricPrompt.CryptoObject(cipher)
        )
    }

    private const val FAILED = "No pudimos verificar tu identidad. Intenta de nuevo o usa tu contraseña."
}

// Muestra el diálogo para activar el desbloqueo con huella. cipher viene de
// SessionRepository.biometricEnrollCipher(); si es null el teléfono no pudo crear la llave.
fun Context.enrollBiometric(cipher: Cipher?, onSuccess: (Cipher) -> Unit, onError: (String) -> Unit) {
    val activity = findFragmentActivity() ?: return
    if (cipher == null) {
        onError("No pudimos activar la huella en este teléfono. Revisa que tengas una huella registrada en Android.")
        return
    }
    BiometricAuth.authenticate(
        activity = activity,
        cipher = cipher,
        title = "Activar desbloqueo con huella",
        subtitle = "Confirma tu identidad",
        negativeText = "Cancelar",
        onSuccess = onSuccess,
        onError = onError
    )
}

fun Context.findFragmentActivity(): FragmentActivity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is FragmentActivity) return current
        current = current.baseContext
    }
    return null
}
