package com.example.climapulse.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.climapulse.ui.auth.AuthLayout
import com.example.climapulse.ui.components.BackTopBar
import com.example.climapulse.ui.components.ClimaCard
import com.example.climapulse.ui.components.ScreenTitle
import com.example.climapulse.ui.theme.Dimens

private val sections = listOf(
    "1. Uso del servicio" to "ClimaPulse entrega lecturas de temperatura, humedad y luz con fines informativos. Las recomendaciones no reemplazan la asesoría de un profesional.",
    "2. Tu cuenta" to "Eres responsable de mantener la confidencialidad de tus credenciales. Tu sesión se guarda cifrada en el dispositivo y expira automáticamente después de 7 días.",
    "3. Datos y privacidad" to "Las mediciones y preferencias se almacenan localmente en tu teléfono. No vendemos ni compartimos tu información con terceros.",
    "4. Notificaciones" to "Solo enviamos alertas cuando una lectura sale de tu rango ideal. Puedes desactivarlas en cualquier momento desde Ajustes.",
    "5. Cambios" to "Podemos actualizar estos términos. Te avisaremos dentro de la app antes de que los cambios entren en vigencia."
)

@Composable
fun TermsScreen(onBack: () -> Unit) {
    AuthLayout(topBar = { BackTopBar(title = "Términos de servicio", onBack = onBack) }) {
        ScreenTitle(title = "Términos y privacidad", subtitle = "Última actualización: 1 de octubre de 2026")
        sections.forEach { (title, body) ->
            ClimaCard {
                Column(modifier = Modifier.padding(Dimens.CardPadding)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.semantics { heading() }
                    )
                    Text(
                        body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }
}
