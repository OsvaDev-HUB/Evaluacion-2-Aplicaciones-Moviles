package cl.osvaldouhart.climapulse.ui.settings

import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockReset
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cl.osvaldouhart.climapulse.R
import cl.osvaldouhart.climapulse.ui.auth.AuthLayout
import cl.osvaldouhart.climapulse.ui.auth.FormErrorBanner
import cl.osvaldouhart.climapulse.ui.auth.requestGoogleIdToken
import cl.osvaldouhart.climapulse.ui.components.BackTopBar
import cl.osvaldouhart.climapulse.ui.components.ClimaTextField
import cl.osvaldouhart.climapulse.ui.components.ConfirmDialog
import cl.osvaldouhart.climapulse.ui.components.LocalMessenger
import cl.osvaldouhart.climapulse.ui.components.PrimaryButton
import cl.osvaldouhart.climapulse.ui.components.ScreenTitle
import cl.osvaldouhart.climapulse.ui.components.SecondaryButton
import cl.osvaldouhart.climapulse.ui.components.revealOnFocus

@Composable
fun ChangePasswordScreen(
    onBack: () -> Unit,
    viewModel: ChangePasswordViewModel = viewModel(factory = ChangePasswordViewModel.Factory)
) {
    val messenger = LocalMessenger.current
    val focusManager = LocalFocusManager.current
    val saveRequester = remember { BringIntoViewRequester() }
    val reveal = Modifier.revealOnFocus(saveRequester)

    LaunchedEffect(viewModel) {
        viewModel.changed.collect {
            messenger.show("Contraseña actualizada")
            onBack()
        }
    }

    AuthLayout(topBar = { BackTopBar(title = "Cambiar contraseña", onBack = onBack) }) {
        ScreenTitle(
            title = "Nueva contraseña",
            subtitle = "Primero confirma tu contraseña actual. Usa al menos 8 caracteres para la nueva."
        )
        FormErrorBanner(viewModel.errorMessage)
        ClimaTextField(
            value = viewModel.current,
            onValueChange = { viewModel.current = it },
            label = "Contraseña actual",
            keyboardType = KeyboardType.Password,
            isPassword = true,
            leadingIcon = Icons.Rounded.Lock,
            error = viewModel.currentError,
            modifier = reveal
        )
        ClimaTextField(
            value = viewModel.newPassword,
            onValueChange = { viewModel.newPassword = it },
            label = "Nueva contraseña",
            keyboardType = KeyboardType.Password,
            isPassword = true,
            leadingIcon = Icons.Rounded.Key,
            error = viewModel.newError,
            modifier = reveal
        )
        ClimaTextField(
            value = viewModel.confirm,
            onValueChange = { viewModel.confirm = it },
            label = "Repite la nueva contraseña",
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done,
            onImeAction = {
                focusManager.clearFocus()
                viewModel.save()
            },
            isPassword = true,
            leadingIcon = Icons.Rounded.Key,
            error = viewModel.confirmError,
            modifier = reveal
        )
        PrimaryButton(
            text = "Cambiar contraseña",
            icon = Icons.Rounded.LockReset,
            loading = viewModel.saving,
            enabled = !viewModel.saving,
            onClick = {
                focusManager.clearFocus()
                viewModel.save()
            },
            modifier = Modifier.bringIntoViewRequester(saveRequester)
        )
    }
}

@Composable
fun DeleteAccountScreen(
    onBack: () -> Unit,
    onDeleted: () -> Unit,
    viewModel: DeleteAccountViewModel = viewModel(factory = DeleteAccountViewModel.Factory)
) {
    val context = LocalContext.current
    val messenger = LocalMessenger.current
    val focusManager = LocalFocusManager.current
    val hasPassword by viewModel.hasPassword.collectAsStateWithLifecycle()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.deleted.collect {
            messenger.show("Tu cuenta fue eliminada")
            onDeleted()
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "¿Eliminar tu cuenta?",
            message = "Se borrarán tu perfil y tus datos. Esta acción no se puede deshacer.",
            confirmLabel = "Eliminar",
            icon = Icons.Rounded.DeleteForever,
            destructive = true,
            onConfirm = {
                confirmDelete = false
                if (hasPassword) {
                    viewModel.deleteWithPassword()
                } else {
                    viewModel.deleteWithGoogle { requestGoogleIdToken(context) }
                }
            },
            onDismiss = { confirmDelete = false }
        )
    }

    AuthLayout(topBar = { BackTopBar(title = "Eliminar cuenta", onBack = onBack) }) {
        ScreenTitle(
            title = "Eliminar tu cuenta",
            subtitle = "Se borrarán tu cuenta, tu perfil y tus datos guardados en ClimaPulse. No podrás recuperarlos."
        )
        FormErrorBanner(viewModel.errorMessage)
        if (hasPassword) {
            ClimaTextField(
                value = viewModel.password,
                onValueChange = { viewModel.password = it },
                label = "Confirma tu contraseña",
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
                onImeAction = { focusManager.clearFocus() },
                isPassword = true,
                leadingIcon = Icons.Rounded.Lock,
                error = viewModel.passwordError
            )
            PrimaryButton(
                text = "Eliminar mi cuenta",
                icon = Icons.Rounded.DeleteForever,
                loading = viewModel.deleting,
                enabled = !viewModel.deleting,
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
                onClick = {
                    focusManager.clearFocus()
                    if (viewModel.password.isEmpty()) viewModel.deleteWithPassword() else confirmDelete = true
                }
            )
        } else {
            Text(
                "Para confirmar que eres tú, elige tu cuenta de Google.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            SecondaryButton(
                text = "Confirmar con Google y eliminar",
                painter = painterResource(R.drawable.ic_google),
                contentColor = MaterialTheme.colorScheme.error,
                loading = viewModel.deleting,
                enabled = !viewModel.deleting,
                onClick = { confirmDelete = true }
            )
        }
    }
}
