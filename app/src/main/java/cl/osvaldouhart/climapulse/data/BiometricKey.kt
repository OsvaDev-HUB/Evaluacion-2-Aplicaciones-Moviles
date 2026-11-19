package cl.osvaldouhart.climapulse.data

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

// Llave AES guardada en el Android Keystore (hardware seguro del teléfono). Nunca sale de ahí
// y solo se puede usar justo después de poner una huella válida. Si se agrega una huella nueva
// en Android, la llave se invalida y hay que volver a activar el desbloqueo con contraseña.
object BiometricKey {
    private const val KEYSTORE = "AndroidKeyStore"
    private const val ALIAS = "climapulse_huella"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    // Crea una llave nueva (reemplaza la anterior) y devuelve el cifrador listo para cifrar.
    fun encryptCipher(): Cipher {
        deleteKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, createKey())
        return cipher
    }

    // Cifrador para descifrar con la llave existente. Devuelve null si la llave ya no sirve
    // (se agregó o borró una huella en Android) o si no existe.
    fun decryptCipher(iv: ByteArray): Cipher? {
        val key = loadKey() ?: return null
        return try {
            Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv)) }
        } catch (e: KeyPermanentlyInvalidatedException) {
            deleteKey()
            null
        }
    }

    fun deleteKey() {
        runCatching { keyStore().deleteEntry(ALIAS) }
    }

    private fun createKey(): SecretKey {
        val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
            .apply {
                // 0 segundos = hay que poner la huella cada vez que se usa la llave.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
                } else {
                    @Suppress("DEPRECATION")
                    setUserAuthenticationValidityDurationSeconds(-1)
                }
            }
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).run {
            init(spec)
            generateKey()
        }
    }

    private fun loadKey(): SecretKey? = runCatching { keyStore().getKey(ALIAS, null) as? SecretKey }.getOrNull()

    private fun keyStore(): KeyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
}
