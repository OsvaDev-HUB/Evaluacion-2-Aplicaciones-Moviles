package com.example.climapulse.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Fila de la tabla usuarios. La contraseña nunca se guarda, solo su hash y su sal. */
data class UserRow(
    val name: String,
    val email: String,
    val phone: String,
    val passwordHash: String,
    val salt: String
)

/**
 * Base de datos local SQLite con dos tablas:
 * - usuarios: cuentas para el login (autenticación sin conexión).
 * - lecturas: mediciones de temperatura, humedad y luz.
 * Todas las consultas usan parámetros (?) para evitar inyección SQL.
 */
class AppDatabase(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    // Cambia cada vez que se agrega o borra una lectura, así las pantallas se actualizan solas.
    private val _readingsVersion = MutableStateFlow(0)
    val readingsVersion: StateFlow<Int> = _readingsVersion.asStateFlow()

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE usuarios (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                nombre TEXT NOT NULL,
                correo TEXT NOT NULL UNIQUE,
                telefono TEXT,
                hash TEXT NOT NULL,
                sal TEXT NOT NULL
            )
            """.trimIndent()
        )
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
        db.execSQL("DROP TABLE IF EXISTS usuarios")
        db.execSQL("DROP TABLE IF EXISTS lecturas")
        onCreate(db)
    }

    // ---------- Usuarios ----------

    fun insertUser(user: UserRow): Boolean {
        val values = ContentValues().apply {
            put("nombre", user.name)
            put("correo", user.email)
            put("telefono", user.phone)
            put("hash", user.passwordHash)
            put("sal", user.salt)
        }
        // insert devuelve -1 si el correo ya existe (columna UNIQUE).
        return writableDatabase.insert("usuarios", null, values) != -1L
    }

    fun findUser(email: String): UserRow? {
        val cursor = readableDatabase.rawQuery(
            "SELECT nombre, correo, telefono, hash, sal FROM usuarios WHERE correo = ?",
            arrayOf(email)
        )
        cursor.use {
            if (!it.moveToFirst()) return null
            return UserRow(
                name = it.getString(0),
                email = it.getString(1),
                phone = it.getString(2).orEmpty(),
                passwordHash = it.getString(3),
                salt = it.getString(4)
            )
        }
    }

    fun updateUser(oldEmail: String, name: String, email: String, phone: String): Boolean {
        val values = ContentValues().apply {
            put("nombre", name)
            put("correo", email)
            put("telefono", phone)
        }
        return runCatching {
            writableDatabase.update("usuarios", values, "correo = ?", arrayOf(oldEmail)) > 0
        }.getOrDefault(false)
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
        const val DB_VERSION = 1
    }
}
