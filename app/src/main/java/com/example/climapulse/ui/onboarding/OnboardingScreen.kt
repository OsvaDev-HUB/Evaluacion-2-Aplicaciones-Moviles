package com.example.climapulse.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.climapulse.R
import com.example.climapulse.data.SessionRepository
import com.example.climapulse.ui.appViewModelFactory
import com.example.climapulse.ui.components.ClimaLogo
import com.example.climapulse.ui.components.IconBadge
import com.example.climapulse.ui.components.PrimaryButton
import com.example.climapulse.ui.components.PulseRings
import com.example.climapulse.ui.components.SecondaryButton
import com.example.climapulse.ui.theme.ClimaShapes
import com.example.climapulse.data.Metric
import com.example.climapulse.ui.home.accent
import com.example.climapulse.ui.home.icon
import com.example.climapulse.ui.theme.Dimens
import com.example.climapulse.ui.theme.Teal
import com.example.climapulse.ui.theme.Yellow
import kotlinx.coroutines.launch

class OnboardingViewModel(private val session: SessionRepository) : ViewModel() {
    fun finish(notificationsGranted: Boolean, onDone: () -> Unit) {
        viewModelScope.launch {
            // Bandera persistida en DataStore: el onboarding no vuelve a mostrarse.
            session.completeOnboarding(notificationsGranted)
            onDone()
        }
    }

    companion object {
        val Factory = appViewModelFactory { container, _ -> OnboardingViewModel(container.sessionRepository) }
    }
}

private data class OnboardingPage(
    val title: String,
    val body: String,
    // Función porque el color cambia entre tema claro y oscuro.
    val accent: @Composable () -> Color,
    val illustration: @Composable () -> Unit
)

private val pages = listOf(
    OnboardingPage(
        title = "Tu ambiente, en un vistazo",
        body = "ClimaPulse mide temperatura, humedad y luz para que sepas al instante si tu espacio es cómodo.",
        accent = { Teal }
    ) { ClimaLogo(size = 112.dp) },
    OnboardingPage(
        title = "Sugerencias que sí sirven",
        body = "Revisa tendencias del día y recibe recomendaciones simples: ventilar, acercarte a la luz o ajustar la temperatura.",
        accent = { Yellow }
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Metric.entries.forEach { metric -> IconBadge(icon = metric.icon, tint = metric.accent, size = 60.dp) }
        }
    },
    OnboardingPage(
        title = "Te avisamos solo cuando importa",
        body = "Activa las notificaciones para enterarte cuando una lectura salga de tu rango ideal. Nada de spam: puedes cambiarlo en Ajustes.",
        accent = { Teal }
    ) { IconBadge(icon = Icons.Rounded.NotificationsActive, tint = Teal, size = 104.dp) }
)

@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    viewModel: OnboardingViewModel = viewModel(factory = OnboardingViewModel.Factory)
) {
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val isLastPage = pagerState.currentPage == pages.lastIndex

    // El permiso se pide en contexto, en la página que explica para qué sirve.
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> viewModel.finish(granted, onFinished) }

    fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.finish(notificationsGranted = true, onDone = onFinished)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .padding(horizontal = Dimens.ScreenPadding, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ClimaLogo(size = 32.dp)
            Spacer(Modifier.width(10.dp))
            Text("ClimaPulse", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (!isLastPage) {
                TextButton(onClick = { scope.launch { pagerState.animateScrollToPage(pages.lastIndex) } }) {
                    Text("Saltar")
                }
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) { index -> OnboardingPageContent(pages[index]) }

        PageIndicator(count = pages.size, current = pagerState.currentPage)
        Spacer(Modifier.height(24.dp))

        if (isLastPage) {
            PrimaryButton(text = "Permitir notificaciones", onClick = ::requestNotifications)
            Spacer(Modifier.height(8.dp))
            SecondaryButton(text = "Ahora no", onClick = { viewModel.finish(false, onFinished) })
        } else {
            PrimaryButton(
                text = "Siguiente",
                icon = Icons.AutoMirrored.Rounded.ArrowForward,
                onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } }
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun OnboardingPageContent(page: OnboardingPage) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        PulseRings(size = 260.dp, color = page.accent()) { page.illustration() }
        Spacer(Modifier.height(32.dp))
        Text(
            page.title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(Modifier.height(12.dp))
        Text(
            page.body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun PageIndicator(count: Int, current: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Paso ${current + 1} de $count" },
        horizontalArrangement = Arrangement.Center
    ) {
        repeat(count) { index ->
            val selected = index == current
            val width by animateDpAsState(if (selected) 28.dp else 8.dp, label = "dotWidth")
            val color by animateColorAsState(
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                label = "dotColor"
            )
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .size(width = width, height = 8.dp)
                    .clip(ClimaShapes.Pill)
                    .background(color)
            )
        }
    }
}
