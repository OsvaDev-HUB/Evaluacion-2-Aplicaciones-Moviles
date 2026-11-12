package cl.osvaldouhart.climapulse.ui.auth

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MarkEmailUnread
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import cl.osvaldouhart.climapulse.ui.components.PrimaryButton
import cl.osvaldouhart.climapulse.ui.components.PulseRings
import cl.osvaldouhart.climapulse.ui.components.SecondaryButton

/** Se muestra después de registrarse (o al entrar sin haber verificado el correo). */
@Composable
fun VerifyEmailScreen(
    onVerified: () -> Unit,
    onLoggedOut: () -> Unit,
    viewModel: VerifyEmailViewModel = viewModel(factory = VerifyEmailViewModel.Factory)
) {
    LaunchedEffect(viewModel) { viewModel.verified.collect { onVerified() } }

    AuthLayout {
        Spacer(Modifier.height(24.dp))
        PulseRings(size = 200.dp, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Icon(
                Icons.Rounded.MarkEmailUnread,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(56.dp)
            )
        }
        Text(
            "Verifica tu correo",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .semantics { heading() }
        )
        Text(
            "Te enviamos un enlace a ${viewModel.email}. Ábrelo para activar tu cuenta y luego vuelve aquí. " +
                "Si no lo ves, revisa la carpeta de spam.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        FormErrorBanner(viewModel.errorMessage)
        viewModel.infoMessage?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
        Spacer(Modifier.height(8.dp))
        PrimaryButton(
            text = if (viewModel.checking) "Revisando..." else "Ya lo verifiqué",
            onClick = viewModel::checkVerified,
            loading = viewModel.checking
        )
        SecondaryButton(
            text = if (viewModel.resendSeconds > 0) "Reenviar correo (${viewModel.resendSeconds} s)" else "Reenviar correo",
            onClick = viewModel::resend,
            loading = viewModel.resending,
            enabled = viewModel.resendSeconds == 0
        )
        TextButton(
            onClick = { viewModel.logout(onLoggedOut) },
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) { Text("Usar otra cuenta") }
    }
}
