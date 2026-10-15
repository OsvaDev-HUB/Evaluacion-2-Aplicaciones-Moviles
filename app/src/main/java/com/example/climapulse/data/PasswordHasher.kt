package com.example.climapulse.data

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Convierte la contraseña en un hash PBKDF2 con una sal aleatoria por usuario.
 * Así, aunque alguien copie la base de datos, no puede leer las contraseñas.
 */
object PasswordHasher {
    private const val ITERATIONS = 60_000
    private const val KEY_LENGTH = 256

    fun newSalt(): String {
        val salt = ByteArray(16)
        SecureRandom().nextBytes(salt)
        return Base64.encodeToString(salt, Base64.NO_WRAP)
    }

    fun hash(password: String, salt: String): String {
        val spec = PBEKeySpec(password.toCharArray(), Base64.decode(salt, Base64.NO_WRAP), ITERATIONS, KEY_LENGTH)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    fun matches(password: String, salt: String, expectedHash: String): Boolean {
        // MessageDigest.isEqual compara en tiempo constante.
        return MessageDigest.isEqual(hash(password, salt).toByteArray(), expectedHash.toByteArray())
    }
}
