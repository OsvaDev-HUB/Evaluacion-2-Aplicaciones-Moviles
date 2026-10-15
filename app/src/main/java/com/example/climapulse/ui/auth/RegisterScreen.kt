package com.example.climapulse.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mail
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.climapulse.ui.components.BackTopBar
import com.example.climapulse.ui.components.ClimaTextField
import com.example.climapulse.ui.components.PrimaryButton
import com.example.climapulse.ui.components.ScreenTitle
import com.example.climapulse.ui.components.revealOnFocus

@Composable
fun RegisterScreen(
    onRegistered: () -> Unit,
    onOpenTerms: () -> Unit,
    onBack: () -> Unit,
    viewModel: RegisterViewModel = viewModel(factory = RegisterViewModel.Factory)
) {
    val focusManager = LocalFocusManager.current
    val submitRequester = remember { BringIntoViewRequester() }
    val reveal = Modifier.revealOnFocus(submitRequester)

    LaunchedEffect(viewModel) { viewModel.registered.collect { onRegistered() } }

    AuthLayout(topBar = { BackTopBar(title = "Crear cuenta", onBack = onBack) }) {
        ScreenTitle(
            title = "Empecemos",
            subtitle = "Crea tu cuenta para guardar tus mediciones y preferencias."
        )
        FormErrorBanner(viewModel.errorMessage)

        ClimaTextField(
            value = viewModel.name,
            onValueChange = { viewModel.name = it },
            label = "Nombre",
            capitalization = KeyboardCapitalization.Words,
            leadingIcon = Icons.Rounded.Person,
            error = viewModel.nameError,
            modifier = reveal
        )
        ClimaTextField(
            value = viewModel.email,
            onValueChange = { viewModel.email = it },
            label = "Correo electrónico",
            keyboardType = KeyboardType.Email,
            leadingIcon = Icons.Rounded.Mail,
            error = viewModel.emailError,
            modifier = reveal
        )
        ClimaTextField(
            value = viewModel.phone,
            onValueChange = { input -> viewModel.phone = input.filter { it.isDigit() || it in "+ " } },
            label = "Teléfono (opcional)",
            keyboardType = KeyboardType.Phone,
            leadingIcon = Icons.Rounded.Phone,
            error = viewModel.phoneError,
            modifier = reveal
        )
        ClimaTextField(
            value = viewModel.password,
            onValueChange = { viewModel.password = it },
            label = "Contraseña",
            keyboardType = KeyboardType.Password,
            isPassword = true,
            leadingIcon = Icons.Rounded.Lock,
            error = viewModel.passwordError,
            modifier = reveal
        )
        ClimaTextField(
            value = viewModel.confirmPassword,
            onValueChange = { viewModel.confirmPassword = it },
            label = "Repite la contraseña",
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done,
            onImeAction = { focusManager.clearFocus() },
            isPassword = true,
            leadingIcon = Icons.Rounded.Lock,
            error = viewModel.confirmError,
            modifier = reveal
        )

        Column(
            modifier = Modifier.bringIntoViewRequester(submitRequester),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = viewModel.acceptedTerms,
                        role = Role.Checkbox,
                        onValueChange = { viewModel.acceptedTerms = it }
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(checked = viewModel.acceptedTerms, onCheckedChange = null)
                Text(
                    "Acepto los términos de servicio",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onOpenTerms) {
                    Text("Leer")
                }
            }
            PrimaryButton(
                text = if (viewModel.loading) "Creando cuenta..." else "Crear cuenta",
                onClick = {
                    focusManager.clearFocus()
                    viewModel.submit()
                },
                loading = viewModel.loading
            )
        }
    }
}
