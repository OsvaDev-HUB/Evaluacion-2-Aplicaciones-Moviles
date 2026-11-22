package cl.osvaldouhart.climapulse.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkSessionTest {
    private fun bienvenida(session: LinkSession, sesion: String = "abc123", n: Long = 1) =
        LinkMessage(tipo = "bienvenida", reto = session.challenge, sesion = sesion, n = n)

    private fun lectura(sesion: String, n: Long) = LinkMessage(tipo = "lectura", sesion = sesion, n = n)

    @Test
    fun cadaConexionUsaUnRetoDistinto() {
        assertNotEquals(LinkSession().challenge, LinkSession().challenge)
        assertEquals(16, LinkSession().challenge.length)
    }

    @Test
    fun aceptaLaBienvenidaConElMismoReto() {
        val session = LinkSession()
        assertTrue(session.acceptWelcome(bienvenida(session)))
        assertTrue(session.isOpen)
    }

    @Test
    fun rechazaUnaBienvenidaGrabadaDeOtraConexion() {
        // Una bienvenida vieja trae el reto de otra conexión.
        val vieja = LinkSession()
        val nueva = LinkSession()
        assertFalse(nueva.acceptWelcome(bienvenida(vieja)))
        assertFalse(nueva.isOpen)
    }

    @Test
    fun aceptaMensajesConContadorQueSube() {
        val session = LinkSession().apply { acceptWelcome(bienvenida(this)) }
        assertTrue(session.accept(lectura("abc123", 2)))
        assertTrue(session.accept(lectura("abc123", 3)))
        assertTrue(session.accept(lectura("abc123", 10)))
        assertEquals(0, session.rejected)
    }

    @Test
    fun rechazaUnMensajeRepetido() {
        val session = LinkSession().apply { acceptWelcome(bienvenida(this)) }
        val mensaje = lectura("abc123", 2)
        assertTrue(session.accept(mensaje))
        assertFalse(session.accept(mensaje))
        assertFalse(session.accept(lectura("abc123", 1)))
        assertEquals(2, session.rejected)
    }

    @Test
    fun rechazaMensajesDeOtraSesionOSinContador() {
        val session = LinkSession().apply { acceptWelcome(bienvenida(this)) }
        assertFalse(session.accept(lectura("otra", 5)))
        assertFalse(session.accept(LinkMessage(tipo = "lectura", sesion = "abc123")))
        assertFalse(session.accept(LinkMessage(tipo = "lectura", n = 7)))
    }

    @Test
    fun losMensajesDeLaAppLlevanSesionYContadorNuevo() {
        val session = LinkSession().apply { acceptWelcome(bienvenida(this)) }
        val primero = session.stamp(LinkMessage(tipo = "comando"))
        val segundo = session.stamp(LinkMessage(tipo = "comando"))
        assertEquals("abc123", primero.sesion)
        assertEquals(1L, primero.n)
        assertEquals(2L, segundo.n)
    }

    @Test
    fun elHolaNoLlevaCamposVacios() {
        // La app omite los campos nulos: el ESP8266 solo recibe lo necesario.
        val json = Json.encodeToString(LinkMessage.serializer(), LinkSession().hello("Pixel 7"))
        assertTrue(json.contains("\"reto\""))
        assertFalse(json.contains("null"))
    }

    @Test
    fun leeElHistorialQueMandaElNodo() {
        val texto = """{"tipo":"historial","sesion":"abc123","n":4,"quedan":0,
            "lecturas":[{"temperature":22.5,"humidity":48.0,"light":420,"edad":30000}]}"""
        val mensaje = Json { ignoreUnknownKeys = true }.decodeFromString(LinkMessage.serializer(), texto)
        assertEquals(1, mensaje.lecturas?.size)
        assertEquals(30_000L, mensaje.lecturas?.first()?.edad)
        assertNull(mensaje.lectura)
    }
}

class ReadingRulesTest {
    @Test
    fun aceptaLecturasNormales() {
        assertTrue(ReadingRules.isPlausible(22.5f, 48f, 420f))
        assertTrue(ReadingRules.isPlausible(-40f, 0f, 0f))
        assertTrue(ReadingRules.isPlausible(85f, 100f, 1000f))
    }

    @Test
    fun rechazaValoresImposiblesODanados() {
        assertFalse(ReadingRules.isPlausible(Float.NaN, 48f, 420f))
        assertFalse(ReadingRules.isPlausible(22f, 140f, 420f))
        assertFalse(ReadingRules.isPlausible(22f, 48f, -5f))
        assertFalse(ReadingRules.isPlausible(300f, 48f, 420f))
        assertFalse(ReadingRules.isPlausible(StoredReading(22f, 48f, 420f, edad = -1)))
    }
}
