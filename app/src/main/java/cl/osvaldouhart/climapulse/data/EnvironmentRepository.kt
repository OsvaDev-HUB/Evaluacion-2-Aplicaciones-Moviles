package cl.osvaldouhart.climapulse.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json

/**
 * Fuente única de lecturas: todas vienen del nodo sensor ESP8266 por el enlace WiFi.
 * El historial se guarda en Firestore y la última lectura queda en caché para verla sin conexión.
 */
class EnvironmentRepository(
    private val dataStore: DataStore<Preferences>,
    private val readings: ReadingsStore,
    private val link: WifiLink
) {
    private val json = Json { ignoreUnknownKeys = true }

    val history: Flow<List<EnvironmentReading>> = readings.history

    /** Lecturas que llegan en tiempo real desde el sensor. */
    val liveReadings: Flow<EnvironmentReading> = link.readings

    val idealRanges: Flow<Map<Metric, IdealRange>> = dataStore.data.map { prefs ->
        Metric.entries.associateWith { metric ->
            prefs[rangeKey(metric)]?.let { runCatching { json.decodeFromString<IdealRange>(it) }.getOrNull() }
                ?: metric.defaultRange
        }
    }

    /** Devuelve la última lectura del sensor. Si no hay sensor conectado lanza LinkException. */
    suspend fun fetchReading(): EnvironmentReading {
        if (!link.state.value.connected) throw LinkException()
        val reading = link.lastReading
            ?: withTimeoutOrNull(5_000) { link.readings.first() }
            ?: throw LinkException()
        dataStore.edit { it[LAST_READING] = json.encodeToString(EnvironmentReading.serializer(), reading) }
        return reading
    }

    suspend fun cachedReading(): EnvironmentReading? =
        dataStore.data.first()[LAST_READING]?.let {
            runCatching { json.decodeFromString<EnvironmentReading>(it) }.getOrNull()
        }

    fun saveToHistory(reading: EnvironmentReading) = readings.add(reading)

    suspend fun clearHistory() = readings.clear()

    suspend fun saveIdealRange(metric: Metric, range: IdealRange) {
        dataStore.edit { it[rangeKey(metric)] = json.encodeToString(range) }
    }

    companion object {
        private val LAST_READING = stringPreferencesKey("last_reading")
        private fun rangeKey(metric: Metric) = stringPreferencesKey("range_${metric.name.lowercase()}")
    }
}
