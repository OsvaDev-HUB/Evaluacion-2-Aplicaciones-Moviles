package com.example.climapulse.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mail
import androidx.compose.material.icons.rounded.MarkEmailRead
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.climapulse.ui.components.BackTopBar
import com.example.climapulse.ui.components.ClimaTextField
import com.example.climapulse.ui.components.PrimaryButton
import com.example.climapulse.ui.components.ScreenTitle
import com.example.climapulse.ui.components.SecondaryButton
import com.example.climapulse.ui.components.revealOnFocus
import com.example.climapulse.ui.components.PulseRings

/** Recuperación sin fricción: un solo campo (precargado desde el login) y una confirmación clara. */
@Composable
fun RecoveryScreen(
    onBack: () -> Unit,
    viewModel: RecoveryViewModel = viewModel(factory = RecoveryViewModel.Factory)
) {
    val focusManager = LocalFocusManager.current
    val submitRequester = remember { BringIntoViewRequester() }

    AuthLayout(topBar = { BackTopBar(title = "Recuperar contraseña", onBack = onBack) }) {
        if (viewModel.sent) {
            Spacer(Modifier.height(24.dp))
            PulseRings(size = 200.dp, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Icon(
                    Icons.Rounded.MarkEmailRead,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(56.dp)
                )
            }
            Text(
                "Revisa tu correo",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Text(
                "Enviamos un enlace a ${viewModel.email.trim()} para que crees una nueva contraseña. Puede tardar un par de minutos.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            PrimaryButton(text = "Volver a iniciar sesión", onClick = onBack)
            SecondaryButton(text = "Usar otro correo", onClick = viewModel::editEmail)
        } else {
            ScreenTitle(
                title = "¿Olvidaste tu contraseña?",
                subtitle = "Escribe tu correo y te enviaremos un enlace para restablecerla."
            )
            FormErrorBanner(viewModel.errorMessage)
            ClimaTextField(
                value = viewModel.email,
                onValueChange = { viewModel.email = it },
                label = "Correo electrónico",
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Send,
                onImeAction = {
                    focusManager.clearFocus()
                    viewModel.submit()
                },
                leadingIcon = Icons.Rounded.Mail,
                error = viewModel.emailError,
                modifier = Modifier.revealOnFocus(submitRequester)
            )
            PrimaryButton(
                text = if (viewModel.loading) "Enviando..." else "Enviar enlace",
                onClick = {
                    focusManager.clearFocus()
                    viewModel.submit()
                },
                loading = viewModel.loading,
                modifier = Modifier.bringIntoViewRequester(submitRequester)
            )
        }
    }
}
