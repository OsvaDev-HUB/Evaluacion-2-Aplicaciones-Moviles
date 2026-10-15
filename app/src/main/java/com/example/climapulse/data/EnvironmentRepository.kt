package com.example.climapulse.data

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json

// Demo controlada mientras la app no esté conectada a un sensor externo.
private const val USE_DEMO_DATA = true

/**
 * Fuente única de lecturas. Según el enlace WiFi las lecturas vienen de:
 * - otro teléfono (modo monitor),
 * - el simulador de este teléfono (modo sensor o sin enlace).
 * El historial se guarda en SQLite y la última lectura queda en caché para verla sin conexión.
 */
class EnvironmentRepository(
    context: Context,
    private val dataStore: DataStore<Preferences>,
    private val connectivity: ConnectivityObserver,
    private val database: AppDatabase,
    private val link: WifiLink,
    private val simulator: ReadingSimulator
) {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val json = Json { ignoreUnknownKeys = true }

    // Se vuelve a consultar SQLite cada vez que cambia la tabla de lecturas.
    val history: Flow<List<EnvironmentReading>> = database.readingsVersion
        .map { database.readings(MAX_HISTORY) }
        .flowOn(Dispatchers.IO)

    /** Lecturas que llegan en tiempo real por el enlace WiFi. */
    val liveReadings: Flow<EnvironmentReading> = link.readings

    val idealRanges: Flow<Map<Metric, IdealRange>> = dataStore.data.map { prefs ->
        Metric.entries.associateWith { metric ->
            prefs[rangeKey(metric)]?.let { runCatching { json.decodeFromString<IdealRange>(it) }.getOrNull() }
                ?: metric.defaultRange
        }
    }

    val hasHardwareSensors: Boolean
        get() = !USE_DEMO_DATA && hardwareSensors().isNotEmpty()

    suspend fun fetchReading(): EnvironmentReading {
        val linkState = link.state.value
        val reading = when (linkState.role) {
            // Monitor: se usa la última lectura recibida del nodo sensor.
            LinkRole.MONITOR -> {
                if (!linkState.connected) throw LinkException()
                link.lastReading ?: withTimeoutOrNull(5_000) { link.readings.first() } ?: throw LinkException()
            }
            // Sensor: la lectura que este teléfono está enviando.
            LinkRole.SENSOR -> link.lastReading ?: simulator.next(linkState.actuators)
            // Sin enlace: demo local como en la versión original.
            LinkRole.NONE -> {
                delay(700)
                if (!connectivity.isOnline()) throw OfflineException()
                readHardware() ?: simulator.next()
            }
        }
        dataStore.edit { it[LAST_READING] = json.encodeToString(EnvironmentReading.serializer(), reading) }
        return reading
    }

    suspend fun cachedReading(): EnvironmentReading? =
        dataStore.data.first()[LAST_READING]?.let {
            runCatching { json.decodeFromString<EnvironmentReading>(it) }.getOrNull()
        }

    suspend fun saveToHistory(reading: EnvironmentReading) = withContext(Dispatchers.IO) {
        database.insertReading(reading, MAX_HISTORY)
    }

    suspend fun clearHistory() = withContext(Dispatchers.IO) {
        database.deleteReadings()
    }

    suspend fun saveIdealRange(metric: Metric, range: IdealRange) {
        dataStore.edit { it[rangeKey(metric)] = json.encodeToString(range) }
    }

    private fun hardwareSensors(): List<Sensor> = listOfNotNull(
        sensorManager.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE),
        sensorManager.getDefaultSensor(Sensor.TYPE_RELATIVE_HUMIDITY),
        sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
    )

    private suspend fun readHardware(): EnvironmentReading? {
        if (!hasHardwareSensors) return null
        val fallback = cachedReading() ?: simulator.next()
        val values = hardwareSensors().associate { sensor ->
            sensor.type to withTimeoutOrNull(1_500) { readOnce(sensor) }
        }
        return EnvironmentReading(
            temperature = values[Sensor.TYPE_AMBIENT_TEMPERATURE] ?: fallback.temperature,
            humidity = values[Sensor.TYPE_RELATIVE_HUMIDITY]?.coerceIn(0f, 100f) ?: fallback.humidity,
            light = values[Sensor.TYPE_LIGHT]?.coerceAtLeast(0f) ?: fallback.light,
            timestampMillis = System.currentTimeMillis(),
            usingHardware = true
        )
    }

    private suspend fun readOnce(sensor: Sensor): Float? = suspendCancellableCoroutine { continuation ->
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                sensorManager.unregisterListener(this)
                if (continuation.isActive) continuation.resume(event.values.firstOrNull())
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        continuation.invokeOnCancellation { sensorManager.unregisterListener(listener) }
    }

    companion object {
        const val MAX_HISTORY = 200

        private val LAST_READING = stringPreferencesKey("last_reading")
        private fun rangeKey(metric: Metric) = stringPreferencesKey("range_${metric.name.lowercase()}")
    }
}
