package cl.osvaldouhart.climapulse.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cl.osvaldouhart.climapulse.data.IdealRange
import cl.osvaldouhart.climapulse.data.Metric
import kotlin.math.PI
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

// Íconos dibujados a mano (como un SVG, pero en código) para poder moverlos según los datos.
// Todos se dibujan en una grilla de 24 x 24, igual que los íconos Material, y se escalan al tamaño pedido.
// Si el usuario quitó las animaciones en Android, se muestran quietos en su estado final.

private const val GRID = 24f

/** Escala el dibujo de la grilla de 24 al tamaño real del Canvas. */
private inline fun DrawScope.onGrid(block: DrawScope.() -> Unit) {
    val factor = size.minDimension / GRID
    scale(factor, pivot = Offset.Zero) { block() }
}

// ===================== 1. Estado del ambiente =====================

/**
 * Ícono del estado principal del Inicio.
 * Cómodo: el círculo aparece con un pequeño rebote y el check se dibuja solo.
 * Revisa: el triángulo entra con un leve tambaleo y después late despacio, sin llamar demasiado la atención.
 */
@Composable
fun StatusGlyph(
    comfortable: Boolean,
    color: Color,
    cutout: Color,
    modifier: Modifier = Modifier,
    size: Dp = 28.dp
) {
    val animate = animationsEnabled()
    val pop = remember { Animatable(1f) }
    val draw = remember { Animatable(1f) }
    val wobble = remember { Animatable(0f) }

    LaunchedEffect(comfortable) {
        if (!animate) return@LaunchedEffect
        pop.snapTo(0.6f)
        draw.snapTo(0f)
        // El contenedor rebota un poco al llegar (ease-out-back) y el trazo empieza 100 ms después.
        coroutineScope {
            launch { pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow)) }
            launch {
                delay(100)
                draw.animateTo(1f, tween(320, easing = FastOutSlowInEasing))
            }
            if (!comfortable) {
                launch {
                    wobble.animateTo(0f, keyframes {
                        durationMillis = 420
                        -9f at 70
                        7f at 160
                        -4f at 250
                        2f at 330
                    })
                }
            }
        }
    }

    // Latido suave solo en "Revisa": 2,5 s por ciclo y máximo 6 % de escala.
    val breath = if (animate && !comfortable) {
        rememberInfiniteTransition(label = "statusBreath").animateFloat(
            initialValue = 1f,
            targetValue = 1.06f,
            animationSpec = infiniteRepeatable(tween(1250, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "statusBreathScale"
        ).value
    } else 1f

    Canvas(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = pop.value * breath
                scaleY = pop.value * breath
                rotationZ = wobble.value
            }
    ) {
        onGrid {
            if (comfortable) {
                drawCircle(color, radius = 10f, center = Offset(12f, 12f))
                val check = Path().apply {
                    moveTo(7.2f, 12.4f)
                    lineTo(10.4f, 15.6f)
                    lineTo(16.8f, 9f)
                }
                drawPath(
                    trimmed(check, draw.value),
                    cutout,
                    style = Stroke(width = 2.4f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            } else {
                val triangle = Path().apply {
                    moveTo(12f, 3.2f)
                    lineTo(21.4f, 19.6f)
                    lineTo(2.6f, 19.6f)
                    close()
                }
                drawPath(triangle, color, style = Stroke(width = 2.2f, join = StrokeJoin.Round))
                // El signo "!" crece desde arriba junto con el trazo.
                val bar = 6f * draw.value
                drawLine(color, Offset(12f, 9f), Offset(12f, 9f + bar * 0.75f), strokeWidth = 2.2f, cap = StrokeCap.Round)
                if (draw.value > 0.8f) drawCircle(color, radius = 1.3f, center = Offset(12f, 16.6f))
            }
        }
    }
}

/** Devuelve solo una parte del trazo (de 0 a fraction), para que parezca que se dibuja. */
private fun trimmed(path: Path, fraction: Float): Path {
    val measure = PathMeasure().apply { setPath(path, false) }
    val result = Path()
    if (fraction > 0f) measure.getSegment(0f, measure.length * fraction.coerceIn(0f, 1f), result, true)
    return result
}

// ===================== 2. Íconos de métrica =====================

/** Qué tan "lleno" está el ícono: 0 = mínimo, 1 = máximo. */
private fun Metric.level(value: Float, range: IdealRange): Float = when (this) {
    // La escala del termómetro deja 10 grados de margen bajo y sobre el rango ideal.
    Metric.TEMPERATURE -> (value - (range.min - 10f)) / ((range.max + 10f) - (range.min - 10f))
    Metric.HUMIDITY -> value / 100f
    Metric.LIGHT -> value / 1000f
}.coerceIn(0.05f, 1f)

/**
 * Ícono de la métrica que refleja el valor actual:
 * el termómetro sube, la gota se llena con un oleaje suave y el sol brilla más o menos.
 */
@Composable
fun MetricGlyph(
    metric: Metric,
    value: Float,
    range: IdealRange,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp
) {
    val animate = animationsEnabled()
    val target = metric.level(value, range)
    // El nivel se acomoda como un líquido: sin rebote y con algo de peso.
    val level by animateFloatAsState(
        target,
        if (animate) spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessVeryLow) else tween(0),
        label = "metricLevel"
    )
    // Movimiento ambiental: muy lento para que acompañe sin distraer.
    val ambient = if (animate) {
        rememberInfiniteTransition(label = "metricAmbient").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                tween(if (metric == Metric.LIGHT) 24_000 else 2_600, easing = LinearEasing),
                RepeatMode.Restart
            ),
            label = "metricAmbientPhase"
        )
    } else null

    Canvas(modifier = modifier.size(size)) {
        val phase = ambient?.value ?: 0f
        onGrid {
            when (metric) {
                Metric.TEMPERATURE -> drawThermometer(level, tint)
                Metric.HUMIDITY -> drawDrop(level, phase, tint)
                Metric.LIGHT -> drawSun(level, phase, tint)
            }
        }
    }
}

private fun DrawScope.drawThermometer(level: Float, tint: Color) {
    val stroke = Stroke(width = 1.8f)
    // Tubo: de y = 3 a y = 15. El bulbo está centrado en y = 17,5.
    drawRoundRect(tint, topLeft = Offset(9.6f, 2.6f), size = Size(4.8f, 13.4f), cornerRadius = CornerRadius(2.4f), style = stroke)
    drawCircle(tint, radius = 4.2f, center = Offset(12f, 17.6f), style = stroke)
    // Mercurio: el bulbo siempre lleno y la columna sube según el nivel.
    drawCircle(tint, radius = 2.4f, center = Offset(12f, 17.6f))
    val top = 15.8f - 11.2f * level
    drawRoundRect(tint, topLeft = Offset(11.1f, top), size = Size(1.8f, 16.6f - top), cornerRadius = CornerRadius(0.9f))
    // Dos marcas de la escala al costado.
    drawLine(tint.copy(alpha = 0.55f), Offset(15.6f, 6.5f), Offset(17f, 6.5f), strokeWidth = 1.2f, cap = StrokeCap.Round)
    drawLine(tint.copy(alpha = 0.55f), Offset(15.6f, 10f), Offset(17f, 10f), strokeWidth = 1.2f, cap = StrokeCap.Round)
}

private fun dropPath() = Path().apply {
    moveTo(12f, 2.6f)
    cubicTo(12f, 2.6f, 5f, 10.4f, 5f, 14.6f)
    cubicTo(5f, 18.6f, 8.1f, 21.4f, 12f, 21.4f)
    cubicTo(15.9f, 21.4f, 19f, 18.6f, 19f, 14.6f)
    cubicTo(19f, 10.4f, 12f, 2.6f, 12f, 2.6f)
    close()
}

private fun DrawScope.drawDrop(level: Float, phase: Float, tint: Color) {
    val drop = dropPath()
    // El agua llena la gota desde abajo; la superficie es una ola que se desplaza.
    val surface = 21.4f - 18.8f * level
    clipPath(drop) {
        val water = Path().apply {
            moveTo(0f, GRID)
            lineTo(0f, surface)
            var x = 0f
            while (x <= GRID) {
                val y = surface + sin((x / GRID * 2f * PI + phase * 2f * PI).toFloat()) * 0.9f
                lineTo(x, y)
                x += 1f
            }
            lineTo(GRID, GRID)
            close()
        }
        drawPath(water, tint.copy(alpha = 0.85f))
    }
    drawPath(drop, tint, style = Stroke(width = 1.8f, join = StrokeJoin.Round))
}

private fun DrawScope.drawSun(level: Float, phase: Float, tint: Color) {
    val center = Offset(12f, 12f)
    // Más luz = núcleo más grande, rayos más largos y más opacos.
    drawCircle(tint, radius = 3.4f + 1.2f * level, center = center)
    rotate(phase * 360f, pivot = center) {
        repeat(8) { index ->
            val angle = (index * 45f) * (PI / 180f).toFloat()
            val inner = 6.6f + 0.6f * level
            val outer = inner + 1.2f + 2.4f * level
            drawLine(
                tint.copy(alpha = 0.45f + 0.55f * level),
                Offset(center.x + cos(angle) * inner, center.y + sin(angle) * inner),
                Offset(center.x + cos(angle) * outer, center.y + sin(angle) * outer),
                strokeWidth = 1.8f,
                cap = StrokeCap.Round
            )
        }
    }
}

// ===================== 3. Ventilador y lámpara =====================

/** Aspas que aceleran al encender y frenan poco a poco al apagar, como un ventilador real. */
@Composable
fun FanGlyph(on: Boolean, tint: Color, modifier: Modifier = Modifier, size: Dp = 22.dp) {
    val animate = animationsEnabled()
    val speed = animateFloatAsState(
        if (on) 1f else 0f,
        tween(if (on) 700 else 1_400, easing = FastOutSlowInEasing),
        label = "fanSpeed"
    )
    var angle by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(on, animate) {
        if (!animate) return@LaunchedEffect
        var last = 0L
        // Sigue girando mientras esté encendido o todavía no termine de frenar.
        while (on || speed.value > 0.01f) {
            withFrameNanos { now ->
                if (last != 0L) angle = (angle + speed.value * 540f * (now - last) / 1e9f) % 360f
                last = now
            }
        }
    }

    Canvas(modifier = modifier.size(size)) {
        onGrid {
            val center = Offset(12f, 12f)
            rotate(angle, pivot = center) {
                repeat(3) { index ->
                    rotate(index * 120f, pivot = center) {
                        val blade = Path().apply {
                            moveTo(12f, 12f)
                            cubicTo(10.2f, 8.6f, 10.6f, 3.4f, 13.4f, 3.2f)
                            cubicTo(16.4f, 3f, 16f, 8.4f, 12f, 12f)
                            close()
                        }
                        drawPath(blade, tint.copy(alpha = if (on) 1f else 0.85f))
                    }
                }
            }
            drawCircle(tint, radius = 2f, center = center)
        }
    }
}

/** Ampolleta: al encender se llena de luz y aparecen tres rayos con un pequeño rebote. */
@Composable
fun BulbGlyph(on: Boolean, tint: Color, glow: Color, modifier: Modifier = Modifier, size: Dp = 22.dp) {
    val animate = animationsEnabled()
    val light by animateFloatAsState(
        if (on) 1f else 0f,
        if (animate) spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessLow) else tween(0),
        label = "bulbLight"
    )
    Canvas(modifier = modifier.size(size)) {
        onGrid {
            val glass = Path().apply {
                moveTo(9f, 16.2f)
                cubicTo(9f, 14.4f, 6.4f, 13f, 6.4f, 9.6f)
                cubicTo(6.4f, 6.4f, 8.9f, 4f, 12f, 4f)
                cubicTo(15.1f, 4f, 17.6f, 6.4f, 17.6f, 9.6f)
                cubicTo(17.6f, 13f, 15f, 14.4f, 15f, 16.2f)
                close()
            }
            val l = light.coerceIn(0f, 1.2f)
            // Halo detrás del vidrio.
            if (l > 0f) drawCircle(glow.copy(alpha = 0.28f * l.coerceAtMost(1f)), radius = 7f + 2.5f * l, center = Offset(12f, 9.8f))
            drawPath(glass, glow.copy(alpha = l.coerceAtMost(1f)))
            drawPath(glass, tint, style = Stroke(width = 1.7f, join = StrokeJoin.Round))
            // Rosca.
            drawLine(tint, Offset(9.6f, 18.4f), Offset(14.4f, 18.4f), strokeWidth = 1.7f, cap = StrokeCap.Round)
            drawLine(tint, Offset(10.4f, 20.6f), Offset(13.6f, 20.6f), strokeWidth = 1.7f, cap = StrokeCap.Round)
            // Rayos: crecen desde el vidrio hacia afuera.
            if (l > 0.02f) {
                listOf(-50f, 0f, 50f).forEach { degrees ->
                    rotate(degrees, pivot = Offset(12f, 9.6f)) {
                        drawLine(
                            glow,
                            Offset(12f, 2.4f),
                            Offset(12f, 2.4f - 2.2f * l),
                            strokeWidth = 1.8f,
                            cap = StrokeCap.Round
                        )
                    }
                }
            }
        }
    }
}

/** Círculo tonal para mostrar un ícono animado, con la misma forma que IconBadge. */
@Composable
fun GlyphBadge(
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    container: Color = tint.copy(alpha = 0.16f),
    content: @Composable (Dp) -> Unit
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(container),
        contentAlignment = Alignment.Center
    ) {
        content(size * 0.56f)
    }
}
