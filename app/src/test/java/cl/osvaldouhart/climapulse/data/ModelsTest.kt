package cl.osvaldouhart.climapulse.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelsTest {
    private val lectura = EnvironmentReading(temperature = 22.5f, humidity = 48f, light = 420f, timestampMillis = 1_000L)

    @Test
    fun valueOfDevuelveLaMetricaPedida() {
        assertEquals(22.5f, lectura.valueOf(Metric.TEMPERATURE))
        assertEquals(48f, lectura.valueOf(Metric.HUMIDITY))
        assertEquals(420f, lectura.valueOf(Metric.LIGHT))
    }

    @Test
    fun rangoIdealIncluyeLosBordes() {
        val rango = IdealRange(19f, 25f)
        assertTrue(19f in rango)
        assertTrue(25f in rango)
        assertFalse(25.1f in rango)
        assertFalse(18.9f in rango)
    }

    @Test
    fun losRangosPorDefectoSonCoherentes() {
        Metric.entries.forEach { assertTrue(it.defaultRange.min < it.defaultRange.max) }
    }

    @Test
    fun mensajeDelSensorSeLeeAunqueTraigaCamposDeMas() {
        // El ESP8266 puede mandar campos que la app no conoce; no debe romperse.
        val json = Json { ignoreUnknownKeys = true }
        val texto = """{"tipo":"lectura","extra":1,"lectura":{"temperature":21.0,"humidity":50.0,"light":300.0,"timestampMillis":0}}"""
        val mensaje = json.decodeFromString<LinkMessage>(texto)
        assertEquals("lectura", mensaje.tipo)
        assertEquals(21.0f, mensaje.lectura?.temperature)
    }
}
