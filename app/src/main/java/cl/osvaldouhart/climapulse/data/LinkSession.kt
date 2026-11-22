package cl.osvaldouhart.climapulse.data

import java.security.SecureRandom
import kotlinx.serialization.Serializable

// Reglas del protocolo del enlace WiFi (versión 2), separadas de los sockets para poder probarlas.
//
// 1. La app manda "hola" con un reto aleatorio.
// 2. El nodo responde "bienvenida" con el mismo reto y un número de sesión nuevo.
// 3. Desde ahí, cada mensaje lleva la sesión y un contador "n" que siempre sube.
//
// Así, un mensaje grabado y reenviado después (ataque de repetición) se rechaza:
// o es de otra sesión, o su contador ya se usó. AES-GCM (LinkCrypto) cubre que nadie
// sin la clave del sensor pueda leer o cambiar el contenido.

/** Mensaje que viaja entre la app y el nodo sensor (JSON cifrado, uno por línea). */
@Serializable
data class LinkMessage(
    val tipo: String,
    val nombre: String? = null,
    val lectura: EnvironmentReading? = null,
    val actuadores: Actuators? = null,
    val reto: String? = null,
    val sesion: String? = null,
    val n: Long? = null,
    val version: Int? = null,
    // Lecturas que el nodo guardó mientras no había teléfono conectado.
    val lecturas: List<StoredReading>? = null,
    val pendientes: Int? = null,
    val quedan: Int? = null,
    val cantidad: Int? = null,
    // Aviso que el nodo manda por su cuenta (falla del DHT11, señal débil, memoria llena).
    val codigo: String? = null,
    val mensaje: String? = null
)

/** Lectura guardada en el nodo. "edad" son los milisegundos desde que se midió. */
@Serializable
data class StoredReading(
    val temperature: Float,
    val humidity: Float,
    val light: Float,
    val edad: Long
)

class LinkSession(random: SecureRandom = SecureRandom()) {
    /** Reto de esta conexión: el nodo debe devolverlo igual en la bienvenida. */
    val challenge: String = ByteArray(8).also(random::nextBytes).joinToString("") { "%02x".format(it) }

    private var sessionId: String? = null
    private var lastReceived = 0L
    private var lastSent = 0L

    /** Cantidad de mensajes rechazados por repetidos o de otra sesión. */
    var rejected = 0
        private set

    val isOpen: Boolean get() = sessionId != null

    fun hello(deviceName: String) = LinkMessage(tipo = "hola", nombre = deviceName, reto = challenge)

    /** Acepta la bienvenida solo si trae nuestro reto: así no puede ser una respuesta vieja grabada. */
    fun acceptWelcome(message: LinkMessage): Boolean {
        val session = message.sesion
        val counter = message.n
        if (message.tipo != "bienvenida" || message.reto != challenge || session.isNullOrBlank() || counter == null) {
            return false
        }
        sessionId = session
        lastReceived = counter
        return true
    }

    /** true si el mensaje es de esta sesión y su contador es mayor que el último aceptado. */
    fun accept(message: LinkMessage): Boolean {
        val counter = message.n
        if (sessionId == null || message.sesion != sessionId || counter == null || counter <= lastReceived) {
            rejected++
            return false
        }
        lastReceived = counter
        return true
    }

    /** Agrega la sesión y el siguiente contador a un mensaje que va hacia el nodo. */
    fun stamp(message: LinkMessage): LinkMessage {
        val session = checkNotNull(sessionId) { "La sesión no está abierta" }
        return message.copy(sesion = session, n = ++lastSent)
    }
}

/** Rangos que pueden medir el DHT11 y el LDR. Una lectura fuera de esto viene dañada y se descarta. */
object ReadingRules {
    val TEMPERATURE = -40f..85f
    val HUMIDITY = 0f..100f
    val LIGHT = 0f..1000f

    fun isPlausible(temperature: Float, humidity: Float, light: Float): Boolean =
        temperature.isFinite() && humidity.isFinite() && light.isFinite() &&
            temperature in TEMPERATURE && humidity in HUMIDITY && light in LIGHT

    fun isPlausible(reading: EnvironmentReading) =
        isPlausible(reading.temperature, reading.humidity, reading.light)

    fun isPlausible(reading: StoredReading) =
        isPlausible(reading.temperature, reading.humidity, reading.light) && reading.edad >= 0
}
