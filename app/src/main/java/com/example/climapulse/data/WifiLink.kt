package com.example.climapulse.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.IOException
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Papel de este teléfono en el enlace WiFi. */
enum class LinkRole { NONE, SENSOR, MONITOR }

data class LinkState(
    val role: LinkRole = LinkRole.NONE,
    val connected: Boolean = false,
    val connecting: Boolean = false,
    val pin: String = "",
    val address: String = "",
    val peerName: String = "",
    val monitors: Int = 0,
    val actuators: Actuators = Actuators(),
    val error: String? = null
)

/** Nodo sensor encontrado en la red WiFi con NSD (Network Service Discovery). */
data class FoundSensor(val name: String, val host: String, val port: Int)

/** Mensaje que viaja entre los dos teléfonos (JSON cifrado, uno por línea). */
@Serializable
data class LinkMessage(
    val tipo: String,
    val nombre: String? = null,
    val lectura: EnvironmentReading? = null,
    val actuadores: Actuators? = null
)

/**
 * Conexión WiFi entre dos teléfonos Android en la misma red:
 * - SENSOR: abre un servidor TCP, se anuncia con NSD y envía lecturas simuladas cada 3 segundos.
 * - MONITOR: busca sensores, se conecta con el PIN, recibe lecturas y controla los actuadores.
 */
class WifiLink(
    context: Context,
    private val database: AppDatabase,
    private val simulator: ReadingSimulator
) {
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }
    private val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}"

    private val _state = MutableStateFlow(LinkState())
    val state: StateFlow<LinkState> = _state.asStateFlow()

    private val _foundSensors = MutableStateFlow<List<FoundSensor>>(emptyList())
    val foundSensors: StateFlow<List<FoundSensor>> = _foundSensors.asStateFlow()

    private val _readings = MutableSharedFlow<EnvironmentReading>(extraBufferCapacity = 8)
    val readings: SharedFlow<EnvironmentReading> = _readings.asSharedFlow()

    /** Última lectura generada (sensor) o recibida (monitor). */
    @Volatile
    var lastReading: EnvironmentReading? = null
        private set

    private var crypto: LinkCrypto? = null
    private var job: Job? = null
    private var serverSocket: ServerSocket? = null
    private var monitorPeer: Peer? = null
    private val connectedMonitors = mutableListOf<Peer>()
    private var nsdRegistration: NsdManager.RegistrationListener? = null
    private var nsdDiscovery: NsdManager.DiscoveryListener? = null
    private var lastSavedMillis = 0L

    // ===================== Modo SENSOR =====================

    fun startSensor() {
        stop()
        val pin = (100_000 + SecureRandom().nextInt(900_000)).toString()
        crypto = LinkCrypto(pin)
        _state.value = LinkState(role = LinkRole.SENSOR, connecting = true, pin = pin)

        job = scope.launch {
            try {
                val server = ServerSocket()
                server.reuseAddress = true
                server.bind(InetSocketAddress(PORT))
                serverSocket = server
                registerService()
                _state.value = _state.value.copy(
                    connecting = false,
                    connected = true,
                    address = "${localIpAddress() ?: "sin IP"}:$PORT"
                )
                launch { sendReadingsLoop() }
                // Espera a que se conecten monitores (accept bloquea hasta que llega uno).
                while (isActive) {
                    val socket = server.accept()
                    launch { handleMonitor(socket) }
                }
            } catch (e: Exception) {
                if (isActive && _state.value.role == LinkRole.SENSOR) {
                    _state.value = _state.value.copy(
                        connected = false,
                        connecting = false,
                        error = "No se pudo abrir el puerto $PORT. Revisa que estés conectado a WiFi."
                    )
                }
            }
        }
    }

    private suspend fun sendReadingsLoop() {
        // delay() se cancela solo cuando se detiene el enlace.
        while (_state.value.role == LinkRole.SENSOR) {
            val actuators = _state.value.actuators
            if (!actuators.paused) {
                val reading = simulator.next(actuators)
                lastReading = reading
                _readings.tryEmit(reading)
                broadcast(LinkMessage(tipo = "lectura", lectura = reading.copy(origin = deviceName)))
            }
            delay(SEND_INTERVAL_MILLIS)
        }
    }

    private fun handleMonitor(socket: Socket) {
        val peer = Peer(socket)
        try {
            // El primer mensaje debe ser "hola". Si el PIN es distinto, no se puede descifrar y se corta.
            socket.soTimeout = 10_000
            val hello = peer.read() ?: return
            if (hello.tipo != "hola") return
            socket.soTimeout = 0

            synchronized(connectedMonitors) { connectedMonitors.add(peer) }
            updateMonitorCount()
            peer.send(LinkMessage(tipo = "bienvenida", nombre = deviceName, actuadores = _state.value.actuators))
            lastReading?.let { peer.send(LinkMessage(tipo = "lectura", lectura = it.copy(origin = deviceName))) }

            while (true) {
                val message = peer.read() ?: break
                if (message.tipo == "comando" && message.actuadores != null) {
                    _state.value = _state.value.copy(actuators = message.actuadores)
                    broadcast(LinkMessage(tipo = "estado", actuadores = message.actuadores))
                }
            }
        } catch (e: Exception) {
            // PIN incorrecto o el monitor se desconectó.
        } finally {
            synchronized(connectedMonitors) { connectedMonitors.remove(peer) }
            updateMonitorCount()
            peer.close()
        }
    }

    private fun broadcast(message: LinkMessage) {
        val peers = synchronized(connectedMonitors) { connectedMonitors.toList() }
        peers.forEach { peer -> runCatching { peer.send(message) } }
    }

    private fun updateMonitorCount() {
        val count = synchronized(connectedMonitors) { connectedMonitors.size }
        if (_state.value.role == LinkRole.SENSOR) _state.value = _state.value.copy(monitors = count)
    }

    /** El sensor también puede cambiar sus actuadores desde su propia pantalla. */
    fun setLocalActuators(actuators: Actuators) {
        if (_state.value.role != LinkRole.SENSOR) return
        _state.value = _state.value.copy(actuators = actuators)
        scope.launch { broadcast(LinkMessage(tipo = "estado", actuadores = actuators)) }
    }

    // ===================== Modo MONITOR =====================

    fun connect(host: String, port: Int, pin: String) {
        stop()
        crypto = LinkCrypto(pin)
        _state.value = LinkState(role = LinkRole.MONITOR, connecting = true, address = "$host:$port")

        job = scope.launch {
            var peer: Peer? = null
            try {
                val socket = Socket()
                socket.connect(InetSocketAddress(host, port), 5_000)
                peer = Peer(socket)
                monitorPeer = peer

                peer.send(LinkMessage(tipo = "hola", nombre = deviceName))
                socket.soTimeout = 5_000
                val welcome = peer.read() ?: throw IOException("PIN incorrecto")
                socket.soTimeout = 0
                _state.value = _state.value.copy(
                    connecting = false,
                    connected = true,
                    peerName = welcome.nombre.orEmpty(),
                    actuators = welcome.actuadores ?: Actuators()
                )

                while (isActive) {
                    val message = peer.read() ?: break
                    when (message.tipo) {
                        "lectura" -> message.lectura?.let { onRemoteReading(it) }
                        "estado" -> message.actuadores?.let { _state.value = _state.value.copy(actuators = it) }
                    }
                }
                throw IOException("conexión cerrada")
            } catch (e: Exception) {
                if (isActive && _state.value.role == LinkRole.MONITOR) {
                    val wasConnected = _state.value.connected
                    _state.value = _state.value.copy(
                        connected = false,
                        connecting = false,
                        error = if (wasConnected) "Se perdió la conexión con el nodo sensor."
                        else "No se pudo conectar. Revisa la IP, el PIN y que ambos estén en la misma red WiFi."
                    )
                }
            } finally {
                peer?.close()
                if (monitorPeer === peer) monitorPeer = null
            }
        }
    }

    private fun onRemoteReading(reading: EnvironmentReading) {
        lastReading = reading
        _readings.tryEmit(reading)
        // El monitor guarda en SQLite una lectura cada 15 segundos para no llenar la base.
        if (reading.timestampMillis - lastSavedMillis >= AUTO_SAVE_MILLIS) {
            lastSavedMillis = reading.timestampMillis
            database.insertReading(reading, EnvironmentRepository.MAX_HISTORY)
        }
    }

    fun sendCommand(actuators: Actuators) {
        if (_state.value.role != LinkRole.MONITOR) return
        _state.value = _state.value.copy(actuators = actuators)
        scope.launch { runCatching { monitorPeer?.send(LinkMessage(tipo = "comando", actuadores = actuators)) } }
    }

    // ===================== Búsqueda con NSD =====================

    fun startDiscovery() {
        stopDiscovery()
        _foundSensors.value = emptyList()
        val listener = object : NsdManager.DiscoveryListener {
            override fun onServiceFound(service: NsdServiceInfo) = resolve(service)
            override fun onServiceLost(service: NsdServiceInfo) {
                _foundSensors.value = _foundSensors.value.filter { it.name != service.serviceName }
            }
            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
        }
        nsdDiscovery = listener
        runCatching { nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener) }
    }

    fun stopDiscovery() {
        nsdDiscovery?.let { runCatching { nsdManager.stopServiceDiscovery(it) } }
        nsdDiscovery = null
    }

    @Suppress("DEPRECATION")
    private fun resolve(service: NsdServiceInfo) {
        nsdManager.resolveService(service, object : NsdManager.ResolveListener {
            override fun onServiceResolved(info: NsdServiceInfo) {
                val host = info.host?.hostAddress ?: return
                val found = FoundSensor(info.serviceName, host, info.port)
                _foundSensors.value = (_foundSensors.value.filter { it.name != found.name } + found)
            }

            override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                // Si hay otra resolución en curso, se reintenta un poco después.
                if (errorCode == NsdManager.FAILURE_ALREADY_ACTIVE) {
                    scope.launch {
                        delay(300)
                        resolve(service)
                    }
                }
            }
        })
    }

    private fun registerService() {
        val info = NsdServiceInfo().apply {
            serviceName = "ClimaPulse ${Build.MODEL}"
            serviceType = SERVICE_TYPE
            port = PORT
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) = Unit
            override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) = Unit
            override fun onServiceUnregistered(info: NsdServiceInfo) = Unit
            override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) = Unit
        }
        nsdRegistration = listener
        runCatching { nsdManager.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener) }
    }

    // ===================== Común =====================

    /** Detiene el enlace, sea cual sea el modo. */
    fun stop() {
        _state.value = LinkState()
        job?.cancel()
        job = null
        runCatching { serverSocket?.close() }
        serverSocket = null
        monitorPeer?.close()
        synchronized(connectedMonitors) {
            connectedMonitors.forEach { it.close() }
            connectedMonitors.clear()
        }
        nsdRegistration?.let { runCatching { nsdManager.unregisterService(it) } }
        nsdRegistration = null
        lastReading = null
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    private fun localIpAddress(): String? {
        val properties = connectivityManager.getLinkProperties(connectivityManager.activeNetwork) ?: return null
        return properties.linkAddresses
            .map { it.address }
            .firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
            ?.hostAddress
    }

    /** Un socket con lectura y escritura de mensajes cifrados. */
    private inner class Peer(private val socket: Socket) {
        private val reader: BufferedReader = socket.getInputStream().bufferedReader()
        private val writer: BufferedWriter = socket.getOutputStream().bufferedWriter()
        private val cipher = crypto ?: throw IllegalStateException("sin PIN")

        fun send(message: LinkMessage) {
            val line = cipher.encrypt(json.encodeToString(LinkMessage.serializer(), message))
            synchronized(writer) {
                writer.write(line)
                writer.newLine()
                writer.flush()
            }
        }

        /** Devuelve null si el otro teléfono cerró la conexión. */
        fun read(): LinkMessage? {
            val line = reader.readLine() ?: return null
            return json.decodeFromString(LinkMessage.serializer(), cipher.decrypt(line))
        }

        fun close() {
            runCatching { socket.close() }
        }
    }

    companion object {
        const val PORT = 5050
        const val SERVICE_TYPE = "_climapulse._tcp."
        const val SEND_INTERVAL_MILLIS = 3_000L
        const val AUTO_SAVE_MILLIS = 15_000L
    }
}
