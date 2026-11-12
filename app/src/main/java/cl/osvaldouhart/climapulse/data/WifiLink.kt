package cl.osvaldouhart.climapulse.data

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import kotlin.coroutines.cancellation.CancellationException
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
enum class LinkRole { NONE, MONITOR }

data class LinkState(
    val role: LinkRole = LinkRole.NONE,
    val connected: Boolean = false,
    val connecting: Boolean = false,
    val address: String = "",
    val peerName: String = "",
    val actuators: Actuators = Actuators(),
    val error: String? = null
)

/** Nodo sensor (ESP8266) encontrado en la red WiFi con NSD (Network Service Discovery). */
data class FoundSensor(val name: String, val host: String, val port: Int)

/** Mensaje que viaja entre la app y el nodo sensor (JSON cifrado, uno por línea). */
@Serializable
data class LinkMessage(
    val tipo: String,
    val nombre: String? = null,
    val lectura: EnvironmentReading? = null,
    val actuadores: Actuators? = null
)

/**
 * Conexión WiFi con el nodo sensor ESP8266 (sketch "sensores/sensores.ino"):
 * busca el sensor en la red, se conecta con el PIN, recibe lecturas reales
 * del DHT11 y del LDR cada 3 segundos y controla los actuadores.
 */
class WifiLink(
    context: Context,
    private val database: AppDatabase
) {
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }
    private val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}"

    private val _state = MutableStateFlow(LinkState())
    val state: StateFlow<LinkState> = _state.asStateFlow()

    private val _foundSensors = MutableStateFlow<List<FoundSensor>>(emptyList())
    val foundSensors: StateFlow<List<FoundSensor>> = _foundSensors.asStateFlow()

    private val _readings = MutableSharedFlow<EnvironmentReading>(extraBufferCapacity = 8)
    val readings: SharedFlow<EnvironmentReading> = _readings.asSharedFlow()

    /** Última lectura recibida del nodo sensor. */
    @Volatile
    var lastReading: EnvironmentReading? = null
        private set

    private var crypto: LinkCrypto? = null
    private var job: Job? = null
    private var sensorPeer: Peer? = null
    private var nsdDiscovery: NsdManager.DiscoveryListener? = null
    private var lastSavedMillis = 0L

    // ===================== Conexión con el sensor =====================

    fun connect(host: String, port: Int, pin: String) {
        stop()
        crypto = LinkCrypto(pin)
        _state.value = LinkState(role = LinkRole.MONITOR, connecting = true, address = "$host:$port")

        job = scope.launch {
            var linked = false  // true después de la primera conexión exitosa
            while (isActive) {
                try {
                    openSession(host, port) { linked = true }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // La conexión falló o se cortó; se decide abajo qué hacer.
                }
                if (!isActive || _state.value.role != LinkRole.MONITOR) break

                if (!linked) {
                    // Nunca se pudo conectar: probablemente la IP o el PIN están mal.
                    _state.value = _state.value.copy(
                        connected = false,
                        connecting = false,
                        error = "No se pudo conectar. Revisa la IP, el PIN y que el sensor esté en la misma red WiFi."
                    )
                    break
                }

                // Ya estaba conectado (por ejemplo, el ESP8266 se reinició): se reintenta solo.
                _state.value = _state.value.copy(connected = false, connecting = true, error = null)
                delay(RECONNECT_MILLIS)
            }
        }
    }

    /** Abre una conexión con el sensor y recibe mensajes hasta que se corte. */
    private fun openSession(host: String, port: Int, onConnected: () -> Unit) {
        val socket = Socket()
        var peer: Peer? = null
        try {
            socket.connect(InetSocketAddress(host, port), 5_000)
            peer = Peer(socket)
            sensorPeer = peer

            peer.send(LinkMessage(tipo = "hola", nombre = deviceName))
            socket.soTimeout = 5_000
            val welcome = peer.read() ?: throw IOException("PIN incorrecto")
            // Si pasan 10 segundos sin mensajes, la conexión se da por perdida.
            socket.soTimeout = SILENCE_TIMEOUT_MILLIS
            onConnected()
            _state.value = _state.value.copy(
                connecting = false,
                connected = true,
                error = null,
                peerName = welcome.nombre.orEmpty(),
                actuators = welcome.actuadores ?: Actuators()
            )

            while (true) {
                val message = try {
                    peer.read()
                } catch (e: SocketTimeoutException) {
                    // Con el envío en pausa es normal que no lleguen lecturas.
                    if (_state.value.actuators.paused) continue else throw e
                } ?: break
                when (message.tipo) {
                    "lectura" -> message.lectura?.let { onSensorReading(it) }
                    "estado" -> message.actuadores?.let { _state.value = _state.value.copy(actuators = it) }
                }
            }
        } finally {
            runCatching { socket.close() }
            if (sensorPeer === peer) sensorPeer = null
        }
    }

    private fun onSensorReading(received: EnvironmentReading) {
        // El ESP8266 no tiene reloj, así que la hora la pone el teléfono al recibirla.
        val reading = received.copy(timestampMillis = System.currentTimeMillis())
        lastReading = reading
        _readings.tryEmit(reading)
        // Se guarda en SQLite una lectura cada 15 segundos para no llenar la base.
        if (reading.timestampMillis - lastSavedMillis >= AUTO_SAVE_MILLIS) {
            lastSavedMillis = reading.timestampMillis
            database.insertReading(reading, EnvironmentRepository.MAX_HISTORY)
        }
    }

    fun sendCommand(actuators: Actuators) {
        if (_state.value.role != LinkRole.MONITOR) return
        _state.value = _state.value.copy(actuators = actuators)
        scope.launch { runCatching { sensorPeer?.send(LinkMessage(tipo = "comando", actuadores = actuators)) } }
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

    // ===================== Común =====================

    /** Cierra la conexión con el sensor. */
    fun stop() {
        _state.value = LinkState()
        job?.cancel()
        job = null
        sensorPeer?.close()
        lastReading = null
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
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

        /** Devuelve null si el sensor cerró la conexión. */
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
        const val AUTO_SAVE_MILLIS = 15_000L
        const val RECONNECT_MILLIS = 3_000L
        const val SILENCE_TIMEOUT_MILLIS = 10_000
    }
}
