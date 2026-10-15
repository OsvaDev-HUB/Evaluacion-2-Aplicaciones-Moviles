package com.example.climapulse.ui.components

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Forma de "galleta" de Material 3 Expressive: un círculo con ondas en el borde.
 * - lobes: cuántas ondas tiene el borde.
 * - depth: qué tan marcadas son (0 = círculo perfecto).
 * - rotationDegrees: gira las ondas sin girar el contenido.
 */
class CookieShape(
    private val lobes: Int = 9,
    private val depth: Float = 0.05f,
    private val rotationDegrees: Float = 0f
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val centerX = size.width / 2f
        val centerY = size.height / 2f
        val radius = min(centerX, centerY)
        val rotation = rotationDegrees * PI.toFloat() / 180f
        val path = Path()
        val steps = 240
        for (i in 0..steps) {
            val angle = 2f * PI.toFloat() * i / steps
            // El radio sube y baja con un coseno: eso dibuja las ondas.
            val r = radius * (1f - depth + depth * cos(lobes * (angle + rotation)))
            val x = centerX + r * cos(angle)
            val y = centerY + r * sin(angle)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return Outline.Generic(path)
    }
}
