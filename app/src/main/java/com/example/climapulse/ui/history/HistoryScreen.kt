package com.example.climapulse.ui.history

import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.automirrored.rounded.TrendingFlat
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Schedule
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.climapulse.data.EnvironmentReading
import com.example.climapulse.data.Metric
import com.example.climapulse.ui.UiState
import com.example.climapulse.ui.components.ClimaCard
import com.example.climapulse.ui.components.CollectMessages
import com.example.climapulse.ui.components.ConfirmDialog
import com.example.climapulse.ui.components.EmptyState
import com.example.climapulse.ui.components.ErrorState
import com.example.climapulse.ui.components.HeroCard
import com.example.climapulse.ui.components.IconBadge
import com.example.climapulse.ui.components.ListSkeleton
import com.example.climapulse.ui.components.ScreenTitle
import com.example.climapulse.ui.components.SectionHeading
import com.example.climapulse.ui.components.formatDate
import com.example.climapulse.ui.components.formatTime
import com.example.climapulse.ui.home.accent
import com.example.climapulse.ui.home.formatWithUnit
import com.example.climapulse.ui.home.icon
import com.example.climapulse.ui.theme.ClimaShapes
import com.example.climapulse.ui.theme.Dimens
import com.example.climapulse.ui.theme.MetricValueStyle

@Composable
fun HistoryScreen(viewModel: HistoryViewModel = viewModel(factory = HistoryViewModel.Factory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    var confirmClear by rememberSaveable { mutableStateOf(false) }
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
            // Arriba: el botón de borrar queda a la altura del título.
            verticalAlignment = Alignment.Top
        ) {
            Box(Modifier.weight(1f)) {
                ScreenTitle(title = "Historial", subtitle = "Observa cómo cambia tu ambiente durante el día.")
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
                    contentPadding = PaddingValues(Dimens.ScreenPadding),
                    verticalArrangement = Arrangement.spacedBy(Dimens.ItemGap)
                ) {
                    item { SummaryCard(rows) }
                    item { SectionHeading(title = "${rows.size} mediciones") }
                    item { HistoryHeader() }
                    items(rows, key = { it.timestampMillis }) { row -> HistoryRowItem(row) }
                }
            }
        }
    }
}

/** Resumen compacto de la temperatura: mínima, promedio, máxima y cuánto cambió. */
@Composable
private fun SummaryCard(rows: List<EnvironmentReading>) {
    val metric = Metric.TEMPERATURE
    val temperatures = rows.map { it.temperature }
    val average = temperatures.average().toFloat()
    val change = rows.first().temperature - rows.last().temperature
    // El ícono sigue el signo del cambio: sube, baja o se mantiene.
    val changeIcon = when {
        change > 0f -> Icons.AutoMirrored.Rounded.TrendingUp
        change < 0f -> Icons.AutoMirrored.Rounded.TrendingDown
        else -> Icons.AutoMirrored.Rounded.TrendingFlat
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ClimaShapes.Large)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(metric.icon, contentDescription = null, tint = metric.accent)
            Spacer(Modifier.width(8.dp))
            Text("Temperatura", style = MaterialTheme.typography.titleSmall)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SummaryStat("Mín", metric.formatWithUnit(temperatures.min()), Icons.Rounded.ArrowDownward)
            SummaryStat("Prom", metric.formatWithUnit(average), Icons.Rounded.DragHandle)
            SummaryStat("Máx", metric.formatWithUnit(temperatures.max()), Icons.Rounded.ArrowUpward)
            SummaryStat("Cambio", (if (change > 0f) "+" else "") + metric.formatWithUnit(change), changeIcon)
        }
    }
}

@Composable
private fun SummaryStat(label: String, value: String, icon: ImageVector) {
    Row(
        modifier = Modifier
            .clip(ClimaShapes.Pill)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text("$label ", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.labelLarge)
    }
}

// Tabla densa: una fila por medición y columnas alineadas, sin una tarjeta por fila.
private val TimeColumn = Modifier.width(96.dp)

@Composable
private fun HistoryHeader() {
    Row(modifier = Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Hora", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = TimeColumn)
        // Cada columna lleva su nombre en texto, no solo un ícono.
        listOf("Temp.", "Humedad", "Luz").forEach { label ->
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun HistoryRowItem(row: EnvironmentReading) {
    val numbers = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum")
    Column(
        modifier = Modifier.semantics(mergeDescendants = true) {
            contentDescription = "${formatTime(row.timestampMillis)}, ${row.origin}: " +
                Metric.entries.joinToString { "${it.label} ${it.formatWithUnit(row.valueOf(it))}" }
        }
    ) {
        Row(modifier = Modifier.padding(horizontal = 4.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = TimeColumn) {
                Text(formatTime(row.timestampMillis), style = numbers)
                // Origen de la lectura: este teléfono o el nodo sensor remoto.
                Text(row.origin, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            Metric.entries.forEach { metric ->
                Text(
                    metric.formatWithUnit(row.valueOf(metric)),
                    style = numbers,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun ReadingPill(metric: Metric, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(metric.icon, contentDescription = null, tint = metric.accent, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}
