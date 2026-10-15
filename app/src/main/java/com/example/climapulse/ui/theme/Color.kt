package com.example.climapulse.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

// Colores del logo. Se usan en la Splash y como semilla de los esquemas.
val BrandTeal = Color(0xFF2BC1AD)
val BrandYellow = Color(0xFFFFD166)
val Navy = Color(0xFF17212F)
val NavyDeep = Color(0xFF101823)

// ---------- Esquema claro (uso en terreno, con luz de día) ----------
internal val LightPrimary = Color(0xFF00695F)
internal val LightOnPrimary = Color(0xFFFFFFFF)
internal val LightPrimaryContainer = Color(0xFFA2F2E3)
internal val LightOnPrimaryContainer = Color(0xFF00201C)
internal val LightSecondary = Color(0xFF4A635F)
internal val LightOnSecondary = Color(0xFFFFFFFF)
internal val LightSecondaryContainer = Color(0xFFCCE8E2)
internal val LightOnSecondaryContainer = Color(0xFF05201C)
internal val LightTertiary = Color(0xFF745B00)
internal val LightOnTertiary = Color(0xFFFFFFFF)
internal val LightTertiaryContainer = BrandYellow
internal val LightOnTertiaryContainer = Color(0xFF241A00)
internal val LightError = Color(0xFFB3261E)
internal val LightOnError = Color(0xFFFFFFFF)
internal val LightErrorContainer = Color(0xFFF9DEDC)
internal val LightOnErrorContainer = Color(0xFF410E0B)
internal val LightSurface = Color(0xFFF4FBF8)
internal val LightOnSurface = Color(0xFF161D1B)
internal val LightOnSurfaceVariant = Color(0xFF3F4946)
internal val LightSurfaceLowest = Color(0xFFFFFFFF)
// Superficies con más contraste contra el fondo, para que las tarjetas se noten.
internal val LightSurfaceLow = Color(0xFFE5EEEA)
internal val LightSurfaceContainer = Color(0xFFDCE6E2)
internal val LightSurfaceHigh = Color(0xFFD2DDD8)
internal val LightSurfaceHighest = Color(0xFFC8D4CF)
internal val LightOutline = Color(0xFF6F7976)
internal val LightOutlineVariant = Color(0xFFAAB7B2)

// ---------- Esquema oscuro (interiores con poca luz) ----------
internal val DarkPrimary = Color(0xFF5FDBC6)
internal val DarkOnPrimary = Color(0xFF003731)
internal val DarkPrimaryContainer = Color(0xFF005048)
internal val DarkOnPrimaryContainer = Color(0xFFA2F2E3)
internal val DarkSecondary = Color(0xFFB1CCC6)
internal val DarkOnSecondary = Color(0xFF1C3531)
internal val DarkSecondaryContainer = Color(0xFF334B47)
internal val DarkOnSecondaryContainer = Color(0xFFCCE8E2)
internal val DarkTertiary = BrandYellow
internal val DarkOnTertiary = Color(0xFF3D2E00)
internal val DarkTertiaryContainer = Color(0xFF584400)
internal val DarkOnTertiaryContainer = Color(0xFFFFE08F)
internal val DarkError = Color(0xFFFFB4AB)
internal val DarkOnError = Color(0xFF690005)
internal val DarkErrorContainer = Color(0xFF93000A)
internal val DarkOnErrorContainer = Color(0xFFFFDAD6)
internal val DarkSurface = Color(0xFF0E1513)
internal val DarkOnSurface = Color(0xFFDDE4E1)
internal val DarkOnSurfaceVariant = Color(0xFFBEC9C5)
internal val DarkSurfaceLowest = Color(0xFF090F0E)
internal val DarkSurfaceLow = Color(0xFF19221F)
internal val DarkSurfaceContainer = Color(0xFF1F2926)
internal val DarkSurfaceHigh = Color(0xFF2A3431)
internal val DarkSurfaceHighest = Color(0xFF353F3C)
internal val DarkOutline = Color(0xFF899390)
internal val DarkOutlineVariant = Color(0xFF3F4946)

// Colores de acento que cambian según el tema claro u oscuro.
// En claro se usan tonos más oscuros para que el texto se lea bien sobre fondo blanco.

/** Estado "todo bien": el turquesa de la marca. */
val Teal: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.primary

/** Estado "revisa": el amarillo de la marca. */
val Yellow: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.tertiary
