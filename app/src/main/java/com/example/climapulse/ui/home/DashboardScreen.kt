package com.example.climapulse.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.ui.unit.sp
import com.example.climapulse.ui.components.CookieShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.material3.FilledTonalButton
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.climapulse.data.EnvironmentReading
import com.example.climapulse.data.IdealRange
import com.example.climapulse.data.Metric
import com.example.climapulse.data.ReadingSimulator
import com.example.climapulse.ui.UiState
import com.example.climapulse.ui.components.ClimaCard
import com.example.climapulse.ui.components.ClimaLogo
import com.example.climapulse.ui.components.DashboardSkeleton
import com.example.climapulse.ui.components.ErrorState
import com.example.climapulse.ui.components.HeroCard
import com.example.climapulse.ui.components.IconBadge
import com.example.climapulse.ui.components.LinearMeter
import com.example.climapulse.ui.components.PrimaryButton
import com.example.climapulse.ui.components.SectionHeading
import com.example.climapulse.ui.components.StatusPill
import com.example.climapulse.ui.components.formatTime
import com.example.climapulse.ui.components.pressScale
import com.example.climapulse.ui.components.rememberInteractionSource
import com.example.climapulse.ui.theme.ClimaShapes
import com.example.climapulse.ui.theme.Dimens
import com.example.climapulse.ui.theme.MetricValueStyle
import com.example.climapulse.ui.theme.Teal
import com.example.climapulse.ui.theme.Yellow
import com.example.climapulse.ui.tour.TourKeys
import com.example.climapulse.ui.tour.tourTarget

@Composable
fun DashboardScreen(
    viewModel: HomeViewModel,
    onOpenMetric: (Metric) -> Unit,
    onOpenHistory: () -> Unit,
    onOpenLink: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val firstName by viewModel.firstName.collectAsStateWithLifecycle()

    AnimatedContent(
        targetState = state,
        contentKey = { it::class },
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "dashboardState"
    ) { current ->
        when (current) {
            UiState.Loading -> DashboardSkeleton(PaddingValues())
            is UiState.Error -> ErrorState(
                message = current.message,
                onRetry = viewModel::retry,
                modifier = Modifier.fillMaxSize()
            )
            UiState.Empty -> Unit
            is UiState.Success -> DashboardContent(
                data = current.data,
                firstName = firstName,
                fromCache = current.fromCache,
                isRefreshing = isRefreshing,
                onRefresh = { viewModel.refresh() },
                onSave = viewModel::saveMeasurement,
                onOpenMetric = onOpenMetric,
                onOpenHistory = onOpenHistory,
                onOpenLink = onOpenLink
            )
        }
    }
}

@Composable
private fun DashboardContent(
    data: DashboardData,
    firstName: String,
    fromCache: Boolean,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onSave: () -> Unit,
    onOpenMetric: (Metric) -> Unit,
    onOpenHistory: () -> Unit,
    onOpenLink: () -> Unit
) {
    var trendMetric by rememberSaveable { mutableStateOf(Metric.TEMPERATURE) }
    val reading = data.reading
    fun range(metric: Metric) = data.ranges[metric] ?: metric.defaultRange

    val listState = rememberLazyListState()
    // El botón muestra su texto solo arriba; al bajar queda solo el ícono.
    val fabExpanded by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            // Espacio extra abajo para que el botón flotante no tape la última tarjeta.
            contentPadding = PaddingValues(start = Dimens.ScreenPadding, end = Dimens.ScreenPadding, top = Dimens.ScreenPadding, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(Dimens.ItemGap)
        ) {
            item { DashboardHeader(reading, fromCache, isRefreshing, onRefresh) }
            item { Greeting(firstName) }
            item {
                StatusHero(
                    reading = reading,
                    ranges = data.ranges,
                    onClick = { onOpenMetric(Metric.TEMPERATURE) },
                    modifier = Modifier.tourTarget(TourKeys.HERO)
                )
            }
            item {
                Column(
                    modifier = Modifier.tourTarget(TourKeys.METRICS),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(Metric.HUMIDITY, Metric.LIGHT).forEach { metric ->
                        MetricPill(
                            metric = metric,
                            value = reading.valueOf(metric),
                            range = range(metric),
                            onClick = { onOpenMetric(metric) }
                        )
                    }
                }
            }
            item { SectionHeading(title = "Tendencia", action = "Ver historial", onAction = onOpenHistory) }
            item {
                TrendCard(trend = data.trend, selectedMetric = trendMetric, onMetricSelected = { trendMetric = it })
            }
            item {
                // Si algo está fuera de rango, la sugerencia lleva a los actuadores.
                RecommendationCard(
                    message = recommendationFor(reading, data.ranges),
                    onAct = if (isComfortable(reading, data.ranges)) null else onOpenLink
                )
            }
        }

        // Acción principal de la pantalla: un solo botón flotante.
        ExtendedFloatingActionButton(
            onClick = onSave,
            icon = { Icon(Icons.Rounded.BookmarkAdd, contentDescription = null) },
            text = { Text("Guardar medición") },
            expanded = fabExpanded,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .tourTarget(TourKeys.SAVE)
        )
    }
}

/** Saludo con el nombre del usuario. Sin nombre guardado, solo dice "Hola". */
@Composable
private fun Greeting(firstName: String) {
    Text(
        if (firstName.isBlank()) "Hola" else "Hola, $firstName",
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .padding(top = 8.dp)
            .semantics { heading() }
    )
}

@Composable
private fun DashboardHeader(
    reading: EnvironmentReading,
    fromCache: Boolean,
    isRefreshing: Boolean,
    onRefresh: () -> Unit
) {
    // Microinteracción: el ícono gira mientras se actualiza.
    val rotation by rememberInfiniteTransition(label = "refresh").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart),
        label = "refreshRotation"
    )
    val subtitle = when {
        fromCache -> "Sin conexión · última lectura ${formatTime(reading.timestampMillis)}"
        reading.usingHardware -> "Sensor conectado · ${formatTime(reading.timestampMillis)}"
        // Lectura que llegó por WiFi desde otro teléfono.
        reading.origin != ReadingSimulator.ORIGIN_LOCAL -> "${reading.origin} · ${formatTime(reading.timestampMillis)}"
        else -> "Datos simulados · ${formatTime(reading.timestampMillis)}"
    }

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        ClimaLogo(size = 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("ClimaPulse", style = MaterialTheme.typography.titleLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (fromCache) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        FilledTonalIconButton(
            onClick = onRefresh,
            enabled = !isRefreshing,
            modifier = Modifier.tourTarget(TourKeys.REFRESH),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        ) {
            Icon(
                Icons.Rounded.Refresh,
                contentDescription = if (isRefreshing) "Actualizando lecturas" else "Actualizar lecturas",
                modifier = if (isRefreshing) Modifier.rotate(rotation) else Modifier
            )
        }
    }
}

/**
 * Pieza principal: una forma de "galleta" que cambia según el estado.
 * Cómodo = turquesa y ondas suaves. Revisa = amarillo y ondas marcadas.
 * Cada lectura nueva gira la forma un poco: es el "pulso" de ClimaPulse.
 */
@Composable
private fun StatusHero(
    reading: EnvironmentReading,
    ranges: Map<Metric, IdealRange>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val comfortable = isComfortable(reading, ranges)
    val metric = Metric.TEMPERATURE
    val range = ranges[metric] ?: metric.defaultRange
    val value = reading.temperature
    val colors = MaterialTheme.colorScheme

    val container by animateColorAsState(if (comfortable) colors.primaryContainer else colors.tertiaryContainer, tween(600), label = "heroColor")
    val content by animateColorAsState(if (comfortable) colors.onPrimaryContainer else colors.onTertiaryContainer, tween(600), label = "heroText")
    // Cómodo = galleta de 9 ondas suaves. Revisa = flor de 5 ondas marcadas.
    val lobes = if (comfortable) 9 else 5
    val depth by animateFloatAsState(if (comfortable) 0.06f else 0.09f, spring(dampingRatio = 0.5f, stiffness = 120f), label = "heroDepth")
    // Cada lectura nueva suma un "pulso" y la forma gira 20 grados.
    var pulses by remember { mutableIntStateOf(0) }
    LaunchedEffect(reading.timestampMillis) { pulses++ }
    val rotation by animateFloatAsState(pulses * 20f, spring(dampingRatio = 0.6f, stiffness = 60f), label = "heroTurn")

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .aspectRatio(1f)
                .clip(CookieShape(lobes = lobes, depth = depth, rotationDegrees = rotation))
                .background(container)
                .clickable(role = Role.Button, onClickLabel = "Ver detalle de temperatura", onClick = onClick)
                .semantics(mergeDescendants = true) {
                    contentDescription = (if (comfortable) "Ambiente cómodo. " else "Revisa el ambiente. ") +
                        "Temperatura ${metric.formatWithUnit(value)}, ${metric.statusLabel(value, range)}"
                },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    if (comfortable) Icons.Rounded.CheckCircle else Icons.Rounded.WarningAmber,
                    contentDescription = null,
                    tint = content,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (comfortable) "Ambiente cómodo" else "Revisa el ambiente",
                    style = MaterialTheme.typography.titleMedium,
                    color = content
                )
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        metric.format(value),
                        style = MetricValueStyle.copy(fontSize = 84.sp, lineHeight = 92.sp, letterSpacing = (-3).sp),
                        color = content
                    )
                    Text(
                        metric.unit,
                        style = MaterialTheme.typography.headlineSmall,
                        color = content,
                        modifier = Modifier.padding(top = 14.dp, start = 4.dp)
                    )
                }
                Text(
                    "Ideal ${metric.format(range.min)}–${metric.formatWithUnit(range.max)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = content.copy(alpha = 0.8f)
                )
            }
        }
    }
}

/** Píldora tonal a lo ancho para humedad o luz. Solo se colorea el estado cuando sale de rango. */
@Composable
private fun MetricPill(
    metric: Metric,
    value: Float,
    range: IdealRange,
    onClick: () -> Unit
) {
    val status = metric.statusLabel(value, range)
    val inRange = value in range
    val colors = MaterialTheme.colorScheme

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ClimaShapes.Pill)
            .background(colors.surfaceContainerHigh)
            .clickable(role = Role.Button, onClickLabel = "Ver detalle de ${metric.label}", onClick = onClick)
            // Un solo anuncio para TalkBack en lugar de leer cada texto por separado.
            .semantics(mergeDescendants = true) {
                contentDescription = "${metric.label}: ${metric.formatWithUnit(value)}, $status"
            }
            .padding(start = 8.dp, end = 24.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconBadge(icon = metric.icon, tint = metric.accent, size = 48.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(metric.label, style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
            Text(
                status,
                style = MaterialTheme.typography.bodyMedium,
                color = if (inRange) colors.onSurface else colors.tertiary
            )
        }
        Text(
            metric.format(value),
            style = MetricValueStyle.copy(fontSize = 32.sp, lineHeight = 36.sp),
            color = colors.onSurface,
            maxLines = 1
        )
        Spacer(Modifier.width(4.dp))
        Text(metric.unit, style = MaterialTheme.typography.titleSmall, color = colors.onSurfaceVariant)
    }
}

@Composable
private fun TrendCard(
    trend: List<EnvironmentReading>,
    selectedMetric: Metric,
    onMetricSelected: (Metric) -> Unit
) {
    ClimaCard {
        Column(modifier = Modifier.padding(vertical = 16.dp)) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(Metric.entries) { metric ->
                    FilterChip(
                        selected = metric == selectedMetric,
                        onClick = { onMetricSelected(metric) },
                        label = { Text(metric.label) },
                        leadingIcon = {
                            Icon(metric.icon, contentDescription = null, tint = metric.accent, modifier = Modifier.width(18.dp))
                        },
                        shape = ClimaShapes.Pill
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                if (trend.size < 2) {
                    // Estado vacío del gráfico: explica cómo llenarlo.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .clip(ClimaShapes.Medium)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Guarda al menos dos mediciones para ver cómo cambia tu ambiente.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    val values = trend.map { it.valueOf(selectedMetric) }
                    Column {
                        TrendGraph(
                            values = values,
                            color = selectedMetric.accent,
                            description = "Tendencia de ${selectedMetric.label}: de ${selectedMetric.formatWithUnit(values.first())} a ${selectedMetric.formatWithUnit(values.last())}"
                        )
                        Spacer(Modifier.height(8.dp))
                        // Solo la primera y la última hora, para no repetir etiquetas.
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            listOf(trend.first(), trend.last()).forEach {
                                Text(
                                    formatTime(it.timestampMillis),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrendGraph(values: List<Float>, color: Color, description: String) {
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val dotFill = MaterialTheme.colorScheme.surfaceContainer
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .semantics { contentDescription = description }
    ) {
        val max = values.max()
        val min = values.min()
        val range = (max - min).coerceAtLeast(1f)
        val top = 10.dp.toPx()
        val usable = size.height - top * 2
        val stepX = size.width / (values.size - 1).coerceAtLeast(1)
        val points = values.mapIndexed { index, value ->
            Offset(index * stepX, top + usable - (value - min) / range * usable)
        }
        // Solo una línea base: la grilla sin valores no aportaba información.
        drawLine(gridColor, Offset(0f, size.height - 1.dp.toPx()), Offset(size.width, size.height - 1.dp.toPx()), strokeWidth = 1.dp.toPx())
        val line = Path().apply {
            points.forEachIndexed { index, point ->
                if (index == 0) {
                    moveTo(point.x, point.y)
                } else {
                    // Curva suave entre puntos.
                    val previous = points[index - 1]
                    val midX = (previous.x + point.x) / 2f
                    cubicTo(midX, previous.y, midX, point.y, point.x, point.y)
                }
            }
        }
        val area = Path().apply {
            addPath(line)
            lineTo(points.last().x, size.height)
            lineTo(points.first().x, size.height)
            close()
        }
        drawPath(area, color.copy(alpha = 0.12f))
        drawPath(line, color, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
        points.forEach { point ->
            drawCircle(color, radius = 5.dp.toPx(), center = point)
            drawCircle(dotFill, radius = 2.5.dp.toPx(), center = point)
        }
    }
}

@Composable
fun RecommendationCard(message: String, onAct: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ClimaShapes.Large)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(16.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(Icons.Rounded.Lightbulb, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
        Spacer(Modifier.width(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Sugerencia", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (onAct != null) {
                FilledTonalButton(onClick = onAct, modifier = Modifier.padding(top = 8.dp)) {
                    Text("Ir a actuadores")
                }
            }
        }
    }
}
