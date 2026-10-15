package com.example.climapulse.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

// Los tres colores del logo. Toda la paleta sale de ellos:
// - Azul marino: estructura (texto, barra de navegación, fondo oscuro).
// - Turquesa: estado "cómodo" y acciones.
// - Amarillo: estado "revisa".
val BrandTeal = Color(0xFF2BC1AD)
val BrandYellow = Color(0xFFFFD166)
val Navy = Color(0xFF17212F)
val NavyDeep = Color(0xFF101823)

// ---------- Esquema claro (uso en terreno, con luz de día) ----------
// Botones, links, interruptores y textos destacados van en el azul marino del logo.
// No se usa un turquesa oscurecido porque se ve verde esmeralda.
internal val LightPrimary = Navy
internal val LightOnPrimary = Color(0xFFFFFFFF)
// El turquesa exacto del logo, siempre como relleno y con contenido azul marino encima.
internal val LightPrimaryContainer = BrandTeal
internal val LightOnPrimaryContainer = Navy
// Secundario: azul marino suave.
internal val LightSecondary = Color(0xFF3D4D61)
internal val LightOnSecondary = Color(0xFFFFFFFF)
internal val LightSecondaryContainer = Color(0xFFD9E1EB)
internal val LightOnSecondaryContainer = Navy
// Terciario: el amarillo del logo (contenedor) y un amarillo oscuro para texto.
internal val LightTertiary = Color(0xFF7A5900)
internal val LightOnTertiary = Color(0xFFFFFFFF)
internal val LightTertiaryContainer = BrandYellow
internal val LightOnTertiaryContainer = Navy
internal val LightError = Color(0xFFB3261E)
internal val LightOnError = Color(0xFFFFFFFF)
internal val LightErrorContainer = Color(0xFFF9DEDC)
internal val LightOnErrorContainer = Color(0xFF410E0B)
// Grises fríos de la familia del azul marino (no verdosos).
internal val LightSurface = Color(0xFFF5F7FA)
internal val LightOnSurface = Navy
internal val LightOnSurfaceVariant = Color(0xFF465263)
internal val LightSurfaceLowest = Color(0xFFFFFFFF)
internal val LightSurfaceLow = Color(0xFFEBEFF4)
internal val LightSurfaceContainer = Color(0xFFE2E8EF)
internal val LightSurfaceHigh = Color(0xFFD8DFE8)
internal val LightSurfaceHighest = Color(0xFFCDD6E1)
internal val LightOutline = Color(0xFF737F8E)
internal val LightOutlineVariant = Color(0xFFB3BECB)

// ---------- Esquema oscuro (interiores con poca luz) ----------
// El fondo es el azul marino del logo, igual que la Splash.
// Sobre fondo oscuro el turquesa del logo sí se lee, así que es el color de acción.
internal val DarkPrimary = BrandTeal
internal val DarkOnPrimary = Navy
// Los rellenos de estado usan los colores exactos del logo en ambos temas.
internal val DarkPrimaryContainer = BrandTeal
internal val DarkOnPrimaryContainer = Navy
internal val DarkSecondary = Color(0xFFB9C6D6)
internal val DarkOnSecondary = Color(0xFF233142)
internal val DarkSecondaryContainer = Color(0xFF2C3B4E)
internal val DarkOnSecondaryContainer = Color(0xFFD9E1EB)
internal val DarkTertiary = BrandYellow
internal val DarkOnTertiary = Color(0xFF3D2E00)
internal val DarkTertiaryContainer = BrandYellow
internal val DarkOnTertiaryContainer = Navy
internal val DarkError = Color(0xFFFFB4AB)
internal val DarkOnError = Color(0xFF690005)
internal val DarkErrorContainer = Color(0xFF93000A)
internal val DarkOnErrorContainer = Color(0xFFFFDAD6)
internal val DarkSurface = NavyDeep
internal val DarkOnSurface = Color(0xFFE5ECF4)
internal val DarkOnSurfaceVariant = Color(0xFFA9B6C5)
internal val DarkSurfaceLowest = Color(0xFF0A1018)
internal val DarkSurfaceLow = Navy
internal val DarkSurfaceContainer = Color(0xFF1D2938)
internal val DarkSurfaceHigh = Color(0xFF253344)
internal val DarkSurfaceHighest = Color(0xFF2F3E51)
internal val DarkOutline = Color(0xFF6B7C90)
internal val DarkOutlineVariant = Color(0xFF34445A)

// Colores de acento que cambian según el tema claro u oscuro.

/** Estado "todo bien": azul marino en tema claro y turquesa en tema oscuro (se leen sobre el fondo). */
val Teal: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.primary

/** Estado "revisa": el amarillo de la marca. */
val Yellow: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.tertiary
