package com.example.climapulse.ui.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingFlat
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.climapulse.data.EnvironmentReading
import com.example.climapulse.data.IdealRange
import com.example.climapulse.data.Metric
import com.example.climapulse.ui.UiState
import com.example.climapulse.ui.components.CollectMessages
import com.example.climapulse.ui.components.ConfirmDialog
import com.example.climapulse.ui.components.CookieShape
import com.example.climapulse.ui.components.EmptyState
import com.example.climapulse.ui.components.ErrorState
import com.example.climapulse.ui.components.ListSkeleton
import com.example.climapulse.ui.components.formatDate
import com.example.climapulse.ui.components.formatTime
import com.example.climapulse.ui.home.format
import com.example.climapulse.ui.home.formatWithUnit
import com.example.climapulse.ui.home.icon
import com.example.climapulse.ui.home.isComfortable
import com.example.climapulse.ui.theme.BrandTeal
import com.example.climapulse.ui.theme.BrandYellow
import com.example.climapulse.ui.theme.ClimaShapes
import com.example.climapulse.ui.theme.Dimens
import com.example.climapulse.ui.theme.MetricValueStyle
import com.example.climapulse.ui.theme.Navy
import java.util.Calendar

// Colores de la tarjeta principal: el azul marino del logo con texto claro encima.
private val OnHero = Color(0xFFF5F7FA)
private val OnHeroMuted = Color(0xFFA9B6C5)

@Composable
fun HistoryScreen(viewModel: HistoryViewModel = viewModel(factory = HistoryViewModel.Factory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    val ranges by viewModel.ranges.collectAsStateWithLifecycle()
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    // Métrica que se muestra en la tarjeta principal.
    var selected by rememberSaveable { mutableStateOf(Metric.TEMPERATURE) }
    CollectMessages(viewModel.messages)

    if (confirmClear) {
        ConfirmDialog(
            title = "¿Borrar historial?",
            message = "Se eliminarán todas las mediciones guardadas. Esta acción no se puede deshacer.",
            confirmLabel = "Borrar",
            icon = Icons.Rounded.DeleteSweep,
            destructive = true,
            onConfirm = {
                confirmClear = false
                viewModel.clearHistory()
            },
            onDismiss = { confirmClear = false }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.padding(start = Dimens.ScreenPadding, end = 8.dp, top = Dimens.ScreenPadding),
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Historial", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
                Text(
                    "Mediciones guardadas en este teléfono",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (state is UiState.Success) {
                IconButton(onClick = { confirmClear = true }) {
                    Icon(Icons.Rounded.DeleteSweep, contentDescription = "Borrar historial")
                }
            }
        }
        // Los estados de carga, vacío y error van fuera de la lista: solo el éxito usa LazyColumn.
        when (val current = state) {
            UiState.Loading -> Box(Modifier.padding(Dimens.ScreenPadding)) { ListSkeleton() }
            is UiState.Error -> ErrorState(
                current.message,
                onRetry = viewModel::recordFirstMeasurement,
                modifier = Modifier.weight(1f)
            )
            UiState.Empty -> EmptyState(
                title = "Aún no hay mediciones",
                message = "Guarda tu primera lectura para empezar a ver tendencias y promedios del día.",
                actionLabel = "Registrar primera medición",
                onAction = viewModel::recordFirstMeasurement,
                loading = isSaving,
                modifier = Modifier.weight(1f)
            )
            is UiState.Success -> {
                val rows = current.data
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(start = Dimens.ScreenPadding, end = Dimens.ScreenPadding, top = 16.dp, bottom = Dimens.ScreenPadding),
                    verticalArrangement = Arrangement.spacedBy(Dimens.ItemGap)
                ) {
                    item {
                        SummaryHero(
                            rows = rows,
                            metric = selected,
                            range = ranges[selected] ?: selected.defaultRange,
                            onSelect = { selected = it }
                        )
                    }
                    // Una línea de tiempo por día: "Hoy", "Ayer" o la fecha.
                    items(groupByDay(rows), key = { it.first }) { (day, dayRows) ->
                        DayTimeline(day, dayRows, ranges)
                    }
                }
            }
        }
    }
}

/**
 * Tarjeta principal en el azul marino del logo: selector de métrica, promedio,
 * una mini galleta con el porcentaje en rango, gráfico y datos clave.
 */
@Composable
private fun SummaryHero(
    rows: List<EnvironmentReading>,
    metric: Metric,
    range: IdealRange,
    onSelect: (Metric) -> Unit
) {
    // En tema oscuro el fondo ya es azul marino, así que la tarjeta usa un tono más claro.
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val heroColor = if (isDark) MaterialTheme.colorScheme.surfaceContainerHigh else Navy

    // La lista viene de la más nueva a la más antigua; el gráfico va de izquierda (antigua) a derecha.
    val chronological = rows.reversed()
    val values = chronological.map { it.valueOf(metric) }
    val average = values.average().toFloat()
    val change = values.last() - values.first()
    val percentInRange = values.count { it in range } * 100 / values.size

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ClimaShapes.Hero)
            .background(heroColor)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        MetricTabs(selected = metric, onSelect = onSelect)

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Promedio de ${metric.label.lowercase()}", style = MaterialTheme.typography.labelLarge, color = OnHeroMuted)
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        metric.format(average),
                        style = MetricValueStyle.copy(fontSize = 56.sp, lineHeight = 60.sp, letterSpacing = (-2).sp),
                        color = OnHero
                    )
                    Text(
                        metric.unit,
                        style = MaterialTheme.typography.titleMedium,
                        color = OnHeroMuted,
                        modifier = Modifier.padding(top = 8.dp, start = 4.dp)
                    )
                }
                Text(
                    "${values.size} mediciones guardadas",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnHeroMuted
                )
            }
            InRangeCookie(percentInRange)
        }

        if (values.size >= 2) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                HeroChart(values, range, metric)
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(formatTime(chronological.first().timestampMillis), style = MaterialTheme.typography.labelSmall, color = OnHeroMuted)
                    Text(
                        "Franja: ideal ${metric.format(range.min)}–${metric.formatWithUnit(range.max)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = OnHeroMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                    Text(formatTime(chronological.last().timestampMillis), style = MaterialTheme.typography.labelSmall, color = OnHeroMuted)
                }
            }
        }

        // Datos clave separados por líneas finas.
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            HeroStat("Mínima", metric.formatWithUnit(values.min()), Modifier.weight(1f))
            HeroDivider()
            HeroStat("Máxima", metric.formatWithUnit(values.max()), Modifier.weight(1f))
            HeroDivider()
            val changeIcon = when {
                change > 0f -> Icons.AutoMirrored.Rounded.TrendingUp
                change < 0f -> Icons.AutoMirrored.Rounded.TrendingDown
                else -> Icons.AutoMirrored.Rounded.TrendingFlat
            }
            HeroStat("Cambio", (if (change > 0f) "+" else "") + metric.formatWithUnit(change), Modifier.weight(1f), changeIcon)
        }
    }
}

/** Píldoras para elegir la métrica, dentro de la tarjeta azul marino. */
@Composable
private fun MetricTabs(selected: Metric, onSelect: (Metric) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ClimaShapes.Pill)
            .background(OnHero.copy(alpha = 0.08f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Metric.entries.forEach { metric ->
            val isSelected = metric == selected
            Row(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .clip(ClimaShapes.Pill)
                    .background(if (isSelected) BrandTeal else Color.Transparent)
                    .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(metric) }),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val color = if (isSelected) Navy else OnHero
                Icon(metric.icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(metric.label, style = MaterialTheme.typography.labelLarge, color = color, maxLines = 1)
            }
        }
    }
}

/** Mini galleta con el porcentaje de mediciones en rango: turquesa si va bien, amarillo si no. */
@Composable
private fun InRangeCookie(percent: Int) {
    val good = percent >= 75
    Box(
        modifier = Modifier
            .size(96.dp)
            .clip(CookieShape(lobes = if (good) 9 else 5, depth = if (good) 0.07f else 0.1f))
            .background(if (good) BrandTeal else BrandYellow)
            .semantics(mergeDescendants = true) { contentDescription = "$percent por ciento de las mediciones en rango" },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$percent %", style = MaterialTheme.typography.titleLarge, color = Navy)
            Text("en rango", style = MaterialTheme.typography.labelSmall, color = Navy)
        }
    }
}

@Composable
private fun HeroStat(label: String, value: String, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Column(
        modifier = modifier.semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = OnHeroMuted)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = OnHero, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
            }
            Text(value, style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"), color = OnHero, maxLines = 1)
        }
    }
}

@Composable
private fun HeroDivider() {
    Box(
        Modifier
            .width(1.dp)
            .fillMaxHeight()
            .background(OnHero.copy(alpha = 0.15f))
    )
}

/**
 * Gráfico sobre la tarjeta azul marino: franja turquesa del rango ideal,
 * línea turquesa y puntos amarillos cuando la lectura sale del rango.
 */
@Composable
private fun HeroChart(values: List<Float>, range: IdealRange, metric: Metric) {
    val description = "Gráfico de ${metric.label}: de ${metric.formatWithUnit(values.first())} a ${metric.formatWithUnit(values.last())}"
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(130.dp)
            .semantics { contentDescription = description }
    ) {
        // La escala incluye el rango ideal y deja margen, así la franja se ve como franja.
        val highest = maxOf(values.max(), range.max)
        val lowest = minOf(values.min(), range.min)
        val margin = (highest - lowest).coerceAtLeast(1f) * 0.35f
        val top = highest + margin
        val span = top - (lowest - margin)
        val pad = 8.dp.toPx()
        val usable = size.height - pad * 2
        fun yFor(value: Float) = pad + (top - value) / span * usable

        drawRect(
            BrandTeal.copy(alpha = 0.18f),
            topLeft = Offset(0f, yFor(range.max)),
            size = Size(size.width, yFor(range.min) - yFor(range.max))
        )

        val stepX = (size.width - pad * 2) / (values.size - 1)
        val points = values.mapIndexed { index, value -> Offset(pad + index * stepX, yFor(value)) }
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
        drawPath(line, BrandTeal, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
        // Con muchas mediciones no se dibujan todos los puntos, solo la línea.
        if (values.size <= 30) {
            points.forEachIndexed { index, point ->
                val inRange = values[index] in range
                drawCircle(if (inRange) BrandTeal else BrandYellow, radius = if (inRange) 4.dp.toPx() else 6.dp.toPx(), center = point)
            }
        }
    }
}

/** Mediciones de un día como línea de tiempo. */
@Composable
private fun DayTimeline(day: String, rows: List<EnvironmentReading>, ranges: Map<Metric, IdealRange>) {
    Column {
        Row(modifier = Modifier.padding(top = 12.dp, bottom = 8.dp, start = 4.dp), verticalAlignment = Alignment.Bottom) {
            Text(day, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).semantics { heading() })
            Text(
                if (rows.size == 1) "1 medición" else "${rows.size} mediciones",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        rows.forEachIndexed { index, row ->
            TimelineItem(row, ranges, isFirst = index == 0, isLast = index == rows.lastIndex)
        }
    }
}

/**
 * Un punto de la línea de tiempo: el nodo (turquesa en rango, amarillo si algo se salió)
 * y una tarjeta con la hora, el origen y los tres valores.
 */
@Composable
private fun TimelineItem(row: EnvironmentReading, ranges: Map<Metric, IdealRange>, isFirst: Boolean, isLast: Boolean) {
    val colors = MaterialTheme.colorScheme
    val comfortable = isComfortable(row, ranges)
    val lineColor = colors.outlineVariant
    val nodeColor = if (comfortable) BrandTeal else BrandYellow
    val background = colors.background

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .semantics(mergeDescendants = true) {
                contentDescription = "${formatTime(row.timestampMillis)}, ${row.origin}, " +
                    (if (comfortable) "en rango. " else "fuera de rango. ") +
                    Metric.entries.joinToString { "${it.label} ${it.formatWithUnit(row.valueOf(it))}" }
            }
    ) {
        // Columna de la línea: se dibuja detrás con drawBehind (línea vertical y nodo).
        Box(
            modifier = Modifier
                .width(28.dp)
                .fillMaxHeight()
                .drawBehind {
                    val x = size.width / 2f
                    val nodeY = 30.dp.toPx()
                    val stroke = 2.dp.toPx()
                    if (!isFirst) drawLine(lineColor, Offset(x, 0f), Offset(x, nodeY), strokeWidth = stroke)
                    if (!isLast) drawLine(lineColor, Offset(x, nodeY), Offset(x, size.height), strokeWidth = stroke)
                    drawCircle(background, radius = 9.dp.toPx(), center = Offset(x, nodeY))
                    drawCircle(nodeColor, radius = 6.dp.toPx(), center = Offset(x, nodeY))
                }
        )
        Spacer(Modifier.width(8.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = 10.dp)
                .clip(ClimaShapes.Large)
                .background(colors.surfaceContainerLow)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(formatTime(row.timestampMillis), style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"))
                Spacer(Modifier.width(8.dp))
                // Origen de la lectura: este teléfono o el nodo sensor remoto.
                Text(row.origin, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1, modifier = Modifier.weight(1f))
                if (!comfortable) {
                    Text("Revisar", style = MaterialTheme.typography.labelMedium, color = colors.tertiary)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Metric.entries.forEach { metric ->
                    val inRange = row.valueOf(metric) in (ranges[metric] ?: metric.defaultRange)
                    ValueChip(metric, metric.formatWithUnit(row.valueOf(metric)), inRange, Modifier.weight(1f))
                }
            }
        }
    }
}

/** Valor de una métrica: gris si está en rango, amarillo del logo si se salió. */
@Composable
private fun ValueChip(metric: Metric, value: String, inRange: Boolean, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val background = if (inRange) colors.surfaceContainerHigh else BrandYellow
    val content = if (inRange) colors.onSurface else Navy
    Row(
        modifier = modifier
            .clip(ClimaShapes.Pill)
            .background(background)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(metric.icon, contentDescription = null, tint = content, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(value, style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"), color = content, maxLines = 1)
    }
}

/** Agrupa las mediciones por día y le pone nombre a cada grupo. */
private fun groupByDay(rows: List<EnvironmentReading>): List<Pair<String, List<EnvironmentReading>>> =
    rows.groupBy { dayLabel(it.timestampMillis) }.toList()

private fun dayLabel(millis: Long): String {
    val day = Calendar.getInstance().apply { timeInMillis = millis }
    val today = Calendar.getInstance()
    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    fun sameDay(a: Calendar, b: Calendar) =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    return when {
        sameDay(day, today) -> "Hoy"
        sameDay(day, yesterday) -> "Ayer"
        else -> formatDate(millis).replaceFirstChar { it.uppercase() }
    }
}
