package cl.osvaldouhart.climapulse.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json

/**
 * Fuente única de lecturas: todas vienen del nodo sensor ESP8266 por el enlace WiFi.
 * El historial se guarda en SQLite y la última lectura queda en caché para verla sin conexión.
 */
class EnvironmentRepository(
    private val dataStore: DataStore<Preferences>,
    private val database: AppDatabase,
    private val link: WifiLink
) {
    private val json = Json { ignoreUnknownKeys = true }

    // Se vuelve a consultar SQLite cada vez que cambia la tabla de lecturas.
    val history: Flow<List<EnvironmentReading>> = database.readingsVersion
        .map { database.readings(MAX_HISTORY) }
        .flowOn(Dispatchers.IO)

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

    suspend fun saveToHistory(reading: EnvironmentReading) = withContext(Dispatchers.IO) {
        database.insertReading(reading, MAX_HISTORY)
    }

    suspend fun clearHistory() = withContext(Dispatchers.IO) {
        database.deleteReadings()
    }

    suspend fun saveIdealRange(metric: Metric, range: IdealRange) {
        dataStore.edit { it[rangeKey(metric)] = json.encodeToString(range) }
    }

    companion object {
        const val MAX_HISTORY = 200

        private val LAST_READING = stringPreferencesKey("last_reading")
        private fun rangeKey(metric: Metric) = stringPreferencesKey("range_${metric.name.lowercase()}")
    }
}
