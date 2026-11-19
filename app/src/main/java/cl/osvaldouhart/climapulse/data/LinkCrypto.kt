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
 * La llave sale del PIN de 6 dígitos que muestra el nodo sensor: solo quien conoce
 * el PIN puede leer o enviar mensajes. GCM además detecta si un mensaje fue alterado.
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

    /** Lanza una excepción si el PIN no coincide o el mensaje fue modificado. */
    fun decrypt(line: String): String {
        // El decodificador MIME ignora saltos de línea o espacios que puedan venir del ESP8266.
        val bytes = Base64.getMimeDecoder().decode(line)
        val iv = bytes.copyOfRange(0, 12)
        val encrypted = bytes.copyOfRange(12, bytes.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        return String(cipher.doFinal(encrypted), Charsets.UTF_8)
    }

    private companion object {
        val SALT = "climapulse-enlace-wifi".toByteArray()
    }
}
