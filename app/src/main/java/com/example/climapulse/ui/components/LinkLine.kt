package com.example.climapulse.ui.components

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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.climapulse.data.LinkRole
import com.example.climapulse.data.LinkState

/**
 * Dibuja el enlace como una línea de metro con dos estaciones: el nodo sensor y el monitor.
 * Línea llena = conectados (un punto viaja como si fuera la lectura); línea punteada = sin enlace.
 */
@Composable
fun LinkLine(state: LinkState, modifier: Modifier = Modifier) {
    val sensorName = when (state.role) {
        LinkRole.SENSOR -> "Este teléfono"
        LinkRole.MONITOR -> state.peerName.ifEmpty { "Nodo sensor" }
        LinkRole.NONE -> "Nodo sensor"
    }
    val monitorName = when (state.role) {
        LinkRole.SENSOR -> if (state.monitors == 1) "1 monitor" else "${state.monitors} monitores"
        LinkRole.MONITOR -> "Este teléfono"
        LinkRole.NONE -> "Monitor"
    }
    val dataFlowing = state.isActive
    val description = when {
        state.role == LinkRole.NONE -> "Sin enlace WiFi"
        dataFlowing -> "Enlace activo entre $sensorName y $monitorName"
        else -> "Esperando conexión entre $sensorName y $monitorName"
    }

    Row(
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = description },
        verticalAlignment = Alignment.Top
    ) {
        Station(Icons.Rounded.Sensors, sensorName, active = state.role != LinkRole.NONE, modifier = Modifier.width(104.dp))
        Track(active = dataFlowing, modifier = Modifier.weight(1f).padding(top = 18.dp))
        Station(Icons.Rounded.PhoneAndroid, monitorName, active = dataFlowing, modifier = Modifier.width(104.dp))
    }
}

@Composable
private fun Station(icon: ImageVector, label: String, active: Boolean, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (active) colors.primary else colors.surfaceContainerHighest),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = if (active) colors.onPrimary else colors.onSurfaceVariant, modifier = Modifier.size(22.dp))
        }
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (active) colors.onSurface else colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun Track(active: Boolean, modifier: Modifier = Modifier) {
    val lineColor = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    val dotColor = MaterialTheme.colorScheme.tertiary
    // El punto recorre la línea cada 3 segundos, igual que el envío real de lecturas.
    val progress by rememberInfiniteTransition(label = "link").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3_000, easing = LinearEasing), RepeatMode.Restart),
        label = "linkDot"
    )
    Canvas(modifier = modifier.height(4.dp)) {
        val y = size.height / 2f
        val stroke = 4.dp.toPx()
        if (active) {
            drawLine(lineColor, Offset(0f, y), Offset(size.width, y), strokeWidth = stroke, cap = StrokeCap.Round)
            drawCircle(dotColor, radius = 6.dp.toPx(), center = Offset(size.width * progress, y))
        } else {
            drawLine(
                lineColor.copy(alpha = 0.6f),
                Offset(0f, y),
                Offset(size.width, y),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 10.dp.toPx()))
            )
        }
    }
}

/** true cuando hay lecturas viajando: monitor conectado, o sensor con al menos un monitor. */
val LinkState.isActive: Boolean
    get() = connected && (role == LinkRole.MONITOR || monitors > 0)
