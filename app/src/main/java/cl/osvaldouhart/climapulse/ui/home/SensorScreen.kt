package cl.osvaldouhart.climapulse.ui.home

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
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.EnergySavingsLeaf
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PauseCircle
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cl.osvaldouhart.climapulse.data.Actuators
import cl.osvaldouhart.climapulse.data.FoundSensor
import cl.osvaldouhart.climapulse.data.LinkCrypto
import cl.osvaldouhart.climapulse.data.LinkRole
import cl.osvaldouhart.climapulse.data.LinkState
import cl.osvaldouhart.climapulse.data.Metric
import cl.osvaldouhart.climapulse.ui.components.ClimaCard
import cl.osvaldouhart.climapulse.ui.components.LinkLine
import cl.osvaldouhart.climapulse.ui.components.isActive
import cl.osvaldouhart.climapulse.ui.theme.ClimaShapes
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import cl.osvaldouhart.climapulse.ui.components.ClimaTextField
import cl.osvaldouhart.climapulse.ui.components.HeroCard
import cl.osvaldouhart.climapulse.ui.components.IconBadge
import cl.osvaldouhart.climapulse.ui.components.BulbGlyph
import cl.osvaldouhart.climapulse.ui.components.FanGlyph
import cl.osvaldouhart.climapulse.ui.components.GlyphBadge
import cl.osvaldouhart.climapulse.ui.components.PrimaryButton
import cl.osvaldouhart.climapulse.ui.components.PulseRings
import cl.osvaldouhart.climapulse.ui.components.ScreenTitle
import cl.osvaldouhart.climapulse.ui.components.Illustration
import cl.osvaldouhart.climapulse.ui.components.IllustrationImage
import cl.osvaldouhart.climapulse.ui.components.SecondaryButton
import cl.osvaldouhart.climapulse.ui.components.SectionHeading
import cl.osvaldouhart.climapulse.ui.components.StatusPill
import cl.osvaldouhart.climapulse.ui.theme.Dimens
import cl.osvaldouhart.climapulse.ui.theme.Teal
import cl.osvaldouhart.climapulse.ui.theme.Yellow
import cl.osvaldouhart.climapulse.ui.theme.BrandYellow

@Composable
fun SensorScreen(viewModel: SensorViewModel = viewModel(factory = SensorViewModel.Factory)) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val found by viewModel.foundSensors.collectAsStateWithLifecycle()
    // Cuenta las lecturas recibidas mientras la pantalla está abierta.
    var pulse by remember { mutableIntStateOf(0) }
    LaunchedEffect(viewModel) { viewModel.readings.collect { pulse++ } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Dimens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.ItemGap)
    ) {
        ScreenTitle(title = "Enlace WiFi", subtitle = "Conecta la app al sensor ESP8266 de tu red WiFi.")
        Spacer(Modifier.height(4.dp))

        LinkHero(
            state,
            // Busca solo mientras elige sensor; al intentar conectar el estado pasa a "Conectando..."
            searching = viewModel.choosingMonitor && state.role == LinkRole.NONE,
            pulse = pulse
        )

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

        // Aviso que mandó el nodo por su cuenta (falla del DHT11, señal débil, memoria llena).
        state.notice?.takeIf { it.problem && state.connected }?.let { notice ->
            ClimaCard(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
            ) {
                ListItem(
                    headlineContent = { Text("Aviso del sensor") },
                    supportingContent = { Text(notice.message) },
                    leadingContent = { Icon(Icons.Rounded.WarningAmber, contentDescription = null) },
                    colors = ListItemDefaults.colors(
                        containerColor = Color.Transparent,
                        headlineColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        supportingColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        leadingIconColor = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                )
            }
        }

        when {
            state.role == LinkRole.MONITOR && state.connected -> {
                ClimaCard {
                    InfoRow(Icons.Rounded.Router, "Conectado a", state.peerName)
                    InfoRow(Icons.Rounded.Wifi, "Dirección", state.address)
                    if (state.recovered > 0) {
                        InfoRow(
                            Icons.Rounded.CloudDownload,
                            "Lecturas recuperadas",
                            "${state.recovered} lecturas que el sensor guardó sin conexión ya están en tu historial"
                        )
                    }
                }
                SectionHeading(title = "Control remoto")
                ActuatorControls(state.actuators, onChange = viewModel::updateActuators)
                SecondaryButton(text = "Desconectar", onClick = viewModel::stop)
            }

            viewModel.choosingMonitor || state.role == LinkRole.MONITOR -> {
                MonitorForm(viewModel, found, connecting = state.connecting)
            }

            else -> {
                IllustrationImage(
                    Illustration.SENSOR_SEARCH,
                    size = 180.dp,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                PrimaryButton(text = "Buscar sensor", icon = Icons.Rounded.Sensors, onClick = viewModel::startMonitor)
            }
        }

        SectionHeading(title = "Seguridad del enlace")
        ClimaCard {
            InfoRow(
                Icons.Rounded.Lock,
                "Mensajes cifrados (AES-GCM)",
                "La llave se crea a partir de la clave del sensor (mínimo 10 caracteres con letras y números). Sin ella no se pueden leer las lecturas ni controlar el sensor."
            )
            InfoRow(
                Icons.Rounded.VerifiedUser,
                "Protección contra repetición",
                "Cada conexión tiene una sesión nueva y cada mensaje un contador que siempre sube. Un mensaje grabado y reenviado se descarta." +
                    if (state.rejected > 0) " Descartados en esta conexión: ${state.rejected}." else ""
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
                                Metric.LIGHT -> "Nivel de luz del LDR (escala de 0 a 1000)"
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
private fun LinkHero(state: LinkState, searching: Boolean, pulse: Int) {
    val colors = MaterialTheme.colorScheme
    val title = when {
        state.role == LinkRole.MONITOR && state.connected -> "Recibiendo lecturas"
        state.connecting -> "Conectando..."
        searching -> "Buscando sensor..."
        state.role == LinkRole.MONITOR -> "Sin conexión"
        else -> "Sin enlace"
    }
    val description = when {
        state.role == LinkRole.MONITOR && state.connected ->
            "Ves las lecturas del DHT11 y del LDR cada ${if (state.actuators.eco) 10 else 3} segundos y puedes controlar el sensor."
        else -> "El teléfono y el ESP8266 deben estar en la misma red WiFi."
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
        LinkLine(state, modifier = Modifier.fillMaxWidth(), searching = searching, pulse = pulse, container = container, content = content)
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
private fun MonitorForm(viewModel: SensorViewModel, found: List<FoundSensor>, connecting: Boolean) {
    SectionHeading(title = "Sensores en tu red")
    ClimaCard {
        if (found.isEmpty()) {
            ListItem(
                headlineContent = { Text("Buscando sensores...") },
                supportingContent = { Text("Si no aparece, escribe la IP que muestra el monitor serie del ESP8266.") },
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
        // Sin espacios y con el largo máximo que acepta el sensor.
        onValueChange = { text -> viewModel.pin = text.filterNot { it.isWhitespace() }.take(LinkCrypto.MAX_KEY_LENGTH) },
        label = "Clave del sensor",
        keyboardType = KeyboardType.Password,
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
    val colors = MaterialTheme.colorScheme
    ClimaCard {
        SwitchRow(
            title = "Ventilador",
            subtitle = "Enciende el LED D0 del sensor",
            checked = actuators.fan,
            onCheckedChange = { onChange(actuators.copy(fan = it)) }
        ) {
            GlyphBadge(tint = colors.primary, size = 40.dp) { size ->
                FanGlyph(on = actuators.fan, tint = colors.primary, size = size)
            }
        }
        HorizontalDivider(color = colors.outlineVariant)
        SwitchRow(
            title = "Lámpara",
            subtitle = "Enciende el LED D4 del sensor",
            checked = actuators.lamp,
            onCheckedChange = { onChange(actuators.copy(lamp = it)) }
        ) {
            GlyphBadge(tint = colors.primary, size = 40.dp) { size ->
                BulbGlyph(on = actuators.lamp, tint = colors.primary, glow = BrandYellow, size = size)
            }
        }
        HorizontalDivider(color = colors.outlineVariant)
        SwitchRow(
            title = "Pausar envío",
            subtitle = "El sensor deja de mandar lecturas",
            checked = actuators.paused,
            onCheckedChange = { onChange(actuators.copy(paused = it)) }
        ) {
            IconBadge(icon = Icons.Rounded.PauseCircle, size = 40.dp)
        }
        HorizontalDivider(color = colors.outlineVariant)
        SwitchRow(
            title = "Modo ahorro",
            subtitle = "Mide cada 10 s y la radio WiFi duerme entre mensajes",
            checked = actuators.eco,
            onCheckedChange = { onChange(actuators.copy(eco = it)) }
        ) {
            IconBadge(icon = Icons.Rounded.EnergySavingsLeaf, size = 40.dp)
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    leading: @Composable () -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = leading,
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
