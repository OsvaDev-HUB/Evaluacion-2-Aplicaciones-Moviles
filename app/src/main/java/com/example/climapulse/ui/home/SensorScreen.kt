package com.example.climapulse.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.climapulse.data.Actuators
import com.example.climapulse.data.FoundSensor
import com.example.climapulse.data.LinkRole
import com.example.climapulse.data.LinkState
import com.example.climapulse.data.Metric
import com.example.climapulse.data.WifiLink
import com.example.climapulse.ui.components.ClimaCard
import com.example.climapulse.ui.components.LinkLine
import com.example.climapulse.ui.components.isActive
import com.example.climapulse.ui.theme.ClimaShapes
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import com.example.climapulse.ui.components.ClimaTextField
import com.example.climapulse.ui.components.HeroCard
import com.example.climapulse.ui.components.IconBadge
import com.example.climapulse.ui.components.PrimaryButton
import com.example.climapulse.ui.components.PulseRings
import com.example.climapulse.ui.components.ScreenTitle
import com.example.climapulse.ui.components.SecondaryButton
import com.example.climapulse.ui.components.SectionHeading
import com.example.climapulse.ui.components.StatusPill
import com.example.climapulse.ui.theme.Dimens
import com.example.climapulse.ui.theme.Teal
import com.example.climapulse.ui.theme.Yellow

@Composable
fun SensorScreen(viewModel: SensorViewModel = viewModel(factory = SensorViewModel.Factory)) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val found by viewModel.foundSensors.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Dimens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.ItemGap)
    ) {
        ScreenTitle(title = "Enlace WiFi", subtitle = "Conecta dos teléfonos: uno simula el sensor y el otro lo monitorea.")
        Spacer(Modifier.height(4.dp))

        LinkHero(state)

        state.error?.let { message ->
            ClimaCard(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(message, style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = viewModel::retrySearch, modifier = Modifier.align(Alignment.End)) {
                        Text("Intentar de nuevo")
                    }
                }
            }
        }

        when {
            state.role == LinkRole.SENSOR -> {
                SensorInfoCard(state)
                SectionHeading(title = "Actuadores del nodo")
                ActuatorControls(state.actuators, onChange = viewModel::updateActuators)
                SecondaryButton(text = "Detener nodo sensor", onClick = viewModel::stop)
            }

            state.role == LinkRole.MONITOR && state.connected -> {
                ClimaCard {
                    InfoRow(Icons.Rounded.Router, "Conectado a", state.peerName)
                    InfoRow(Icons.Rounded.Wifi, "Dirección", state.address)
                }
                SectionHeading(title = "Control remoto")
                ActuatorControls(state.actuators, onChange = viewModel::updateActuators)
                SecondaryButton(text = "Desconectar", onClick = viewModel::stop)
            }

            viewModel.choosingMonitor || state.role == LinkRole.MONITOR -> {
                MonitorForm(viewModel, found, connecting = state.connecting)
            }

            else -> {
                PrimaryButton(text = "Ser nodo sensor", icon = Icons.Rounded.Sensors, onClick = viewModel::startSensor)
                SecondaryButton(text = "Ser monitor", icon = Icons.Rounded.PhoneAndroid, onClick = viewModel::startMonitor)
            }
        }

        SectionHeading(title = "Seguridad del enlace")
        ClimaCard {
            InfoRow(
                Icons.Rounded.Lock,
                "Mensajes cifrados (AES-GCM)",
                "La llave se crea a partir del PIN. Sin el PIN no se pueden leer las lecturas ni controlar el sensor."
            )
        }

        SectionHeading(title = "Lecturas que se envían")
        ClimaCard {
            Metric.entries.forEachIndexed { index, metric ->
                ListItem(
                    headlineContent = { Text(metric.label) },
                    supportingContent = {
                        Text(
                            when (metric) {
                                Metric.TEMPERATURE -> "Medición en grados Celsius"
                                Metric.HUMIDITY -> "Humedad relativa del ambiente"
                                Metric.LIGHT -> "Nivel de iluminación en lux"
                            }
                        )
                    },
                    leadingContent = { IconBadge(icon = metric.icon, tint = metric.accent) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
                if (index < Metric.entries.lastIndex) {
                    HorizontalDivider(modifier = Modifier.padding(start = 76.dp), color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

/** Cabecera del enlace: la línea sensor → monitor y una frase con el estado. */
@Composable
private fun LinkHero(state: LinkState) {
    val colors = MaterialTheme.colorScheme
    val title = when {
        state.role == LinkRole.SENSOR && state.monitors > 0 -> "Enviando lecturas"
        state.role == LinkRole.SENSOR -> "Esperando un monitor"
        state.role == LinkRole.MONITOR && state.connected -> "Recibiendo lecturas"
        state.connecting -> "Conectando..."
        state.role == LinkRole.MONITOR -> "Sin conexión"
        else -> "Sin enlace"
    }
    val description = when {
        state.role == LinkRole.SENSOR -> "Este teléfono simula el sensor y envía una lectura cada 3 segundos."
        state.role == LinkRole.MONITOR && state.connected -> "Ves las lecturas del otro teléfono en tiempo real y puedes controlarlo."
        else -> "Elige qué hará este teléfono. Los dos deben estar en la misma red WiFi."
    }
    val container = if (state.isActive) colors.primaryContainer else colors.surfaceContainerLow
    val content = if (state.isActive) colors.onPrimaryContainer else colors.onSurface

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ClimaShapes.Hero)
            .background(container)
            .padding(horizontal = 16.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LinkLine(state, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(20.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, color = content)
        Spacer(Modifier.height(4.dp))
        Text(
            description,
            style = MaterialTheme.typography.bodyMedium,
            color = content.copy(alpha = 0.8f),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun SensorInfoCard(state: LinkState) {
    ClimaCard(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("PIN para el monitor", style = MaterialTheme.typography.labelLarge)
            Text(
                state.pin.chunked(3).joinToString(" "),
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 4.sp
            )
        }
    }
    ClimaCard {
        InfoRow(Icons.Rounded.Wifi, "Dirección del sensor", state.address.ifEmpty { "Abriendo puerto ${WifiLink.PORT}..." })
        InfoRow(Icons.Rounded.PhoneAndroid, "Monitores conectados", state.monitors.toString())
    }
}

@Composable
private fun MonitorForm(viewModel: SensorViewModel, found: List<FoundSensor>, connecting: Boolean) {
    SectionHeading(title = "Sensores en tu red")
    ClimaCard {
        if (found.isEmpty()) {
            ListItem(
                headlineContent = { Text("Buscando sensores...") },
                supportingContent = { Text("Si no aparece, escribe la IP que muestra el teléfono sensor.") },
                leadingContent = { CircularProgressIndicator(modifier = Modifier.padding(8.dp), strokeWidth = 2.dp) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )
        }
        found.forEach { sensor ->
            ListItem(
                headlineContent = { Text(sensor.name) },
                supportingContent = { Text("${sensor.host}:${sensor.port}") },
                leadingContent = { IconBadge(icon = Icons.Rounded.Router, size = 40.dp) },
                trailingContent = { if (viewModel.host == sensor.host) Text("Elegido", color = Teal) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable { viewModel.selectSensor(sensor) }
            )
        }
    }

    ClimaTextField(
        value = viewModel.host,
        onValueChange = { viewModel.host = it },
        label = "IP del nodo sensor",
        keyboardType = KeyboardType.Uri,
        leadingIcon = Icons.Rounded.Wifi
    )
    ClimaTextField(
        value = viewModel.pin,
        // Solo números y máximo 6 dígitos.
        onValueChange = { text -> viewModel.pin = text.filter { it.isDigit() }.take(6) },
        label = "PIN de 6 dígitos",
        keyboardType = KeyboardType.NumberPassword,
        imeAction = ImeAction.Done,
        onImeAction = viewModel::connect,
        leadingIcon = Icons.Rounded.Key,
        error = viewModel.formError
    )
    PrimaryButton(
        text = if (connecting) "Conectando..." else "Conectar",
        loading = connecting,
        enabled = !connecting,
        onClick = viewModel::connect
    )
    SecondaryButton(text = "Cancelar", onClick = viewModel::stop)
}

@Composable
private fun ActuatorControls(actuators: Actuators, onChange: (Actuators) -> Unit) {
    ClimaCard {
        SwitchRow(Icons.Rounded.Air, "Ventilador", "Baja la temperatura y la humedad", actuators.fan) {
            onChange(actuators.copy(fan = it))
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        SwitchRow(Icons.Rounded.Lightbulb, "Lámpara", "Sube el nivel de luz", actuators.lamp) {
            onChange(actuators.copy(lamp = it))
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        SwitchRow(Icons.Rounded.PauseCircle, "Pausar envío", "El sensor deja de mandar lecturas", actuators.paused) {
            onChange(actuators.copy(paused = it))
        }
    }
}

@Composable
private fun SwitchRow(icon: ImageVector, title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = { IconBadge(icon = icon, size = 40.dp) },
        // El Switch no recibe clics propios: toda la fila cambia el valor.
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
    )
}

@Composable
private fun InfoRow(icon: ImageVector, title: String, value: String) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(value) },
        leadingContent = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}
