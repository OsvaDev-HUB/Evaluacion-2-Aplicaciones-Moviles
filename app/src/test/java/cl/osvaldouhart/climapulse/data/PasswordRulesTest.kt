package cl.osvaldouhart.climapulse.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PasswordRulesTest {
    @Test
    fun aceptaUnaContrasenaValida() {
        assertNull(validateNewPassword("Clima2026!"))
    }

    @Test
    fun rechazaContrasenasCortas() {
        assertEquals("Usa al menos 8 caracteres", validateNewPassword("abc123"))
    }

    @Test
    fun rechazaContrasenasMuyLargas() {
        assertEquals("Usa como máximo 128 caracteres", validateNewPassword("a1b2c3d4".repeat(17)))
    }

    @Test
    fun rechazaElMismoCaracterRepetido() {
        assertEquals("Evita repetir el mismo carácter", validateNewPassword("aaaaaaaa"))
    }

    @Test
    fun rechazaContrasenasComunesSinImportarMayusculas() {
        assertNotNull(validateNewPassword("12345678"))
        assertNotNull(validateNewPassword("PASSWORD1"))
        assertNotNull(validateNewPassword("ClimaPulse"))
    }
}
