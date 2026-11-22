package cl.osvaldouhart.climapulse.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cl.osvaldouhart.climapulse.data.LinkRole
import cl.osvaldouhart.climapulse.data.LinkState
import cl.osvaldouhart.climapulse.ui.theme.BrandYellow

/**
 * Dibuja el enlace como una línea de metro con dos estaciones: el nodo sensor y el monitor.
 * Línea llena = conectados: con cada lectura que llega, un punto viaja del sensor al teléfono.
 * Línea punteada = sin enlace; mientras se busca, los puntos avanzan y salen ondas del nodo.
 * "pulse" cambia cada vez que llega una lectura real.
 */
@Composable
fun LinkLine(
    state: LinkState,
    modifier: Modifier = Modifier,
    searching: Boolean = false,
    pulse: Int = 0,
    // Colores del fondo donde se dibuja: las estaciones activas usan el color de contraste
    // para que se vean igual en tema claro y oscuro.
    container: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    content: Color = MaterialTheme.colorScheme.onSurface
) {
    val sensorName = when (state.role) {
        LinkRole.MONITOR -> state.peerName.ifEmpty { "Nodo sensor" }
        LinkRole.NONE -> "Nodo sensor"
    }
    val monitorName = when (state.role) {
        LinkRole.MONITOR -> "Este teléfono"
        LinkRole.NONE -> "Monitor"
    }
    val dataFlowing = state.isActive
    val looking = !dataFlowing && (searching || state.connecting)
    val description = when {
        dataFlowing -> "Enlace activo entre $sensorName y $monitorName"
        looking -> "Buscando el nodo sensor"
        state.role == LinkRole.NONE -> "Sin enlace WiFi"
        else -> "Esperando conexión entre $sensorName y $monitorName"
    }

    val animate = animationsEnabled()
    // Avance del punto: 0 = en el sensor, 1 = llegó al teléfono.
    val travel = remember { Animatable(1f) }
    var arrivals by remember { mutableIntStateOf(0) }
    LaunchedEffect(pulse, dataFlowing) {
        if (!dataFlowing || pulse == 0 || !animate) return@LaunchedEffect
        travel.snapTo(0f)
        travel.animateTo(1f, tween(1_100, easing = FastOutSlowInEasing))
        arrivals++
    }

    Row(
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = description },
        verticalAlignment = Alignment.Top
    ) {
        Station(
            Icons.Rounded.Sensors,
            sensorName,
            active = state.role != LinkRole.NONE,
            container = container,
            content = content,
            waves = looking && animate,
            modifier = Modifier.width(104.dp)
        )
        Track(
            active = dataFlowing,
            content = content,
            looking = looking && animate,
            travel = { travel.value },
            modifier = Modifier.weight(1f).padding(top = 18.dp)
        )
        Station(
            Icons.Rounded.PhoneAndroid,
            monitorName,
            active = dataFlowing,
            container = container,
            content = content,
            popKey = arrivals,
            modifier = Modifier.width(104.dp)
        )
    }
}

@Composable
private fun Station(
    icon: ImageVector,
    label: String,
    active: Boolean,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
    waves: Boolean = false,
    popKey: Int = 0
) {
    val colors = MaterialTheme.colorScheme
    val waveColor = colors.primary
    // Pequeño salto cuando llega una lectura: sube rápido y se asienta con rebote.
    val pop = remember { Animatable(1f) }
    LaunchedEffect(popKey) {
        if (popKey == 0) return@LaunchedEffect
        pop.animateTo(1.14f, tween(90, easing = FastOutSlowInEasing))
        pop.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMedium))
    }
    val wave = if (waves) {
        rememberInfiniteTransition(label = "stationWaves").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1_800, easing = LinearEasing), RepeatMode.Restart),
            label = "stationWave"
        )
    } else null

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            modifier = Modifier
                .size(40.dp)
                // Las ondas se dibujan fuera del círculo, por eso van antes del clip.
                .drawBehind {
                    val t = wave?.value ?: return@drawBehind
                    repeat(2) { index ->
                        val p = (t + index / 2f) % 1f
                        drawCircle(
                            // Se apaga rápido para no tapar el nombre que está debajo.
                            waveColor.copy(alpha = (1f - p) * (1f - p) * 0.5f),
                            radius = size.minDimension / 2f * (1f + p * 0.7f),
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }
                }
                .graphicsLayer {
                    scaleX = pop.value
                    scaleY = pop.value
                }
                .clip(CircleShape)
                .background(if (active) content else colors.surfaceContainerHighest),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = if (active) container else colors.onSurfaceVariant, modifier = Modifier.size(22.dp))
        }
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (active) content else content.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun Track(active: Boolean, content: Color, looking: Boolean, travel: () -> Float, modifier: Modifier = Modifier) {
    val lineColor = if (active) content else MaterialTheme.colorScheme.outline
    val dotColor = BrandYellow
    // Mientras se busca, la línea punteada "avanza" hacia el teléfono.
    val dashShift = if (looking) {
        rememberInfiniteTransition(label = "trackDash").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Restart),
            label = "trackDashShift"
        )
    } else null

    Canvas(modifier = modifier.height(12.dp)) {
        val y = size.height / 2f
        val stroke = 4.dp.toPx()
        if (active) {
            drawLine(lineColor, Offset(0f, y), Offset(size.width, y), strokeWidth = stroke, cap = StrokeCap.Round)
            val t = travel()
            if (t < 1f) {
                // El punto aparece al salir y se desvanece al llegar.
                val alpha = (minOf(t, 1f - t) * 6f).coerceIn(0f, 1f)
                val center = Offset(size.width * t, y)
                // Amarillo de la marca con borde de la línea, así se ve sobre el turquesa y en modo oscuro.
                drawCircle(lineColor.copy(alpha = alpha), radius = 7.dp.toPx(), center = center)
                drawCircle(dotColor.copy(alpha = alpha), radius = 5.dp.toPx(), center = center)
            }
        } else {
            val period = 12.dp.toPx()
            drawLine(
                lineColor.copy(alpha = 0.6f),
                Offset(0f, y),
                Offset(size.width, y),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(
                    floatArrayOf(2.dp.toPx(), 10.dp.toPx()),
                    phase = -(dashShift?.value ?: 0f) * period
                )
            )
        }
    }
}

/** true cuando hay lecturas viajando: monitor conectado, o sensor con al menos un monitor. */
val LinkState.isActive: Boolean
    get() = connected && role == LinkRole.MONITOR
