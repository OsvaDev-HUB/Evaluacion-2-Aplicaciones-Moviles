package com.example.climapulse.ui.tour

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateRectAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.climapulse.ui.theme.ClimaShapes
import com.example.climapulse.ui.theme.NavyDeep
import kotlin.math.roundToInt

/** Un paso del tour: qué elemento iluminar ([key]) y qué explicar. */
data class TourStep(val key: String, val title: String, val body: String)

/**
 * Estado del tour guiado (coach marks). Los elementos se registran con [tourTarget]
 * y el [TourOverlay] ilumina el del paso actual.
 */
@Stable
class TourController {
    internal val targets = mutableStateMapOf<String, Rect>()
    internal val requesters = mutableMapOf<String, BringIntoViewRequester>()

    var steps by mutableStateOf<List<TourStep>>(emptyList())
        private set
    var index by mutableIntStateOf(-1)
        private set

    val isActive: Boolean get() = index in steps.indices
    val current: TourStep? get() = steps.getOrNull(index)

    fun start(steps: List<TourStep>) {
        this.steps = steps
        index = 0
    }

    fun next() {
        index++
    }

    fun stop() {
        index = -1
    }
}

val LocalTour = staticCompositionLocalOf<TourController?> { null }

/** Marca un elemento como objetivo del tour. Sin tour activo no hace nada. */
@Composable
fun Modifier.tourTarget(key: String): Modifier {
    val tour = LocalTour.current ?: return this
    val requester = remember { BringIntoViewRequester() }
    DisposableEffect(key) {
        tour.requesters[key] = requester
        onDispose {
            tour.requesters.remove(key)
            tour.targets.remove(key)
        }
    }
    return bringIntoViewRequester(requester)
        .onGloballyPositioned { tour.targets[key] = it.boundsInWindow() }
}

/**
 * Capa del tour: oscurece la pantalla, recorta un "spotlight" animado sobre el elemento
 * del paso actual y muestra una tarjeta con la explicación y el progreso.
 */
@Composable
fun TourOverlay(controller: TourController, onFinished: () -> Unit) {
    val step = controller.current ?: return
    val density = LocalDensity.current
    var overlayOrigin by remember { mutableStateOf(Offset.Zero) }
    var overlaySize by remember { mutableStateOf(IntSize.Zero) }
    var cardHeight by remember { mutableIntStateOf(0) }

    fun finish() {
        controller.stop()
        onFinished()
    }

    // Si el elemento está fuera de pantalla, se desplaza hasta él antes de iluminarlo.
    LaunchedEffect(step.key) { controller.requesters[step.key]?.bringIntoView() }
    BackHandler { finish() }

    val padding = with(density) { 8.dp.toPx() }
    val edge = with(density) { 4.dp.toPx() }
    val rawTarget = controller.targets[step.key]
        ?.translate(-overlayOrigin)
        ?.inflate(padding)
        // Que el recorte no se salga de la pantalla (ej. la barra inferior).
        ?.let {
            Rect(
                left = it.left.coerceAtLeast(edge),
                top = it.top.coerceAtLeast(edge),
                right = it.right.coerceAtMost(overlaySize.width - edge),
                bottom = it.bottom.coerceAtMost(overlaySize.height - edge)
            )
        }
        ?: Rect(Offset(overlaySize.width / 2f, overlaySize.height / 2f), Size.Zero)
    val spotlight by animateRectAsState(rawTarget, tween(450), label = "spotlight")
    val pulse by rememberInfiniteTransition(label = "tourPulse").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart),
        label = "tourPulseProgress"
    )
    val ringColor = MaterialTheme.colorScheme.primary
    // Esquinas de 24dp, o círculo completo si el elemento es pequeño (ej. botón redondo).
    val shortSide = minOf(spotlight.width, spotlight.height)
    val corner = if (shortSide < with(density) { 80.dp.toPx() }) shortSide / 2f else with(density) { 24.dp.toPx() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned {
                overlayOrigin = it.positionInWindow()
                overlaySize = it.size
            }
            // Bloquea los toques al contenido de abajo mientras el tour está activo.
            .pointerInput(Unit) { detectTapGestures { } }
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
        ) {
            drawRect(NavyDeep.copy(alpha = 0.82f))
            drawRoundRect(
                color = NavyDeep,
                topLeft = spotlight.topLeft,
                size = spotlight.size,
                cornerRadius = CornerRadius(corner),
                blendMode = BlendMode.Clear
            )
            drawRoundRect(
                color = ringColor,
                topLeft = spotlight.topLeft,
                size = spotlight.size,
                cornerRadius = CornerRadius(corner),
                style = Stroke(width = 2.dp.toPx())
            )
            // Halo que "late", igual que los anillos del logo.
            val grow = 14.dp.toPx() * pulse
            drawRoundRect(
                color = ringColor.copy(alpha = 0.5f * (1f - pulse)),
                topLeft = spotlight.topLeft - Offset(grow, grow),
                size = Size(spotlight.width + grow * 2, spotlight.height + grow * 2),
                cornerRadius = CornerRadius(corner + grow),
                style = Stroke(width = 2.dp.toPx())
            )
        }

        // La tarjeta va debajo del elemento si cabe; si no, encima.
        val gap = with(density) { 20.dp.toPx() }
        val margin = with(density) { 24.dp.toPx() }
        val fitsBelow = rawTarget.bottom + gap + cardHeight < overlaySize.height - margin
        val targetY = if (fitsBelow) rawTarget.bottom + gap else rawTarget.top - gap - cardHeight
        val cardY by animateFloatAsState(
            targetY.coerceIn(margin, (overlaySize.height - cardHeight - margin).coerceAtLeast(margin)),
            tween(450),
            label = "cardY"
        )

        TourCard(
            step = step,
            index = controller.index,
            total = controller.steps.size,
            onNext = { if (controller.index == controller.steps.lastIndex) finish() else controller.next() },
            onSkip = ::finish,
            modifier = Modifier
                .offset { IntOffset(0, cardY.roundToInt()) }
                .padding(horizontal = 20.dp)
                .onSizeChanged { cardHeight = it.height }
                .alpha(if (cardHeight == 0) 0f else 1f)
        )
    }
}

@Composable
private fun TourCard(
    step: TourStep,
    index: Int,
    total: Int,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isLast = index == total - 1
    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                paneTitle = "Tutorial"
                liveRegion = LiveRegionMode.Polite
            },
        shape = ClimaShapes.ExtraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Paso ${index + 1} de $total",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Text(step.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            Text(step.body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.size(4.dp))
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                StepDots(index = index, total = total, modifier = Modifier.weight(1f))
                if (!isLast) {
                    TextButton(onClick = onSkip) { Text("Saltar") }
                }
                Button(onClick = onNext) {
                    Text(if (isLast) "¡Entendido!" else "Siguiente")
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Icon(
                        if (isLast) Icons.Rounded.Check else Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize)
                    )
                }
            }
        }
    }
}

@Composable
private fun StepDots(index: Int, total: Int, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(total) { i ->
            val width by animateDpAsState(if (i == index) 20.dp else 6.dp, label = "tourDot")
            val color by animateColorAsState(
                if (i <= index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                label = "tourDotColor"
            )
            Box(
                Modifier
                    .size(width = width, height = 6.dp)
                    .clip(ClimaShapes.Pill)
                    .background(color)
            )
        }
    }
}
