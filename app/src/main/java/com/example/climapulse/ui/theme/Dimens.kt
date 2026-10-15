package com.example.climapulse.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

// Espaciados y radios compartidos para mantener el sistema de diseño consistente.
object Dimens {
    val ScreenPadding = 20.dp
    val SectionGap = 20.dp
    val ItemGap = 12.dp
    val CardPadding = 20.dp
    val ButtonHeight = 56.dp
}

object ClimaShapes {
    val Small = RoundedCornerShape(12.dp)
    val Medium = RoundedCornerShape(16.dp)
    val Large = RoundedCornerShape(24.dp)
    val ExtraLarge = RoundedCornerShape(32.dp)
    // Forma grande de Material 3 Expressive para contenedores protagonistas.
    val Hero = RoundedCornerShape(40.dp)
    val Pill = RoundedCornerShape(50)
}
