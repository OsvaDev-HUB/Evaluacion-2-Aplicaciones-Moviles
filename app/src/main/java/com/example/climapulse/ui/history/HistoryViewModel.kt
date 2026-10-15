package com.example.climapulse.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.climapulse.data.EnvironmentReading
import com.example.climapulse.data.EnvironmentRepository
import com.example.climapulse.ui.UiState
import com.example.climapulse.ui.appViewModelFactory
import com.example.climapulse.ui.toUserMessage
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HistoryViewModel(private val environment: EnvironmentRepository) : ViewModel() {
    private val _messages = Channel<String>(Channel.BUFFERED)
    private val _isSaving = MutableStateFlow(false)
    val messages = _messages.receiveAsFlow()
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    val uiState: StateFlow<UiState<List<EnvironmentReading>>> = environment.history
        .map { list -> if (list.isEmpty()) UiState.Empty else UiState.Success(list) }
        .catch { emit(UiState.Error(it.toUserMessage())) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

    fun recordFirstMeasurement() {
        if (_isSaving.value) return
        viewModelScope.launch {
            _isSaving.value = true
            try {
                environment.saveToHistory(environment.fetchReading())
                _messages.send("Primera medición registrada")
            } catch (e: Exception) {
                _messages.send(e.toUserMessage())
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            environment.clearHistory()
            _messages.send("Historial borrado")
        }
    }

    companion object {
        val Factory = appViewModelFactory { c, _ -> HistoryViewModel(c.environmentRepository) }
    }
}
