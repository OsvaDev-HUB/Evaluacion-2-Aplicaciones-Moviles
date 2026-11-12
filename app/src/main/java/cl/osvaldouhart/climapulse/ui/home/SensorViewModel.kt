package cl.osvaldouhart.climapulse.ui.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import cl.osvaldouhart.climapulse.data.Actuators
import cl.osvaldouhart.climapulse.data.FoundSensor
import cl.osvaldouhart.climapulse.data.LinkState
import cl.osvaldouhart.climapulse.data.WifiLink
import cl.osvaldouhart.climapulse.ui.appViewModelFactory
import kotlinx.coroutines.flow.StateFlow

/** Maneja la pantalla del enlace WiFi con el nodo sensor ESP8266. */
class SensorViewModel(private val link: WifiLink) : ViewModel() {
    val state: StateFlow<LinkState> = link.state
    val foundSensors: StateFlow<List<FoundSensor>> = link.foundSensors

    // true mientras el usuario está eligiendo a qué sensor conectarse.
    var choosingMonitor by mutableStateOf(false)
        private set

    var host by mutableStateOf("")
    var pin by mutableStateOf("")
    var formError by mutableStateOf<String?>(null)
        private set

    fun startMonitor() {
        choosingMonitor = true
        formError = null
        link.startDiscovery()
    }

    fun selectSensor(sensor: FoundSensor) {
        host = sensor.host
        formError = null
    }

    fun connect() {
        val cleanHost = host.trim()
        formError = when {
            cleanHost.isEmpty() -> "Ingresa la IP del nodo sensor o elige uno de la lista."
            pin.length != 6 -> "El PIN tiene 6 dígitos y está en config.h del sensor."
            else -> null
        }
        if (formError != null) return
        link.stopDiscovery()
        link.connect(cleanHost, WifiLink.PORT, pin)
    }

    fun retrySearch() {
        link.clearError()
        startMonitor()
    }

    fun stop() {
        choosingMonitor = false
        link.stopDiscovery()
        link.stop()
    }

    fun clearError() = link.clearError()

    /** Envía al sensor el nuevo estado de los actuadores. */
    fun updateActuators(actuators: Actuators) = link.sendCommand(actuators)

    override fun onCleared() {
        link.stopDiscovery()
    }

    companion object {
        val Factory = appViewModelFactory { c, _ -> SensorViewModel(c.wifiLink) }
    }
}
