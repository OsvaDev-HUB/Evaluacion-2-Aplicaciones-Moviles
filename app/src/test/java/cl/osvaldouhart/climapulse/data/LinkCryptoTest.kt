package cl.osvaldouhart.climapulse.data

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LinkCryptoTest {
    private val mensaje = """{"tipo":"lectura","lectura":{"temperature":22.5,"humidity":48,"light":420,"timestampMillis":0}}"""

    @Test
    fun cifraYDescifraConElMismoPin() {
        val crypto = LinkCrypto("123456")
        assertEquals(mensaje, crypto.decrypt(crypto.encrypt(mensaje)))
    }

    @Test
    fun cadaMensajeUsaUnIvDistinto() {
        // Si el IV se repitiera, dos mensajes iguales se verían iguales cifrados.
        val crypto = LinkCrypto("123456")
        assertNotEquals(crypto.encrypt(mensaje), crypto.encrypt(mensaje))
    }

    @Test
    fun unPinDistintoNoPuedeLeerElMensaje() {
        val linea = LinkCrypto("123456").encrypt(mensaje)
        assertThrows(Exception::class.java) { LinkCrypto("654321").decrypt(linea) }
    }

    @Test
    fun detectaSiElMensajeFueModificado() {
        val crypto = LinkCrypto("123456")
        val bytes = Base64.getDecoder().decode(crypto.encrypt(mensaje))
        bytes[bytes.size - 20] = (bytes[bytes.size - 20].toInt() xor 1).toByte()
        val alterado = Base64.getEncoder().encodeToString(bytes)
        assertThrows(Exception::class.java) { crypto.decrypt(alterado) }
    }

    @Test
    fun laLineaCifradaNoTieneSaltosDeLinea() {
        // El enlace manda un mensaje por línea, así que no puede haber saltos dentro.
        val linea = LinkCrypto("123456").encrypt(mensaje.repeat(10))
        assertEquals(false, linea.contains('\n'))
    }
}
