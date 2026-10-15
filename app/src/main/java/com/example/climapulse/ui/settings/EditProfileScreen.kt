package com.example.climapulse.ui.settings

import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mail
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Save
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.climapulse.ui.auth.AuthLayout
import com.example.climapulse.ui.components.BackTopBar
import com.example.climapulse.ui.components.ClimaTextField
import com.example.climapulse.ui.components.LocalMessenger
import com.example.climapulse.ui.components.PrimaryButton
import com.example.climapulse.ui.components.ScreenTitle
import com.example.climapulse.ui.components.revealOnFocus

@Composable
fun EditProfileScreen(
    onBack: () -> Unit,
    viewModel: EditProfileViewModel = viewModel(factory = EditProfileViewModel.Factory)
) {
    val messenger = LocalMessenger.current
    val focusManager = LocalFocusManager.current
    val saveRequester = remember { BringIntoViewRequester() }
    val reveal = Modifier.revealOnFocus(saveRequester)

    LaunchedEffect(viewModel) {
        viewModel.saved.collect {
            messenger.show("Perfil actualizado")
            onBack()
        }
    }

    AuthLayout(topBar = { BackTopBar(title = "Editar perfil", onBack = onBack) }) {
        ScreenTitle(title = "Tus datos", subtitle = "Esta información se usa para personalizar tu experiencia.")
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
            label = "Teléfono",
            keyboardType = KeyboardType.Phone,
            imeAction = ImeAction.Done,
            onImeAction = {
                focusManager.clearFocus()
                viewModel.save()
            },
            leadingIcon = Icons.Rounded.Phone,
            error = viewModel.phoneError,
            modifier = reveal
        )
        PrimaryButton(
            text = "Guardar cambios",
            icon = Icons.Rounded.Save,
            loading = viewModel.saving,
            onClick = {
                focusManager.clearFocus()
                viewModel.save()
            },
            modifier = Modifier.bringIntoViewRequester(saveRequester)
        )
    }
}
