package com.example.climapulse.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mail
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.climapulse.ui.components.ClimaLogo
import com.example.climapulse.ui.components.ClimaTextField
import com.example.climapulse.ui.components.PrimaryButton
import com.example.climapulse.ui.components.PulseRings
import com.example.climapulse.ui.components.SecondaryButton
import com.example.climapulse.ui.components.revealOnFocus

@Composable
fun LoginScreen(
    biometricUnlock: Boolean,
    onLoggedIn: () -> Unit,
    onRegister: () -> Unit,
    onForgotPassword: (String) -> Unit,
    viewModel: LoginViewModel = viewModel(factory = LoginViewModel.Factory)
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val submitRequester = remember { BringIntoViewRequester() }
    val biometricHardware = remember { BiometricAuth.isAvailable(context) }
    val showBiometric = viewModel.canUseBiometric && biometricHardware
    var autoPromptShown by rememberSaveable { mutableStateOf(false) }

    fun launchBiometric() {
        val activity = context.findFragmentActivity() ?: return
        BiometricAuth.authenticate(
            activity = activity,
            title = "Desbloquear ClimaPulse",
            subtitle = "Usa tu huella o rostro para continuar",
            onSuccess = viewModel::onBiometricSuccess,
            onError = viewModel::onBiometricError
        )
    }

    LaunchedEffect(viewModel) { viewModel.loggedIn.collect { onLoggedIn() } }
    LaunchedEffect(showBiometric) {
        if (biometricUnlock && showBiometric && !autoPromptShown) {
            autoPromptShown = true
            launchBiometric()
        }
    }

    AuthLayout {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            PulseRings(size = 150.dp) { ClimaLogo(size = 64.dp) }
            Text(
                "Hola de nuevo",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() }
            )
            Text(
                "Ingresa para ver cómo está tu ambiente hoy.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        FormErrorBanner(viewModel.errorMessage)

        ClimaTextField(
            value = viewModel.email,
            onValueChange = { viewModel.email = it },
            label = "Correo electrónico",
            keyboardType = KeyboardType.Email,
            leadingIcon = Icons.Rounded.Mail,
            error = viewModel.emailError,
            modifier = Modifier.revealOnFocus(submitRequester)
        )
        ClimaTextField(
            value = viewModel.password,
            onValueChange = { viewModel.password = it },
            label = "Contraseña",
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done,
            onImeAction = {
                focusManager.clearFocus()
                viewModel.submit()
            },
            isPassword = true,
            leadingIcon = Icons.Rounded.Lock,
            error = viewModel.passwordError,
            modifier = Modifier.revealOnFocus(submitRequester)
        )

        Column(
            modifier = Modifier.bringIntoViewRequester(submitRequester),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextButton(
                onClick = { onForgotPassword(viewModel.email.trim()) },
                modifier = Modifier.align(Alignment.End)
            ) { Text("¿Olvidaste tu contraseña?") }
            PrimaryButton(
                text = if (viewModel.loading) "Ingresando..." else "Ingresar",
                onClick = {
                    focusManager.clearFocus()
                    viewModel.submit()
                },
                loading = viewModel.loading,
                enabled = !viewModel.loading
            )
            if (showBiometric) {
                SecondaryButton(
                    text = "Ingresar con huella",
                    icon = Icons.Rounded.Fingerprint,
                    onClick = ::launchBiometric,
                    enabled = !viewModel.loading
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("¿No tienes cuenta?", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onRegister) { Text("Regístrate") }
        }
    }
}
