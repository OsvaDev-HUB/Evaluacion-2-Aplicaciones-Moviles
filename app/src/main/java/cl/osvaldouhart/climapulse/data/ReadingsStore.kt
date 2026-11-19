package cl.osvaldouhart.climapulse.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await

// Historial de lecturas en Firestore, en users/{uid}/lecturas. Cada usuario ve solo las suyas
// (lo controlan las reglas). Firestore guarda una copia en el teléfono, así que el historial
// se puede ver y seguir llenando sin internet; se sube solo cuando vuelve la conexión.
@OptIn(ExperimentalCoroutinesApi::class)
class ReadingsStore(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) {
    // Fecha de la lectura más antigua que se muestra; lo anterior se puede borrar.
    @Volatile
    private var oldestShownMillis: Long? = null
    private var savesSinceCleanup = 0

    private val uid: Flow<String?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.uid) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    // Las lecturas más recientes primero. Se actualiza solo al agregar o borrar.
    val history: Flow<List<EnvironmentReading>> = uid.flatMapLatest { uid ->
        if (uid == null) flowOf(emptyList()) else recentReadings(uid)
    }

    // Sin await: Firestore la guarda primero en el teléfono y la sube cuando hay conexión.
    fun add(reading: EnvironmentReading) {
        val uid = auth.currentUser?.uid ?: return
        lecturas(uid).add(
            mapOf(
                "temperatura" to reading.temperature,
                "humedad" to reading.humidity,
                "luz" to reading.light,
                "fecha" to reading.timestampMillis,
                "origen" to reading.origin.take(MAX_ORIGIN)
            )
        )
        // Cada cierto número de lecturas se borran las que ya no entran en el historial.
        if (++savesSinceCleanup >= CLEANUP_EVERY) {
            savesSinceCleanup = 0
            deleteOlderThanShown(uid)
        }
    }

    // Borra el historial del usuario con la sesión abierta. No espera al servidor para que
    // funcione sin internet: el borrado queda en cola y se sube al volver la conexión.
    // Gracias a la limpieza nunca hay muchas más lecturas que MAX_HISTORY, así que basta un lote.
    suspend fun clear() {
        val uid = auth.currentUser?.uid ?: return
        val all = lecturas(uid).limit(MAX_BATCH_WRITES).get().await()
        if (all.isEmpty) return
        val batch = firestore.batch()
        all.documents.forEach { batch.delete(it.reference) }
        batch.commit()
    }

    // Borra el historial de una cuenta (se usa antes de eliminarla). Firestore no borra
    // las subcolecciones al borrar el documento del usuario, por eso se hace a mano.
    suspend fun deleteAll(uid: String) {
        while (true) {
            val page = lecturas(uid).limit(BATCH_SIZE.toLong()).get().await()
            if (page.isEmpty) return
            val batch = firestore.batch()
            page.documents.forEach { batch.delete(it.reference) }
            batch.commit().await()
        }
    }

    private fun recentReadings(uid: String): Flow<List<EnvironmentReading>> = callbackFlow {
        val registration = lecturas(uid)
            .orderBy("fecha", Query.Direction.DESCENDING)
            .limit(MAX_HISTORY.toLong())
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val list = snapshot.documents.mapNotNull { it.toReading() }
                oldestShownMillis = if (list.size >= MAX_HISTORY) list.last().timestampMillis else null
                trySend(list)
            }
        awaitClose { registration.remove() }
    }

    private fun deleteOlderThanShown(uid: String) {
        val cutoff = oldestShownMillis ?: return
        lecturas(uid).whereLessThan("fecha", cutoff).limit(BATCH_SIZE.toLong()).get()
            .addOnSuccessListener { old ->
                if (old.isEmpty) return@addOnSuccessListener
                val batch = firestore.batch()
                old.documents.forEach { batch.delete(it.reference) }
                batch.commit()
            }
    }

    private fun lecturas(uid: String): CollectionReference =
        firestore.collection("users").document(uid).collection("lecturas")

    private fun DocumentSnapshot.toReading(): EnvironmentReading? {
        return EnvironmentReading(
            temperature = getDouble("temperatura")?.toFloat() ?: return null,
            humidity = getDouble("humedad")?.toFloat() ?: return null,
            light = getDouble("luz")?.toFloat() ?: return null,
            timestampMillis = getLong("fecha") ?: return null,
            origin = getString("origen") ?: "Nodo sensor"
        )
    }

    companion object {
        const val MAX_HISTORY = 200
        private const val MAX_ORIGIN = 40
        private const val BATCH_SIZE = 200
        // Firestore acepta como máximo 500 operaciones por lote.
        private const val MAX_BATCH_WRITES = 500L
        // Con una lectura cada 15 segundos, la limpieza corre más o menos cada 10 minutos.
        private const val CLEANUP_EVERY = 40
    }
}
