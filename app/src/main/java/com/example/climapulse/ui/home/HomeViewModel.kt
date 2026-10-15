package com.example.climapulse.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.climapulse.data.AlertNotifier
import com.example.climapulse.data.ConnectivityObserver
import com.example.climapulse.data.EnvironmentReading
import com.example.climapulse.data.EnvironmentRepository
import com.example.climapulse.data.IdealRange
import com.example.climapulse.data.LinkState
import com.example.climapulse.data.Metric
import com.example.climapulse.data.SessionRepository
import com.example.climapulse.data.WifiLink
import com.example.climapulse.ui.UiState
import com.example.climapulse.ui.appViewModelFactory
import com.example.climapulse.ui.toUserMessage
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DashboardData(
    val reading: EnvironmentReading,
    val ranges: Map<Metric, IdealRange>,
    val trend: List<EnvironmentReading>
)

/** Compartido por Inicio, Sensor y Reportes (vive mientras el Home esté en la pila). */
class HomeViewModel(
    private val environment: EnvironmentRepository,
    private val session: SessionRepository,
    connectivity: ConnectivityObserver,
    link: WifiLink,
    private val notifier: AlertNotifier
) : ViewModel() {
    private val readingState = MutableStateFlow<UiState<EnvironmentReading>>(UiState.Loading)
    private val _isRefreshing = MutableStateFlow(false)
    private val _messages = Channel<String>(Channel.BUFFERED)
    private var loadJob: Job? = null

    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()
    val messages = _messages.receiveAsFlow()
    val linkState: StateFlow<LinkState> = link.state

    // null mientras se lee la bandera; así no se muestra la bienvenida antes de tiempo.
    val introDone: StateFlow<Boolean?> = session.introDone
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** true si la huella ya está activada para entrar a la app. */
    val biometricEnabled: StateFlow<Boolean> = session.biometricEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val isOnline: StateFlow<Boolean> = connectivity.isOnlineFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val uiState: StateFlow<UiState<DashboardData>> = combine(
        readingState,
        environment.idealRanges,
        environment.history
    ) { reading, ranges, history ->
        when (reading) {
            is UiState.Success -> UiState.Success(
                DashboardData(reading.data, ranges, history.take(6).reversed()),
                fromCache = reading.fromCache
            )
            is UiState.Error -> reading
            UiState.Empty -> UiState.Empty
            UiState.Loading -> UiState.Loading
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

    init {
        load(showSkeleton = true, announce = false)
        // Alerta: avisa una vez cuando el ambiente pasa de "cómodo" a "revisa",
        // solo si el usuario dejó activadas las alertas en Perfil.
        viewModelScope.launch {
            var wasComfortable = true
            combine(readingState, environment.idealRanges, session.notificationsEnabled) { state, ranges, enabled ->
                Triple(state, ranges, enabled)
            }.collect { (state, ranges, enabled) ->
                val reading = (state as? UiState.Success)?.data ?: return@collect
                val comfortable = isComfortable(reading, ranges)
                if (wasComfortable && !comfortable && enabled) {
                    notifier.showOutOfRange(recommendationFor(reading, ranges))
                }
                wasComfortable = comfortable
            }
        }
        // Lecturas en tiempo real del enlace WiFi (cada 3 segundos).
        viewModelScope.launch {
            environment.liveReadings.collect { reading ->
                readingState.value = UiState.Success(reading)
            }
        }
        viewModelScope.launch {
            session.autoRefreshEnabled.collectLatest { enabled ->
                while (enabled) {
                    delay(AUTO_REFRESH_MILLIS)
                    load(showSkeleton = false, announce = false)
                }
            }
        }
        // Al recuperar la conexión se sincroniza solo, sin que el usuario tenga que reintentar.
        viewModelScope.launch {
            connectivity.isOnlineFlow.drop(1).filter { it }.collect {
                val current = readingState.value
                if (current is UiState.Error || (current as? UiState.Success)?.fromCache == true) {
                    load(showSkeleton = current is UiState.Error, announce = false)
                }
            }
        }
    }

    fun retry() = load(showSkeleton = true, announce = false)

    fun refresh(successMessage: String = "Lecturas actualizadas") =
        load(showSkeleton = false, announce = true, successMessage = successMessage)

    fun onNotificationPermission(granted: Boolean) {
        viewModelScope.launch { session.setNotificationsEnabled(granted) }
    }

    /** Se llama al terminar o saltar el tutorial: este usuario ya no lo verá al ingresar. */
    fun finishIntro() {
        viewModelScope.launch { session.setIntroDone() }
    }

    fun enableBiometric() {
        viewModelScope.launch {
            session.setBiometricEnabled(true)
            _messages.send("Desbloqueo con huella activado")
        }
    }

    fun saveMeasurement() {
        val reading = (readingState.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            environment.saveToHistory(reading)
            _messages.send("Medición guardada en el historial")
        }
    }

    private fun load(showSkeleton: Boolean, announce: Boolean, successMessage: String = "") {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _isRefreshing.value = !showSkeleton
            if (showSkeleton) readingState.value = UiState.Loading
            try {
                readingState.value = UiState.Success(environment.fetchReading())
                if (announce) _messages.send(successMessage)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Caché: si hay una lectura previa la mostramos en vez de dejar la pantalla en blanco.
                val cached = (readingState.value as? UiState.Success)?.data ?: environment.cachedReading()
                if (cached != null) {
                    readingState.value = UiState.Success(cached, fromCache = true)
                    if (announce) _messages.send(e.toUserMessage())
                } else {
                    readingState.value = UiState.Error(e.toUserMessage())
                }
            } finally {
                // Un job cancelado por una recarga más nueva no debe apagar su indicador.
                if (isActive) _isRefreshing.value = false
            }
        }
    }

    companion object {
        const val AUTO_REFRESH_MILLIS = 30_000L

        val Factory = appViewModelFactory { c, _ ->
            HomeViewModel(c.environmentRepository, c.sessionRepository, c.connectivity, c.wifiLink, c.alertNotifier)
        }
    }
}
