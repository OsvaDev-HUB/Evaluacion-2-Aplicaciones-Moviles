package cl.osvaldouhart.climapulse.data

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Cifra los mensajes del enlace WiFi con AES-GCM.
 * La llave sale de la clave del sensor (config.h del ESP8266): solo quien la conoce
 * puede leer o enviar mensajes. GCM además detecta si un mensaje fue alterado.
 */
class LinkCrypto(pin: String) {
    private val key: SecretKeySpec

    init {
        val spec = PBEKeySpec(pin.toCharArray(), SALT, 20_000, 256)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        key = SecretKeySpec(bytes, "AES")
    }

    /** Devuelve una línea de texto: IV + mensaje cifrado, en Base64. */
    fun encrypt(text: String): String {
        val iv = ByteArray(12)
        SecureRandom().nextBytes(iv)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val encrypted = cipher.doFinal(text.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(iv + encrypted)
    }

    /** Lanza una excepción si la clave no coincide o el mensaje fue modificado. */
    fun decrypt(line: String): String {
        // El decodificador MIME ignora saltos de línea o espacios que puedan venir del ESP8266.
        val bytes = Base64.getMimeDecoder().decode(line)
        val iv = bytes.copyOfRange(0, 12)
        val encrypted = bytes.copyOfRange(12, bytes.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        return String(cipher.doFinal(encrypted), Charsets.UTF_8)
    }

    companion object {
        private val SALT = "climapulse-enlace-wifi".toByteArray()
        const val MIN_KEY_LENGTH = 10
        const val MAX_KEY_LENGTH = 32

        /**
         * Devuelve qué le falta a la clave, o null si sirve.
         * Con 6 dígitos hay solo un millón de opciones: alguien que grabe el tráfico las prueba
         * todas en segundos. Con 10 caracteres entre letras y números son más de 10^17.
         * El ESP8266 aplica la misma regla al compilar (sensores.ino).
         */
        fun keyProblem(key: String): String? = when {
            key.length < MIN_KEY_LENGTH -> "La clave del sensor tiene al menos $MIN_KEY_LENGTH caracteres."
            key.length > MAX_KEY_LENGTH -> "La clave del sensor tiene como máximo $MAX_KEY_LENGTH caracteres."
            key.any { it.isWhitespace() } -> "La clave del sensor no lleva espacios."
            // Solo ASCII: así la clave se convierte en los mismos bytes en Android y en el ESP8266.
            key.any { it.code !in 33..126 } -> "La clave del sensor usa letras sin tilde ni ñ, números y símbolos."
            key.none { it in 'a'..'z' || it in 'A'..'Z' } || key.none { it in '0'..'9' } ->
                "La clave del sensor mezcla letras y números."
            else -> null
        }
    }
}
