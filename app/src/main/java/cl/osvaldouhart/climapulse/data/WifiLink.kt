package cl.osvaldouhart.climapulse.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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
    val error: String? = null,
    // Último aviso que mandó el nodo por su cuenta (por ejemplo, falla del DHT11).
    val notice: SensorNotice? = null,
    // Mensajes descartados: repetidos, de otra sesión o con valores imposibles.
    val rejected: Int = 0,
    // Lecturas que el nodo guardó sin conexión y que ya se pasaron al historial.
    val recovered: Int = 0
)

/** Aviso del nodo sensor. "problem" es true mientras el problema siga activo. */
data class SensorNotice(val code: String, val message: String) {
    val problem: Boolean get() = code != "sensor_ok"
}

/** Nodo sensor (ESP8266) encontrado en la red WiFi con NSD (Network Service Discovery). */
data class FoundSensor(val name: String, val host: String, val port: Int)

/**
 * Conexión WiFi con el nodo sensor ESP8266 (sketch "sensores/sensores.ino"):
 * busca el sensor en la red, se conecta con el PIN, recibe lecturas reales
 * del DHT11 y del LDR cada 3 segundos y controla los actuadores.
 * Las reglas del protocolo (reto, sesión y contador anti-repetición) están en LinkSession.
 */
class WifiLink(
    context: Context,
    private val readingsStore: ReadingsStore
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

    // Mensajes cortos para mostrar al usuario (avisos del nodo, lecturas recuperadas).
    private val _notices = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val notices: SharedFlow<String> = _notices.asSharedFlow()

    // Se avisa aquí cuando vuelve la red del teléfono, para reintentar sin esperar.
    private val retryNow = Channel<Unit>(Channel.CONFLATED)

    /** Última lectura recibida del nodo sensor. */
    @Volatile
    var lastReading: EnvironmentReading? = null
        private set

    private var crypto: LinkCrypto? = null
    private var job: Job? = null
    private var sensorPeer: Peer? = null
    private var session: LinkSession? = null
    private var recoveredInBatch = 0
    private var nsdDiscovery: NsdManager.DiscoveryListener? = null
    private var lastSavedMillis = 0L

    // ===================== Conexión con el sensor =====================

    init {
        // Cuando el teléfono recupera la red, se reintenta la conexión de inmediato.
        val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        runCatching {
            connectivity.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    retryNow.trySend(Unit)
                }
            })
        }
    }

    fun connect(host: String, port: Int, pin: String) {
        stop()
        crypto = LinkCrypto(pin)
        _state.value = LinkState(role = LinkRole.MONITOR, connecting = true, address = "$host:$port")

        job = scope.launch {
            var linked = false  // true después de la primera conexión exitosa
            var attempt = 0
            while (isActive) {
                try {
                    openSession(host, port) {
                        linked = true
                        attempt = 0
                    }
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
                // La espera crece (2, 4, 8, 16 y 30 s) para no gastar batería si el nodo tarda en volver,
                // pero si vuelve la red del teléfono se reintenta al instante.
                _state.value = _state.value.copy(connected = false, connecting = true, error = null)
                val wait = backoffMillis(attempt++)
                withTimeoutOrNull(wait) { retryNow.receive() }
            }
        }
    }
    /** Abre una conexión con el sensor y recibe mensajes hasta que se corte. */
    private fun openSession(host: String, port: Int, onConnected: () -> Unit) {
        val socket = Socket()
        var peer: Peer? = null
        val linkSession = LinkSession()
        try {
            socket.connect(InetSocketAddress(host, port), 5_000)
            peer = Peer(socket, linkSession)

            peer.sendRaw(linkSession.hello(deviceName))
            socket.soTimeout = 5_000
            val welcome = peer.read() ?: throw IOException("PIN incorrecto")
            // La bienvenida debe traer nuestro reto; si no, puede ser una respuesta grabada.
            if (!linkSession.acceptWelcome(welcome)) throw IOException("Bienvenida inválida")
            sensorPeer = peer
            session = linkSession
            recoveredInBatch = 0
            // El nodo manda un latido cada 8 s; si pasan 20 s sin nada, la conexión se da por perdida.
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
                val message = peer.read() ?: break
                if (!linkSession.accept(message)) {
                    // Repetido o de otra sesión: se ignora y se cuenta.
                    _state.value = _state.value.copy(rejected = _state.value.rejected + 1)
                    continue
                }
                when (message.tipo) {
                    "lectura" -> message.lectura?.let { onSensorReading(it) }
                    "estado" -> message.actuadores?.let { _state.value = _state.value.copy(actuators = it) }
                    "historial" -> onStoredReadings(peer, message)
                    "aviso" -> onNotice(message)
                    // "pulso" solo sirve para saber que el nodo sigue vivo.
                }
            }
        } finally {
            runCatching { socket.close() }
            if (sensorPeer === peer) {
                sensorPeer = null
                session = null
            }
        }
    }

    private fun onSensorReading(received: EnvironmentReading) {
        // Integridad: una lectura con valores imposibles viene dañada y no se muestra ni se guarda.
        if (!ReadingRules.isPlausible(received)) {
            _state.value = _state.value.copy(rejected = _state.value.rejected + 1)
            return
        }
        // El ESP8266 no tiene reloj, así que la hora la pone el teléfono al recibirla.
        val reading = received.copy(timestampMillis = System.currentTimeMillis())
        lastReading = reading
        _readings.tryEmit(reading)
        // Se guarda en el historial una lectura cada 15 segundos para no llenar la base.
        if (reading.timestampMillis - lastSavedMillis >= AUTO_SAVE_MILLIS) {
            lastSavedMillis = reading.timestampMillis
            readingsStore.add(reading)
        }
    }

    /** Lecturas que el nodo guardó sin conexión: se pasan al historial y se confirma para que las borre. */
    private fun onStoredReadings(peer: Peer, message: LinkMessage) {
        val now = System.currentTimeMillis()
        val origin = _state.value.peerName.ifEmpty { "Nodo sensor" }
        val items = message.lecturas.orEmpty()
        var saved = 0
        items.forEach { item ->
            if (ReadingRules.isPlausible(item)) {
                readingsStore.add(
                    EnvironmentReading(item.temperature, item.humidity, item.light, now - item.edad, usingHardware = true, origin = origin)
                )
                saved++
            }
        }
        recoveredInBatch += saved
        _state.value = _state.value.copy(
            recovered = _state.value.recovered + saved,
            rejected = _state.value.rejected + (items.size - saved)
        )
        // Se confirma el lote completo (también las dañadas) para que el nodo libere su memoria.
        peer.send(LinkMessage(tipo = "recibido", cantidad = items.size))
        if ((message.quedan ?: 0) == 0 && recoveredInBatch > 0) {
            _notices.tryEmit("Se recuperaron $recoveredInBatch lecturas que el sensor guardó sin conexión")
            recoveredInBatch = 0
        }
    }

    private fun onNotice(message: LinkMessage) {
        val code = message.codigo ?: return
        val text = message.mensaje?.take(200) ?: return
        _state.value = _state.value.copy(notice = SensorNotice(code, text))
        _notices.tryEmit(text)
    }

    fun sendCommand(actuators: Actuators) {
        if (_state.value.role != LinkRole.MONITOR) return
        _state.value = _state.value.copy(actuators = actuators)
        scope.launch { runCatching { sensorPeer?.send(LinkMessage(tipo = "comando", actuadores = actuators)) } }
    }

    /** Espera antes del reintento número [attempt]: 2, 4, 8, 16 y luego 30 segundos. */
    private fun backoffMillis(attempt: Int): Long =
        (RECONNECT_MILLIS shl attempt.coerceAtMost(4)).coerceAtMost(MAX_RECONNECT_MILLIS)

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
    private inner class Peer(private val socket: Socket, private val linkSession: LinkSession) {
        private val reader: BufferedReader = socket.getInputStream().bufferedReader()
        private val writer: BufferedWriter = socket.getOutputStream().bufferedWriter()
        private val cipher = crypto ?: throw IllegalStateException("sin PIN")

        /** Envía un mensaje de la sesión: se le agrega la sesión y el contador. */
        fun send(message: LinkMessage) = synchronized(writer) { write(linkSession.stamp(message)) }

        /** Solo para el "hola", que va antes de que exista la sesión. */
        fun sendRaw(message: LinkMessage) = synchronized(writer) { write(message) }

        private fun write(message: LinkMessage) {
            writer.write(cipher.encrypt(json.encodeToString(LinkMessage.serializer(), message)))
            writer.newLine()
            writer.flush()
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
        const val RECONNECT_MILLIS = 2_000L
        const val MAX_RECONNECT_MILLIS = 30_000L
        const val SILENCE_TIMEOUT_MILLIS = 20_000
    }
}
