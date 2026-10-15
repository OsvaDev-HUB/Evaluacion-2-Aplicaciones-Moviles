package com.example.climapulse.data

import kotlinx.serialization.Serializable

enum class Metric(val label: String, val unit: String, val decimals: Int, val defaultRange: IdealRange) {
    TEMPERATURE("Temperatura", "°C", 1, IdealRange(19f, 25f)),
    HUMIDITY("Humedad", "%", 0, IdealRange(40f, 65f)),
    LIGHT("Luz", "lux", 0, IdealRange(180f, 750f))
}

@Serializable
data class IdealRange(val min: Float, val max: Float) {
    operator fun contains(value: Float) = value in min..max
}

@Serializable
data class EnvironmentReading(
    val temperature: Float,
    val humidity: Float,
    val light: Float,
    val timestampMillis: Long,
    val usingHardware: Boolean = false,
    // De dónde viene la lectura: este teléfono o el nombre del nodo sensor remoto.
    val origin: String = ReadingSimulator.ORIGIN_LOCAL
) {
    fun valueOf(metric: Metric): Float = when (metric) {
        Metric.TEMPERATURE -> temperature
        Metric.HUMIDITY -> humidity
        Metric.LIGHT -> light
    }
}

/** Actuadores simulados del nodo sensor que el monitor puede controlar a distancia. */
@Serializable
data class Actuators(
    val fan: Boolean = false,
    val lamp: Boolean = false,
    val paused: Boolean = false
)

data class UserProfile(val name: String, val email: String, val phone: String)

/** Error de red tipado: la UI lo traduce a un mensaje humano, nunca muestra el código crudo. */
class OfflineException : Exception("offline")
class AuthException(message: String) : Exception(message)
class LinkException : Exception("link")
