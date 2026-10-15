package com.example.climapulse.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.climapulse.ui.theme.ClimaShapes
import com.example.climapulse.ui.theme.Dimens
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.climapulse.ui.theme.Teal

/**
 * Ilustración de marca: los anillos del logo emitiendo un "pulso" continuo.
 * Se usa en el onboarding y en encabezados.
 */
@Composable
fun PulseRings(
    modifier: Modifier = Modifier,
    size: Dp = 240.dp,
    color: Color = Teal,
    content: @Composable BoxScope.() -> Unit
) {
    val progress by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart),
        label = "pulseProgress"
    )
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val maxRadius = this.size.minDimension / 2f
            val minRadius = maxRadius * 0.42f
            // Tres ondas desfasadas que crecen y se desvanecen.
            repeat(3) { index ->
                val t = (progress + index / 3f) % 1f
                drawCircle(
                    color = color.copy(alpha = (1f - t) * 0.35f),
                    radius = minRadius + (maxRadius - minRadius) * t,
                    style = Stroke(width = 2.dp.toPx())
                )
            }
            drawCircle(color = color.copy(alpha = 0.10f), radius = minRadius)
        }
        content()
    }
}

/** Tarjeta destacada: contenedor tonal plano. "accent" se mantiene para no cambiar las llamadas. */
@Suppress("UNUSED_PARAMETER")
@Composable
fun HeroCard(
    modifier: Modifier = Modifier,
    accent: Color = Teal,
    content: @Composable ColumnScope.() -> Unit
) {
    // Superficie neutra: el color fuerte se reserva para la forma de estado del Inicio.
    val container = MaterialTheme.colorScheme.surfaceContainerLow
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(ClimaShapes.Hero)
            .background(container)
            .padding(Dimens.CardPadding),
        content = content
    )
}

/** Etiqueta de estado compacta (Ideal, Alta, Baja...). */
@Composable
fun StatusPill(text: String, color: Color, icon: ImageVector? = null) {
    Row(
        modifier = Modifier
            .clip(ClimaShapes.Pill)
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

/** Barra de progreso redondeada para mostrar dónde cae una lectura. */
@Composable
fun LinearMeter(progress: Float, color: Color, trackColor: Color = MaterialTheme.colorScheme.outlineVariant) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(ClimaShapes.Pill)
            .background(trackColor)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress)
                .height(8.dp)
                .clip(ClimaShapes.Pill)
                .background(color)
        )
    }
}
