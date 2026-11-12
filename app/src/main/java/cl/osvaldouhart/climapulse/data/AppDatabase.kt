package cl.osvaldouhart.climapulse.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Base de datos local SQLite con la tabla lecturas (temperatura, humedad y luz).
 * Las cuentas ya no se guardan aquí: las maneja Firebase Authentication.
 * Todas las consultas usan parámetros (?) para evitar inyección SQL.
 */
class AppDatabase(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    // Cambia cada vez que se agrega o borra una lectura, así las pantallas se actualizan solas.
    private val _readingsVersion = MutableStateFlow(0)
    val readingsVersion: StateFlow<Int> = _readingsVersion.asStateFlow()

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE lecturas (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                temperatura REAL NOT NULL,
                humedad REAL NOT NULL,
                luz REAL NOT NULL,
                fecha INTEGER NOT NULL,
                origen TEXT NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Versión 2: las cuentas pasan a Firebase, así que se borra la tabla usuarios
        // (tenía los hashes de las contraseñas). Las lecturas se conservan.
        if (oldVersion < 2) db.execSQL("DROP TABLE IF EXISTS usuarios")
    }

    // ---------- Lecturas ----------

    fun insertReading(reading: EnvironmentReading, maxRows: Int) {
        val values = ContentValues().apply {
            put("temperatura", reading.temperature)
            put("humedad", reading.humidity)
            put("luz", reading.light)
            put("fecha", reading.timestampMillis)
            put("origen", reading.origin)
        }
        val db = writableDatabase
        db.insert("lecturas", null, values)
        // Se conservan solo las lecturas más recientes.
        db.execSQL(
            "DELETE FROM lecturas WHERE id NOT IN (SELECT id FROM lecturas ORDER BY fecha DESC LIMIT ?)",
            arrayOf<Any>(maxRows)
        )
        _readingsVersion.value++
    }

    fun readings(limit: Int): List<EnvironmentReading> {
        val list = mutableListOf<EnvironmentReading>()
        val cursor = readableDatabase.rawQuery(
            "SELECT temperatura, humedad, luz, fecha, origen FROM lecturas ORDER BY fecha DESC LIMIT ?",
            arrayOf(limit.toString())
        )
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    EnvironmentReading(
                        temperature = it.getFloat(0),
                        humidity = it.getFloat(1),
                        light = it.getFloat(2),
                        timestampMillis = it.getLong(3),
                        origin = it.getString(4)
                    )
                )
            }
        }
        return list
    }

    fun deleteReadings() {
        writableDatabase.delete("lecturas", null, null)
        _readingsVersion.value++
    }

    private companion object {
        const val DB_NAME = "climapulse.db"
        const val DB_VERSION = 2
    }
}
