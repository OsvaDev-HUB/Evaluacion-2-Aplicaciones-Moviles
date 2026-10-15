@file:OptIn(SavedStateHandleSaveableApi::class)

package com.example.climapulse.ui.detail

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.SavedStateHandleSaveableApi
import androidx.lifecycle.viewmodel.compose.saveable
import androidx.navigation.toRoute
import com.example.climapulse.data.EnvironmentReading
import com.example.climapulse.data.EnvironmentRepository
import com.example.climapulse.data.IdealRange
import com.example.climapulse.data.Metric
import com.example.climapulse.ui.UiState
import com.example.climapulse.ui.appViewModelFactory
import com.example.climapulse.ui.components.formatNumber
import com.example.climapulse.ui.navigation.MetricDetailRoute
import com.example.climapulse.ui.toUserMessage
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MetricDetailData(
    val reading: EnvironmentReading,
    val range: IdealRange,
    val history: List<EnvironmentReading>
)

class MetricDetailViewModel(
    private val environment: EnvironmentRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    // Argumento tipado: el compilador garantiza que siempre llega una Metric válida.
    val metric: Metric = savedStateHandle.toRoute<MetricDetailRoute>().metric

    // Formulario conectado al ViewModel + SavedStateHandle: sobrevive al giro de pantalla.
    private var _minText by savedStateHandle.saveable { mutableStateOf("") }
    var minText: String
        get() = _minText
        set(value) {
            _minText = value
            minError = null
        }
    private var _maxText by savedStateHandle.saveable { mutableStateOf("") }
    var maxText: String
        get() = _maxText
        set(value) {
            _maxText = value
            maxError = null
        }
    var minError by mutableStateOf<String?>(null)
        private set
    var maxError by mutableStateOf<String?>(null)
        private set
    var saving by mutableStateOf(false)
        private set

    private val readingState = MutableStateFlow<UiState<EnvironmentReading>>(UiState.Loading)
    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    val uiState: StateFlow<UiState<MetricDetailData>> = combine(
        readingState,
        environment.idealRanges,
        environment.history
    ) { reading, ranges, history ->
        when (reading) {
            is UiState.Success -> UiState.Success(
                MetricDetailData(reading.data, ranges[metric] ?: metric.defaultRange, history),
                reading.fromCache
            )
            is UiState.Error -> reading
            UiState.Empty -> UiState.Empty
            UiState.Loading -> UiState.Loading
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

    init {
        load()
        viewModelScope.launch {
            if (minText.isEmpty() && maxText.isEmpty()) {
                val range = environment.idealRanges.first()[metric] ?: metric.defaultRange
                minText = formatNumber(range.min, metric.decimals)
                maxText = formatNumber(range.max, metric.decimals)
            }
        }
    }

    fun load() {
        viewModelScope.launch {
            readingState.value = UiState.Loading
            readingState.value = try {
                environment.cachedReading()?.let { UiState.Success(it, fromCache = true) }
                    ?: UiState.Success(environment.fetchReading())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                UiState.Error(e.toUserMessage())
            }
        }
    }

    fun saveRange() {
        val min = minText.replace(',', '.').toFloatOrNull()
        val max = maxText.replace(',', '.').toFloatOrNull()
        minError = if (min == null) "Ingresa un número" else null
        maxError = when {
            max == null -> "Ingresa un número"
            min != null && max <= min -> "Debe ser mayor que el mínimo"
            else -> null
        }
        if (min == null || max == null || minError != null || maxError != null) return
        viewModelScope.launch {
            saving = true
            environment.saveIdealRange(metric, IdealRange(min, max))
            saving = false
            _messages.send("Rango ideal de ${metric.label.lowercase()} guardado")
        }
    }

    fun restoreDefault() {
        minText = formatNumber(metric.defaultRange.min, metric.decimals)
        maxText = formatNumber(metric.defaultRange.max, metric.decimals)
        saveRange()
    }

    companion object {
        val Factory = appViewModelFactory { c, handle -> MetricDetailViewModel(c.environmentRepository, handle) }
    }
}
