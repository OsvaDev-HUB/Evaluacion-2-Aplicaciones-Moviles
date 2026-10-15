package com.example.climapulse.ui.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingFlat
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.PriorityHigh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
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
import com.example.climapulse.ui.components.EmptyState
import com.example.climapulse.ui.components.ErrorState
import com.example.climapulse.ui.components.ListSkeleton
import com.example.climapulse.ui.components.formatDate
import com.example.climapulse.ui.components.formatTime
import com.example.climapulse.ui.home.accent
import com.example.climapulse.ui.home.format
import com.example.climapulse.ui.home.formatWithUnit
import com.example.climapulse.ui.home.icon
import com.example.climapulse.ui.home.isComfortable
import com.example.climapulse.ui.theme.ClimaShapes
import com.example.climapulse.ui.theme.Dimens
import com.example.climapulse.ui.theme.MetricValueStyle
import java.util.Calendar

@Composable
fun HistoryScreen(viewModel: HistoryViewModel = viewModel(factory = HistoryViewModel.Factory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    val ranges by viewModel.ranges.collectAsStateWithLifecycle()
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    // Métrica que se muestra en el gráfico y el resumen.
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
                val range = ranges[selected] ?: selected.defaultRange
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(Dimens.ScreenPadding),
                    verticalArrangement = Arrangement.spacedBy(Dimens.ItemGap)
                ) {
                    item { MetricSelector(selected, onSelect = { selected = it }) }
                    item { OverviewCard(rows, selected, range) }
                    // Una tarjeta por día: "Hoy", "Ayer" o la fecha.
                    items(groupByDay(rows), key = { it.first }) { (day, dayRows) ->
                        DayGroup(day, dayRows, selected, ranges)
                    }
                }
            }
        }
    }
}

/** Botones segmentados para elegir qué métrica ver. */
@Composable
private fun MetricSelector(selected: Metric, onSelect: (Metric) -> Unit) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        Metric.entries.forEachIndexed { index, metric ->
            SegmentedButton(
                selected = metric == selected,
                onClick = { onSelect(metric) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = Metric.entries.size),
                icon = { Icon(metric.icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
                label = { Text(metric.label, maxLines = 1) }
            )
        }
    }
}

/** Resumen de la métrica elegida: promedio, cambio, gráfico con el rango ideal y datos clave. */
@Composable
private fun OverviewCard(rows: List<EnvironmentReading>, metric: Metric, range: IdealRange) {
    val colors = MaterialTheme.colorScheme
    // La lista viene de la más nueva a la más antigua; el gráfico va de izquierda (antigua) a derecha.
    val chronological = rows.reversed()
    val values = chronological.map { it.valueOf(metric) }
    val average = values.average().toFloat()
    val change = values.last() - values.first()
    val inRange = values.count { it in range }
    val changeIcon = when {
        change > 0f -> Icons.AutoMirrored.Rounded.TrendingUp
        change < 0f -> Icons.AutoMirrored.Rounded.TrendingDown
        else -> Icons.AutoMirrored.Rounded.TrendingFlat
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ClimaShapes.ExtraLarge)
            .background(colors.surfaceContainerHigh)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Promedio", style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(metric.format(average), style = MetricValueStyle.copy(fontSize = 44.sp, lineHeight = 48.sp))
                    Spacer(Modifier.width(4.dp))
                    Text(metric.unit, style = MaterialTheme.typography.titleMedium, color = colors.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
                }
            }
            // Cuánto cambió desde la primera medición guardada.
            Row(
                modifier = Modifier
                    .clip(ClimaShapes.Pill)
                    .background(colors.surfaceContainerLowest)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(changeIcon, contentDescription = null, tint = colors.onSurface, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text((if (change > 0f) "+" else "") + metric.formatWithUnit(change), style = MaterialTheme.typography.labelLarge)
            }
        }

        if (values.size >= 2) {
            HistoryChart(values, range, metric)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatTime(chronological.first().timestampMillis), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                Text("Franja: rango ideal", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                Text(formatTime(chronological.last().timestampMillis), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("Mínima", metric.formatWithUnit(values.min()), Modifier.weight(1f))
            StatTile("Máxima", metric.formatWithUnit(values.max()), Modifier.weight(1f))
            StatTile("En rango", "$inRange de ${values.size}", Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(ClimaShapes.Large)
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .semantics(mergeDescendants = true) {}
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"), maxLines = 1)
    }
}

/**
 * Gráfico de línea con la franja del rango ideal de fondo.
 * Los puntos fuera de rango se pintan amarillos.
 */
@Composable
private fun HistoryChart(values: List<Float>, range: IdealRange, metric: Metric) {
    val lineColor = metric.accent
    val bandColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    val outColor = MaterialTheme.colorScheme.tertiary
    val dotFill = MaterialTheme.colorScheme.surfaceContainerHigh
    val description = "Gráfico de ${metric.label}: de ${metric.formatWithUnit(values.first())} a ${metric.formatWithUnit(values.last())}"

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
            .semantics { contentDescription = description }
    ) {
        // La escala incluye el rango ideal y deja un margen arriba y abajo,
        // así la franja se ve como franja y no llena todo el gráfico.
        val highest = maxOf(values.max(), range.max)
        val lowest = minOf(values.min(), range.min)
        val margin = (highest - lowest).coerceAtLeast(1f) * 0.35f
        val top = highest + margin
        val bottom = lowest - margin
        val span = top - bottom
        val pad = 8.dp.toPx()
        val usable = size.height - pad * 2
        fun yFor(value: Float) = pad + (top - value) / span * usable

        drawRect(bandColor, topLeft = Offset(0f, yFor(range.max)), size = Size(size.width, yFor(range.min) - yFor(range.max)))

        val stepX = (size.width - pad * 2) / (values.size - 1)
        val points = values.mapIndexed { index, value -> Offset(pad + index * stepX, yFor(value)) }
        val line = Path().apply {
            points.forEachIndexed { index, point -> if (index == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y) }
        }
        drawPath(line, lineColor, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
        // Con muchas mediciones no se dibujan todos los puntos, solo la línea.
        if (values.size <= 30) {
            points.forEachIndexed { index, point ->
                val color = if (values[index] in range) lineColor else outColor
                drawCircle(color, radius = 5.dp.toPx(), center = point)
                drawCircle(dotFill, radius = 2.dp.toPx(), center = point)
            }
        }
    }
}

/** Mediciones de un mismo día dentro de una tarjeta, separadas por líneas. */
@Composable
private fun DayGroup(day: String, rows: List<EnvironmentReading>, selected: Metric, ranges: Map<Metric, IdealRange>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.padding(top = 8.dp, start = 4.dp), verticalAlignment = Alignment.Bottom) {
            Text(day, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).semantics { heading() })
            Text(
                if (rows.size == 1) "1 medición" else "${rows.size} mediciones",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(ClimaShapes.Large)
                .background(MaterialTheme.colorScheme.surfaceContainer)
        ) {
            rows.forEachIndexed { index, row ->
                ReadingRow(row, selected, ranges)
                if (index < rows.lastIndex) {
                    HorizontalDivider(modifier = Modifier.padding(start = 64.dp), color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

/**
 * Una medición: estado (en rango o no), hora y origen, la métrica elegida en grande
 * y las otras dos en chico. Los valores fuera de rango van en amarillo oscuro.
 */
@Composable
private fun ReadingRow(row: EnvironmentReading, selected: Metric, ranges: Map<Metric, IdealRange>) {
    val colors = MaterialTheme.colorScheme
    val comfortable = isComfortable(row, ranges)
    fun inRange(metric: Metric) = row.valueOf(metric) in (ranges[metric] ?: metric.defaultRange)
    val others = Metric.entries.filter { it != selected }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "${formatTime(row.timestampMillis)}, ${row.origin}, " +
                    (if (comfortable) "en rango. " else "fuera de rango. ") +
                    Metric.entries.joinToString { "${it.label} ${it.formatWithUnit(row.valueOf(it))}" }
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(if (comfortable) colors.primaryContainer else colors.tertiaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (comfortable) Icons.Rounded.Check else Icons.Rounded.PriorityHigh,
                contentDescription = null,
                tint = if (comfortable) colors.onPrimaryContainer else colors.onTertiaryContainer,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(formatTime(row.timestampMillis), style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"))
            // Origen de la lectura: este teléfono o el nodo sensor remoto.
            Text(row.origin, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                selected.formatWithUnit(row.valueOf(selected)),
                style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                color = if (inRange(selected)) colors.onSurface else colors.tertiary
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                others.forEach { metric ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(metric.icon, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(2.dp))
                        Text(
                            metric.formatWithUnit(row.valueOf(metric)),
                            style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                            color = if (inRange(metric)) colors.onSurfaceVariant else colors.tertiary
                        )
                    }
                }
            }
        }
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
