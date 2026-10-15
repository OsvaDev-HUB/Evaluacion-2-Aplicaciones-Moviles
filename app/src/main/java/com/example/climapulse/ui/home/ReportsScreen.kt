package com.example.climapulse.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.climapulse.data.Metric
import com.example.climapulse.ui.UiState
import com.example.climapulse.ui.components.DashboardSkeleton
import com.example.climapulse.ui.components.ErrorState
import com.example.climapulse.ui.components.HeroCard
import com.example.climapulse.ui.components.IconBadge
import com.example.climapulse.ui.components.PrimaryButton
import com.example.climapulse.ui.components.ScreenTitle
import com.example.climapulse.ui.components.StatusPill
import com.example.climapulse.ui.components.formatDate
import com.example.climapulse.ui.theme.ClimaShapes
import com.example.climapulse.ui.theme.Dimens
import com.example.climapulse.ui.theme.Teal
import com.example.climapulse.ui.theme.Yellow

@Composable
fun ReportsScreen(viewModel: HomeViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()

    when (val current = state) {
        UiState.Loading -> DashboardSkeleton(PaddingValues())
        is UiState.Error -> ErrorState(current.message, onRetry = viewModel::retry, modifier = Modifier.fillMaxSize())
        UiState.Empty -> Unit
        is UiState.Success -> ReportsContent(
            data = current.data,
            isRefreshing = isRefreshing,
            onGenerate = { viewModel.refresh(successMessage = "Reporte actualizado") }
        )
    }
}

@Composable
private fun ReportsContent(data: DashboardData, isRefreshing: Boolean, onGenerate: () -> Unit) {
    val reading = data.reading
    val comfortable = isComfortable(reading, data.ranges)
    val statusColor = if (comfortable) Teal else Yellow

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Dimens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.ItemGap)
    ) {
        ScreenTitle(title = "Reportes", subtitle = "Resume el estado de tu ambiente en un vistazo.")
        Spacer(Modifier.height(4.dp))

        HeroCard(accent = statusColor) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Reporte ambiental", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Hoy, ${formatDate(reading.timestampMillis)}", style = MaterialTheme.typography.titleLarge)
                }
                IconBadge(icon = Icons.Rounded.Analytics, tint = MaterialTheme.colorScheme.primary, size = 52.dp)
            }
            Spacer(Modifier.height(16.dp))
            StatusPill(
                text = if (comfortable) "Resumen positivo" else "Revisión recomendada",
                color = statusColor,
                icon = if (comfortable) Icons.Rounded.CheckCircle else Icons.Rounded.WarningAmber
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (comfortable) "El ambiente se mantiene dentro del rango ideal." else "Hay lecturas que conviene revisar durante el día.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Metric.entries.forEach { metric ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(ClimaShapes.Medium)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .padding(12.dp)
                            .semantics(mergeDescendants = true) {},
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        IconBadge(icon = metric.icon, tint = metric.accent, size = 36.dp)
                        Text(metric.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(metric.formatWithUnit(reading.valueOf(metric)), style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
        }

        PrimaryButton(
            text = if (isRefreshing) "Actualizando reporte..." else "Generar reporte actualizado",
            onClick = onGenerate,
            loading = isRefreshing,
            icon = Icons.Rounded.Analytics
        )

        RecommendationCard(recommendationFor(reading, data.ranges))
    }
}
