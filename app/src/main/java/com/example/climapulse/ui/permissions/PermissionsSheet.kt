package com.example.climapulse.ui.permissions

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.example.climapulse.ui.auth.BiometricAuth
import com.example.climapulse.ui.components.ClimaCard
import com.example.climapulse.ui.components.IconBadge
import com.example.climapulse.ui.components.PrimaryButton
import com.example.climapulse.ui.components.PulseRings
import com.example.climapulse.ui.components.StatusPill
import com.example.climapulse.ui.theme.Dimens
import com.example.climapulse.ui.theme.Teal
import com.example.climapulse.ui.theme.Yellow
import kotlinx.coroutines.launch

private fun notificationsGranted(context: Context): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    } else {
        NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

private fun openNotificationSettings(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

private fun openBiometricSettings(context: Context) {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Intent(Settings.ACTION_BIOMETRIC_ENROLL)
    } else {
        Intent(Settings.ACTION_SECURITY_SETTINGS)
    }
    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/**
 * Ventana de permisos: explica para qué se usa cada permiso, muestra su estado actual
 * y permite concederlo. Si el sistema ya no muestra su diálogo, lleva a los Ajustes de Android.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsSheet(
    onNotificationsResult: (Boolean) -> Unit,
    biometricEnabled: Boolean,
    onEnableBiometric: () -> Unit,
    onDone: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var notificationsOn by remember { mutableStateOf(notificationsGranted(context)) }
    // Huella registrada en Android (hardware + al menos una huella guardada).
    var biometricReady by remember { mutableStateOf(BiometricAuth.isAvailable(context)) }
    // Tras un rechazo, el sistema puede bloquear el diálogo: entonces ofrecemos ir a Ajustes.
    var notificationsBlocked by rememberSaveable { mutableStateOf(false) }

    // Al volver desde los Ajustes de Android se refresca el estado real de cada permiso.
    LifecycleResumeEffect(Unit) {
        notificationsOn = notificationsGranted(context)
        biometricReady = BiometricAuth.isAvailable(context)
        onNotificationsResult(notificationsOn)
        onPauseOrDispose { }
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationsOn = granted
        notificationsBlocked = !granted
        onNotificationsResult(granted)
    }

    fun close() {
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDone() }
    }

    ModalBottomSheet(
        onDismissRequest = onDone,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.ScreenPadding)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PulseRings(size = 140.dp) {
                IconBadge(icon = Icons.Rounded.Shield, tint = Teal, size = 64.dp)
            }
            Text(
                "Permisos de ClimaPulse",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() }
            )
            Text(
                "Solo pedimos lo necesario para avisarte a tiempo y proteger tu cuenta. Puedes cambiarlo cuando quieras.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))

            ClimaCard {
                PermissionRow(
                    icon = Icons.Rounded.Notifications,
                    title = "Notificaciones",
                    description = "Para alertarte cuando una lectura salga de tu rango ideal.",
                    granted = notificationsOn,
                    actionLabel = if (notificationsBlocked) "Abrir ajustes" else "Permitir",
                    onAction = {
                        when {
                            notificationsBlocked || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ->
                                openNotificationSettings(context)
                            else -> launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(start = 72.dp), color = MaterialTheme.colorScheme.outlineVariant)
                PermissionRow(
                    icon = Icons.Rounded.Fingerprint,
                    title = "Huella o rostro",
                    description = if (biometricReady) "Para entrar a ClimaPulse sin escribir tu contraseña."
                    else "Primero registra una huella en los ajustes de Android.",
                    granted = biometricEnabled,
                    // Si ya hay huella en Android se activa aquí mismo; si no, se abre Ajustes.
                    actionLabel = if (biometricReady) "Activar" else "Configurar en Android",
                    onAction = { if (biometricReady) onEnableBiometric() else openBiometricSettings(context) }
                )
            }

            Spacer(Modifier.height(4.dp))
            PrimaryButton(
                text = if (notificationsOn) "Continuar" else "Continuar sin notificaciones",
                onClick = ::close
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PermissionRow(
    icon: ImageVector,
    title: String,
    description: String,
    granted: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
    grantedLabel: String = "Activado"
) {
    ListItem(
        leadingContent = { IconBadge(icon = icon, tint = if (granted) Teal else Yellow, size = 44.dp) },
        headlineContent = { Text(title) },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 4.dp)) {
                Text(description)
                if (granted) {
                    StatusPill(text = grantedLabel, color = Teal, icon = Icons.Rounded.CheckCircle)
                } else {
                    FilledTonalButton(
                        onClick = onAction,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Icon(icon, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                        Text(actionLabel)
                    }
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}
