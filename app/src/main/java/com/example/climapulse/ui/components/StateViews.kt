package com.example.climapulse.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.climapulse.R
import com.example.climapulse.ui.theme.ClimaShapes
import com.example.climapulse.ui.theme.Dimens

// ---------- Loading: skeleton screens con brillo (shimmer) ----------

@Composable
fun SkeletonBox(modifier: Modifier = Modifier, shape: Shape = ClimaShapes.Medium) {
    val base = MaterialTheme.colorScheme.surfaceContainerHigh
    val highlight = MaterialTheme.colorScheme.surfaceContainerHighest
    val x by rememberInfiniteTransition(label = "shimmer").animateFloat(
        initialValue = -600f,
        targetValue = 1400f,
        animationSpec = infiniteRepeatable(tween(1300, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmerX"
    )
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.linearGradient(
                    colors = listOf(base, highlight, base),
                    start = Offset(x, 0f),
                    end = Offset(x + 600f, 300f)
                )
            )
    )
}

@Composable
fun DashboardSkeleton(contentPadding: PaddingValues) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(Dimens.ScreenPadding)
            .semantics { contentDescription = "Cargando lecturas" },
        verticalArrangement = Arrangement.spacedBy(Dimens.ItemGap)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SkeletonBox(Modifier.size(44.dp), CircleShape)
            Spacer(Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SkeletonBox(Modifier.size(width = 140.dp, height = 18.dp), ClimaShapes.Pill)
                SkeletonBox(Modifier.size(width = 200.dp, height = 12.dp), ClimaShapes.Pill)
            }
        }
        Spacer(Modifier.height(8.dp))
        SkeletonBox(Modifier.fillMaxWidth().height(210.dp), ClimaShapes.ExtraLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.ItemGap)) {
            SkeletonBox(Modifier.weight(1f).height(150.dp), ClimaShapes.Large)
            SkeletonBox(Modifier.weight(1f).height(150.dp), ClimaShapes.Large)
        }
        SkeletonBox(Modifier.fillMaxWidth().height(56.dp), ClimaShapes.Pill)
        SkeletonBox(Modifier.fillMaxWidth().height(180.dp), ClimaShapes.Large)
    }
}

@Composable
fun ListSkeleton(rows: Int = 5) {
    Column(
        verticalArrangement = Arrangement.spacedBy(Dimens.ItemGap),
        modifier = Modifier.semantics { contentDescription = "Cargando historial" }
    ) {
        SkeletonBox(Modifier.fillMaxWidth().height(140.dp), ClimaShapes.Large)
        repeat(rows) { SkeletonBox(Modifier.fillMaxWidth().height(72.dp), ClimaShapes.Large) }
    }
}

// ---------- Empty & Error ----------

@Composable
fun EmptyState(
    title: String,
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    illustration: Painter = painterResource(R.drawable.ill_empty_history),
    loading: Boolean = false
) {
    StateMessage(illustration, title, message, modifier) {
        PrimaryButton(text = actionLabel, onClick = onAction, loading = loading)
    }
}

/** Error amigable: mensaje humano y botón de reintento, nunca un código crudo. */
@Composable
fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Algo no salió como esperábamos"
) {
    StateMessage(painterResource(R.drawable.ill_offline), title, message, modifier) {
        PrimaryButton(text = "Reintentar", onClick = onRetry, icon = Icons.Rounded.Refresh)
    }
}

@Composable
private fun StateMessage(
    illustration: Painter,
    title: String,
    message: String,
    modifier: Modifier,
    action: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(illustration, contentDescription = null, modifier = Modifier.size(170.dp))
        Spacer(Modifier.height(24.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(28.dp))
        action()
    }
}

// ---------- Offline ----------

@Composable
fun OfflineBanner(visible: Boolean) {
    AnimatedVisibility(visible = visible, enter = expandVertically(), exit = shrinkVertically()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.ScreenPadding, vertical = 8.dp)
                .clip(ClimaShapes.Pill)
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Rounded.WifiOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "Sin conexión · los datos pueden no estar al día",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}
