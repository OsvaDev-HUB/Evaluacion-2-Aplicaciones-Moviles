package com.example.climapulse.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.climapulse.R
import com.example.climapulse.ui.theme.ClimaShapes
import com.example.climapulse.ui.theme.Dimens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ClimaCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = ClimaShapes.Large,
        colors = CardDefaults.cardColors(containerColor = containerColor, contentColor = contentColor),
        content = content
    )
}

/** Acción principal de la pantalla: botón relleno (Filled). Solo debe haber uno por vista. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary
) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.ButtonHeight),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = containerColor.copy(alpha = 0.38f),
            disabledContentColor = contentColor.copy(alpha = 0.7f)
        )
    ) {
        if (loading) {
            CircularProgressIndicator(color = contentColor, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
        } else if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
        }
        if (loading || icon != null) Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/** Acción secundaria: botón con contorno, visualmente subordinado al principal. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    painter: Painter? = null,
    iconTint: Color = Color.Unspecified,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    loading: Boolean = false
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.ButtonHeight),
        border = ButtonDefaults.outlinedButtonBorder(enabled),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = contentColor)
    ) {
        when {
            loading -> CircularProgressIndicator(color = contentColor, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
            painter != null -> Icon(painter, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
            icon != null -> Icon(icon, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
        }
        if (loading || painter != null || icon != null) Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun ScreenTitle(title: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() }
        )
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun SectionHeading(title: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() }
        )
        if (action != null && onAction != null) {
            TextButton(onClick = onAction) {
                Text(action)
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** Barra superior Material 3. Los insets los maneja el contenedor que la usa. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackTopBar(title: String, onBack: () -> Unit, actions: @Composable () -> Unit = {}) {
    TopAppBar(
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Volver")
            }
        },
        actions = { actions() },
        windowInsets = WindowInsets(0),
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
    )
}

/** Ícono dentro de un contenedor tonal circular (estilo Material 3). */
@Composable
fun IconBadge(
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    painter: Painter? = null,
    tint: Color = MaterialTheme.colorScheme.primary,
    container: Color = tint.copy(alpha = 0.16f),
    size: Dp = 44.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(container),
        contentAlignment = Alignment.Center
    ) {
        val iconSize = size * 0.52f
        when {
            painter != null -> Image(painter, contentDescription = null, modifier = Modifier.size(iconSize))
            icon != null -> Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(iconSize))
        }
    }
}

/**
 * Campo de texto del sistema de diseño. El tipo de teclado se adapta al dato
 * (correo, teléfono, número, contraseña) y la acción IME avanza al siguiente campo.
 */
@Composable
fun ClimaTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: (() -> Unit)? = null,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
    isPassword: Boolean = false,
    error: String? = null,
    leadingIcon: ImageVector? = null,
    enabled: Boolean = true
) {
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        singleLine = true,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        shape = ClimaShapes.Medium,
        leadingIcon = leadingIcon?.let { { Icon(it, contentDescription = null) } },
        trailingIcon = if (isPassword) {
            {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription = if (passwordVisible) "Ocultar contraseña" else "Mostrar contraseña"
                    )
                }
            }
        } else {
            null
        },
        visualTransformation = if (isPassword && !passwordVisible) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            imeAction = imeAction,
            capitalization = capitalization,
            autoCorrectEnabled = keyboardType == KeyboardType.Text
        ),
        keyboardActions = KeyboardActions(onAny = { onImeAction?.invoke() ?: defaultKeyboardAction(imeAction) })
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    icon: ImageVector? = null,
    destructive: Boolean = false
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = icon?.let { { Icon(it, contentDescription = null) } },
        title = { Text(title) },
        text = { Text(message) },
        iconContentColor = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
fun ClimaLogo(size: Dp = 44.dp) {
    Image(painterResource(R.drawable.ic_logo), contentDescription = null, modifier = Modifier.size(size))
}

/** Microinteracción: el elemento se encoge levemente mientras se mantiene presionado. */
@Composable
fun Modifier.pressScale(interactionSource: MutableInteractionSource): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, label = "pressScale")
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

@Composable
fun rememberInteractionSource() = remember { MutableInteractionSource() }

/**
 * Al enfocar un campo, desplaza también el botón de envío por encima del teclado
 * para que ni el campo ni la acción principal queden tapados.
 */
@Composable
fun Modifier.revealOnFocus(target: BringIntoViewRequester): Modifier {
    val scope = rememberCoroutineScope()
    return onFocusEvent { state ->
        if (state.isFocused) {
            scope.launch {
                delay(350) // espera la animación de apertura del teclado
                target.bringIntoView()
            }
        }
    }
}

fun formatNumber(value: Float, decimals: Int): String =
    String.format(Locale.US, "%.${decimals}f", value).replace('.', ',')

fun formatTime(millis: Long): String = SimpleDateFormat("HH:mm", Locale.forLanguageTag("es-CL")).format(Date(millis))

fun formatDate(millis: Long): String =
    SimpleDateFormat("d 'de' MMMM", Locale.forLanguageTag("es-CL")).format(Date(millis))
