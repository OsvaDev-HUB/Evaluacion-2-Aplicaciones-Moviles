package com.example.climapulse.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.climapulse.ui.UiState
import com.example.climapulse.ui.components.AppSnackbarHost
import com.example.climapulse.ui.components.BackTopBar
import com.example.climapulse.ui.components.ClimaCard
import com.example.climapulse.ui.components.ClimaTextField
import com.example.climapulse.ui.components.CollectMessages
import com.example.climapulse.ui.components.DashboardSkeleton
import com.example.climapulse.ui.components.ErrorState
import com.example.climapulse.ui.components.HeroCard
import com.example.climapulse.ui.components.IconBadge
import com.example.climapulse.ui.components.LinearMeter
import com.example.climapulse.ui.components.PrimaryButton
import com.example.climapulse.ui.components.SectionHeading
import com.example.climapulse.ui.components.StatusPill
import com.example.climapulse.ui.components.formatDate
import com.example.climapulse.ui.components.formatTime
import com.example.climapulse.ui.components.revealOnFocus
import com.example.climapulse.ui.home.accent
import com.example.climapulse.ui.home.description
import com.example.climapulse.ui.home.format
import com.example.climapulse.ui.home.formatWithUnit
import com.example.climapulse.ui.home.icon
import com.example.climapulse.ui.home.statusLabel
import com.example.climapulse.ui.theme.Dimens
import com.example.climapulse.ui.theme.MetricValueStyle

@Composable
fun MetricDetailScreen(
    onBack: () -> Unit,
    viewModel: MetricDetailViewModel = viewModel(factory = MetricDetailViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CollectMessages(viewModel.messages)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Box(Modifier.statusBarsPadding()) { BackTopBar(title = viewModel.metric.label, onBack = onBack) }
        },
        snackbarHost = { AppSnackbarHost() }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
        ) {
            when (val current = state) {
                UiState.Loading -> DashboardSkeleton(PaddingValues())
                is UiState.Error -> ErrorState(current.message, onRetry = viewModel::load, modifier = Modifier.fillMaxSize())
                UiState.Empty -> Unit
                is UiState.Success -> MetricDetailContent(current.data, viewModel)
            }
        }
    }
}

@Composable
private fun MetricDetailContent(data: MetricDetailData, viewModel: MetricDetailViewModel) {
    val metric = viewModel.metric
    val value = data.reading.valueOf(metric)
    val status = metric.statusLabel(value, data.range)
    val focusManager = LocalFocusManager.current
    val saveRequester = remember { BringIntoViewRequester() }
    val history = data.history.take(10)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Dimens.ScreenPadding, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(Dimens.ItemGap)
    ) {
        HeroCard(
            accent = metric.accent,
            modifier = Modifier.semantics(mergeDescendants = true) {
                contentDescription = "${metric.label} actual: ${metric.formatWithUnit(value)}, $status"
            }
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(icon = metric.icon, tint = metric.accent, size = 56.dp)
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Lectura actual", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "Actualizada ${formatTime(data.reading.timestampMillis)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                StatusPill(text = status, color = metric.accent)
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(metric.format(value), style = MetricValueStyle.copy(fontSize = MetricValueStyle.fontSize * 1.3f))
                Spacer(Modifier.width(6.dp))
                Text(
                    metric.unit,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 10.dp)
                )
            }
            Spacer(Modifier.height(12.dp))
            val span = (data.range.max - data.range.min).coerceAtLeast(1f)
            LinearMeter(
                progress = ((value - (data.range.min - span / 2)) / (span * 2)).coerceIn(0f, 1f),
                color = metric.accent
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Rango ideal ${metric.formatWithUnit(data.range.min)} – ${metric.formatWithUnit(data.range.max)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Text(
            metric.description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
        )

        SectionHeading(title = "Tu rango ideal")
        ClimaCard {
            Column(modifier = Modifier.padding(Dimens.CardPadding), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(icon = Icons.Rounded.Tune, size = 36.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Ajusta los límites para recibir alertas y ver el estado según tus preferencias.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ClimaTextField(
                        value = viewModel.minText,
                        onValueChange = { viewModel.minText = it.sanitizeDecimal() },
                        label = "Mínimo (${metric.unit})",
                        keyboardType = KeyboardType.Decimal,
                        error = viewModel.minError,
                        modifier = Modifier
                            .weight(1f)
                            .revealOnFocus(saveRequester)
                    )
                    ClimaTextField(
                        value = viewModel.maxText,
                        onValueChange = { viewModel.maxText = it.sanitizeDecimal() },
                        label = "Máximo (${metric.unit})",
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done,
                        onImeAction = {
                            focusManager.clearFocus()
                            viewModel.saveRange()
                        },
                        error = viewModel.maxError,
                        modifier = Modifier
                            .weight(1f)
                            .revealOnFocus(saveRequester)
                    )
                }
                Column(modifier = Modifier.bringIntoViewRequester(saveRequester)) {
                    PrimaryButton(
                        text = "Guardar rango",
                        icon = Icons.Rounded.Save,
                        loading = viewModel.saving,
                        onClick = {
                            focusManager.clearFocus()
                            viewModel.saveRange()
                        }
                    )
                    TextButton(onClick = viewModel::restoreDefault, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Text("Restaurar valores recomendados")
                    }
                }
            }
        }

        SectionHeading(title = "Mediciones guardadas")
        if (history.isEmpty()) {
            Text(
                "Todavía no guardas mediciones. Usa \"Guardar medición actual\" en Inicio para empezar.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            ClimaCard {
                history.forEachIndexed { index, reading ->
                    ListItem(
                        headlineContent = { Text(formatTime(reading.timestampMillis)) },
                        supportingContent = { Text(formatDate(reading.timestampMillis)) },
                        trailingContent = {
                            Text(
                                metric.formatWithUnit(reading.valueOf(metric)),
                                style = MaterialTheme.typography.titleMedium,
                                color = metric.accent
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                    if (index < history.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

private fun String.sanitizeDecimal(): String = filter { it.isDigit() || it == ',' || it == '.' || it == '-' }.take(7)
