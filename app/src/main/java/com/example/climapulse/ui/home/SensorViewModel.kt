package com.example.climapulse.ui.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.climapulse.data.Actuators
import com.example.climapulse.data.FoundSensor
import com.example.climapulse.data.LinkRole
import com.example.climapulse.data.LinkState
import com.example.climapulse.data.WifiLink
import com.example.climapulse.ui.appViewModelFactory
import kotlinx.coroutines.flow.StateFlow

/** Maneja la pantalla del enlace WiFi entre el nodo sensor y el monitor. */
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

    fun startSensor() {
        choosingMonitor = false
        link.startSensor()
    }

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
            pin.length != 6 -> "El PIN tiene 6 dígitos y aparece en el teléfono sensor."
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

    /** Cambia un actuador: en el sensor se aplica directo, en el monitor se envía como comando. */
    fun updateActuators(actuators: Actuators) {
        when (state.value.role) {
            LinkRole.SENSOR -> link.setLocalActuators(actuators)
            LinkRole.MONITOR -> link.sendCommand(actuators)
            LinkRole.NONE -> Unit
        }
    }

    override fun onCleared() {
        link.stopDiscovery()
    }

    companion object {
        val Factory = appViewModelFactory { c, _ -> SensorViewModel(c.wifiLink) }
    }
}
