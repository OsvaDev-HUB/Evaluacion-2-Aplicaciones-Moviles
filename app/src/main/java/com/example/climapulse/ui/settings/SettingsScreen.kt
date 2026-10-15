package com.example.climapulse.ui.settings

import androidx.compose.material.icons.rounded.WavingHand
import androidx.compose.foundation.layout.fillMaxWidth
import com.example.climapulse.data.ThemeMode
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SegmentedButton
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.climapulse.ui.auth.BiometricAuth
import com.example.climapulse.ui.auth.findFragmentActivity
import com.example.climapulse.ui.components.ClimaCard
import com.example.climapulse.ui.components.CollectMessages
import com.example.climapulse.ui.components.ConfirmDialog
import com.example.climapulse.ui.components.HeroCard
import com.example.climapulse.ui.components.IconBadge
import com.example.climapulse.ui.components.LocalMessenger
import com.example.climapulse.ui.components.ScreenTitle
import com.example.climapulse.ui.components.SecondaryButton
import com.example.climapulse.data.LinkRole
import com.example.climapulse.data.LinkState
import com.example.climapulse.ui.home.HomeViewModel
import com.example.climapulse.ui.theme.Dimens
import com.example.climapulse.ui.theme.Teal

@Composable
fun SettingsScreen(
    linkState: LinkState,
    onRefresh: () -> Unit,
    onEditProfile: () -> Unit,
    onOpenTerms: () -> Unit,
    onLoggedOut: () -> Unit,
    onReplayTour: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)
) {
    val context = LocalContext.current
    val messenger = LocalMessenger.current
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val notifications by viewModel.notificationsEnabled.collectAsStateWithLifecycle()
    val autoRefresh by viewModel.autoRefreshEnabled.collectAsStateWithLifecycle()
    val biometric by viewModel.biometricEnabled.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val showWelcome by viewModel.showWelcome.collectAsStateWithLifecycle()
    val biometricAvailable = remember { BiometricAuth.isAvailable(context) }
    var confirmLogout by rememberSaveable { mutableStateOf(false) }
    CollectMessages(viewModel.messages)

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.setNotifications(true)
        } else {
            messenger.show("Para recibir alertas, permite las notificaciones desde los ajustes del sistema.")
        }
    }

    fun onNotificationsChanged(enabled: Boolean) {
        val needsPermission = enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.setNotifications(enabled)
        }
    }

    fun onBiometricChanged(enabled: Boolean) {
        if (!enabled) {
            viewModel.setBiometric(false)
            return
        }
        // Se verifica la identidad antes de activar el desbloqueo biométrico.
        val activity = context.findFragmentActivity() ?: return
        BiometricAuth.authenticate(
            activity = activity,
            title = "Activar desbloqueo con huella",
            subtitle = "Confirma tu identidad",
            onSuccess = { viewModel.setBiometric(true) },
            onError = { messenger.show(it) }
        )
    }

    if (confirmLogout) {
        ConfirmDialog(
            title = "¿Cerrar sesión?",
            message = "Tendrás que ingresar de nuevo con tu correo y contraseña.",
            confirmLabel = "Cerrar sesión",
            icon = Icons.AutoMirrored.Rounded.Logout,
            destructive = true,
            onConfirm = {
                confirmLogout = false
                viewModel.logout(onLoggedOut)
            },
            onDismiss = { confirmLogout = false }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Dimens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.ItemGap)
    ) {
        ScreenTitle(title = "Perfil", subtitle = "Gestiona tu cuenta y tus preferencias.")
        Spacer(Modifier.height(4.dp))

        ProfileCard(name = profile?.name.orEmpty(), email = profile?.email.orEmpty(), onEdit = onEditProfile)

        SettingsSection(title = "Fuente de datos") {
            SettingsRow(
                icon = Icons.Rounded.Sensors,
                title = when (linkState.role) {
                    LinkRole.SENSOR -> "Este teléfono es el nodo sensor"
                    LinkRole.MONITOR -> if (linkState.connected) "Monitor de ${linkState.peerName}" else "Monitor sin conexión"
                    LinkRole.NONE -> "Datos de demostración"
                },
                subtitle = when (linkState.role) {
                    LinkRole.SENSOR -> "Enviando lecturas a ${linkState.monitors} monitor(es) por WiFi"
                    LinkRole.MONITOR -> "Lecturas recibidas por WiFi desde ${linkState.address}"
                    LinkRole.NONE -> "Conecta dos teléfonos en la pestaña Sensor"
                }
            )
            SettingsDivider()
            SettingsRow(icon = Icons.Rounded.Refresh, title = "Actualizar ahora", subtitle = "Solicitar una nueva lectura", onClick = onRefresh)
        }

        SettingsSection(title = "Apariencia") {
            ThemeSelector(selected = themeMode, onSelect = viewModel::setThemeMode)
        }

        SettingsSection(title = "Preferencias") {
            SettingsSwitchRow(
                icon = Icons.Rounded.Notifications,
                title = "Alertas de ambiente",
                subtitle = "Avisarme si una lectura sale del rango",
                checked = notifications,
                onCheckedChange = ::onNotificationsChanged
            )
            SettingsDivider()
            SettingsSwitchRow(
                icon = Icons.Rounded.Schedule,
                title = "Actualización automática",
                subtitle = "Cada ${HomeViewModel.AUTO_REFRESH_MILLIS / 1000} segundos",
                checked = autoRefresh,
                onCheckedChange = { viewModel.setAutoRefresh(it) }
            )
        }

        SettingsSection(title = "Seguridad") {
            SettingsSwitchRow(
                icon = Icons.Rounded.Fingerprint,
                title = "Desbloqueo con huella",
                subtitle = if (biometricAvailable) "Ingresa sin escribir tu contraseña" else "Tu dispositivo no tiene biometría configurada",
                checked = biometric && biometricAvailable,
                enabled = biometricAvailable,
                onCheckedChange = ::onBiometricChanged
            )
        }

        SettingsSection(title = "Ayuda") {
            SettingsSwitchRow(
                icon = Icons.Rounded.WavingHand,
                title = "Mostrar bienvenida al abrir",
                subtitle = "Las pantallas de presentación antes del login",
                checked = showWelcome,
                onCheckedChange = { viewModel.setShowWelcome(it) }
            )
            SettingsDivider()
            SettingsRow(
                icon = Icons.Rounded.School,
                title = "Ver tutorial de nuevo",
                subtitle = "Recorre las funciones principales del Inicio",
                onClick = onReplayTour
            )
        }

        SettingsSection(title = "Legal") {
            SettingsRow(icon = Icons.Rounded.Description, title = "Términos de servicio", subtitle = "Condiciones de uso de ClimaPulse", onClick = onOpenTerms)
            SettingsDivider()
            SettingsRow(icon = Icons.Rounded.PrivacyTip, title = "Privacidad", subtitle = "Cómo cuidamos tus datos", onClick = onOpenTerms)
            SettingsDivider()
            SettingsRow(icon = Icons.Rounded.Info, title = "Versión", subtitle = "ClimaPulse 1.0")
        }

        Spacer(Modifier.height(4.dp))
        SecondaryButton(
            text = "Cerrar sesión",
            icon = Icons.AutoMirrored.Rounded.Logout,
            contentColor = MaterialTheme.colorScheme.error,
            onClick = { confirmLogout = true }
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ProfileCard(name: String, email: String, onEdit: () -> Unit) {
    val initials = name.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }
    HeroCard(accent = Teal) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text(initials.ifEmpty { "?" }, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimary)
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(name.ifBlank { "Tu nombre" }, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    email,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        FilledTonalButton(
            onClick = onEdit,
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        ) {
            Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
            Text("Editar perfil")
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(start = 4.dp, top = 8.dp)
                .semantics { heading() }
        )
        ClimaCard(content = content)
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(modifier = Modifier.padding(start = 72.dp), color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun SettingsRow(icon: ImageVector, title: String, subtitle: String, onClick: (() -> Unit)? = null) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = { IconBadge(icon = icon, size = 40.dp) },
        trailingContent = onClick?.let { { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null) } },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = if (onClick != null) {
            Modifier.clickable(role = Role.Button, onClick = onClick)
        } else {
            Modifier.semantics(mergeDescendants = true) {}
        }
    )
}

@Composable
private fun SettingsSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    // Toda la fila es el control: área táctil amplia y un solo anuncio para lectores de pantalla.
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = { IconBadge(icon = icon, size = 40.dp) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null, enabled = enabled) },
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent,
            headlineColor = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
        ),
        modifier = Modifier.toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
    )
}

/** Botones segmentados de Material 3 para elegir el tema: Sistema, Claro u Oscuro. */
@Composable
private fun ThemeSelector(selected: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    val options = listOf(
        Triple(ThemeMode.SYSTEM, "Sistema", Icons.Rounded.BrightnessAuto),
        Triple(ThemeMode.LIGHT, "Claro", Icons.Rounded.LightMode),
        Triple(ThemeMode.DARK, "Oscuro", Icons.Rounded.DarkMode)
    )
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Tema", style = MaterialTheme.typography.bodyLarge)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, (mode, label, icon) ->
                SegmentedButton(
                    selected = selected == mode,
                    onClick = { onSelect(mode) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                    icon = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    label = { Text(label) }
                )
            }
        }
    }
}
