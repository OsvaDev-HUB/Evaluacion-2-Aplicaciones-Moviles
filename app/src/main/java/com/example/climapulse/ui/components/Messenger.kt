package com.example.climapulse.ui.components

import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import com.example.climapulse.ui.theme.ClimaShapes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Mensajes breves (toasts) de confirmación. Vive a nivel de app para que un mensaje
 * lanzado justo antes de navegar hacia atrás no se pierda.
 */
class Messenger(val hostState: SnackbarHostState, private val scope: CoroutineScope) {
    fun show(message: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
        scope.launch {
            hostState.currentSnackbarData?.dismiss()
            val result = hostState.showSnackbar(
                message = message,
                actionLabel = actionLabel,
                duration = if (actionLabel != null) SnackbarDuration.Long else SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) onAction?.invoke()
        }
    }
}

val LocalMessenger = staticCompositionLocalOf<Messenger> { error("Messenger no fue provisto") }

@Composable
fun AppSnackbarHost() {
    SnackbarHost(hostState = LocalMessenger.current.hostState) { data ->
        Snackbar(snackbarData = data, shape = ClimaShapes.Medium)
    }
}

/** Muestra como snackbar cada mensaje emitido por un ViewModel. */
@Composable
fun CollectMessages(messages: Flow<String>) {
    val messenger = LocalMessenger.current
    LaunchedEffect(messages) {
        messages.collect { messenger.show(it) }
    }
}
