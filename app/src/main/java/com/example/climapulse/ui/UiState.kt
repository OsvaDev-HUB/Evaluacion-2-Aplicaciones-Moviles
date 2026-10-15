package com.example.climapulse.ui

import com.example.climapulse.data.LinkException
import com.example.climapulse.data.OfflineException

/** Toda pantalla que consume datos reacciona a estos estados en vez de ser una vista única. */
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T, val fromCache: Boolean = false) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
    data object Empty : UiState<Nothing>
}

/** Traduce una excepción técnica a un mensaje humano. Nunca se muestra el error crudo. */
fun Throwable.toUserMessage(): String = when (this) {
    is OfflineException -> "Parece que no tienes conexión. Revisa tu internet e intenta de nuevo."
    is LinkException -> "No hay conexión con el nodo sensor. Revisa el enlace WiFi en la pestaña Sensor."
    else -> "No pudimos cargar esta información, intenta de nuevo."
}
