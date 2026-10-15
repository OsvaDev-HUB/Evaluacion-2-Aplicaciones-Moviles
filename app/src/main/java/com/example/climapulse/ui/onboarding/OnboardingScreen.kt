package com.example.climapulse.ui.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.climapulse.data.SessionRepository
import com.example.climapulse.data.StartDestination
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import com.example.climapulse.ui.appViewModelFactory
import com.example.climapulse.ui.components.ClimaLogo
import com.example.climapulse.ui.components.CookieShape
import com.example.climapulse.ui.components.PrimaryButton
import com.example.climapulse.ui.theme.ClimaShapes
import com.example.climapulse.ui.theme.Dimens
import kotlinx.coroutines.launch

class OnboardingViewModel(private val session: SessionRepository) : ViewModel() {
    // Casilla "No volver a mostrar".
    var hideNextTime by mutableStateOf(false)

    /** Guarda la casilla y avisa a dónde seguir (login o Inicio). */
    fun finish(onDone: (StartDestination) -> Unit) {
        viewModelScope.launch {
            session.setWelcomeHidden(hideNextTime)
            onDone(session.resolveAfterWelcome())
        }
    }

    companion object {
        val Factory = appViewModelFactory { container, _ -> OnboardingViewModel(container.sessionRepository) }
    }
}

/** Datos de cada página. La forma de fondo cambia de figura y color según la página. */
private data class OnboardingPage(
    val title: String,
    val body: String,
    val lobes: Int,
    val depth: Float,
    val illustration: @Composable () -> Unit
)

private val pages = listOf(
    OnboardingPage(
        title = "Tu espacio, en un solo vistazo",
        body = "Temperatura, humedad y luz en tiempo real. Un teléfono mide y otro lo monitorea por WiFi.",
        lobes = 9,
        depth = 0.06f
    ) { ClimaLogo(size = 112.dp) },
    OnboardingPage(
        title = "Sabes cuándo actuar",
        body = "Si una lectura sale del rango ideal, la forma se vuelve amarilla y te sugerimos qué hacer: ventilar, encender la lámpara o pausar.",
        lobes = 5,
        depth = 0.09f
    ) {
        Icon(
            Icons.Rounded.WarningAmber,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.size(88.dp)
        )
    },
    OnboardingPage(
        title = "Dos teléfonos, un sistema",
        body = "Conéctalos con un PIN de 6 dígitos. Las lecturas viajan cifradas y quedan guardadas en el teléfono.",
        lobes = 12,
        depth = 0.05f
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Sensors, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(52.dp))
            Icon(Icons.Rounded.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 8.dp).size(28.dp))
            Icon(Icons.Rounded.PhoneAndroid, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(52.dp))
        }
    }
)

/** Bienvenida: aparece después del splash en cada apertura, salvo que se marque "No volver a mostrar". */
@Composable
fun OnboardingScreen(
    onFinished: (StartDestination) -> Unit,
    viewModel: OnboardingViewModel = viewModel(factory = OnboardingViewModel.Factory)
) {
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val currentPage = pagerState.currentPage
    val isLastPage = currentPage == pages.lastIndex
    val colors = MaterialTheme.colorScheme

    // Color de la forma por página: turquesa (todo bien), amarillo (revisar), verde gris (enlace).
    val shapeColor by animateColorAsState(
        when (currentPage) {
            0 -> colors.primaryContainer
            1 -> colors.tertiaryContainer
            else -> colors.secondaryContainer
        },
        label = "shapeColor"
    )
    val depth by animateFloatAsState(pages[currentPage].depth, spring(dampingRatio = 0.5f, stiffness = 120f), label = "shapeDepth")
    // La forma gira mientras se desliza el dedo entre páginas.
    val rotation = (currentPage + pagerState.currentPageOffsetFraction) * 40f

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .safeDrawingPadding()
            .padding(horizontal = Dimens.ScreenPadding, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
            horizontalArrangement = Arrangement.End
        ) {
            if (!isLastPage) {
                TextButton(onClick = { viewModel.finish(onFinished) }) { Text("Saltar") }
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            // Capa de atrás: una sola forma que cambia de figura y color.
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp)
                    .fillMaxWidth(0.78f)
                    .aspectRatio(1f)
                    .clip(CookieShape(lobes = pages[currentPage].lobes, depth = depth, rotationDegrees = rotation))
                    .background(shapeColor)
            )
            // Capa de adelante: ilustración y textos de cada página.
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { index ->
                OnboardingPageContent(pages[index])
            }
        }

        PageIndicator(count = pages.size, current = currentPage)
        Spacer(Modifier.height(12.dp))
        // Toda la fila marca o desmarca la casilla (área táctil grande).
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .toggleable(
                    value = viewModel.hideNextTime,
                    role = Role.Checkbox,
                    onValueChange = { viewModel.hideNextTime = it }
                ),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = viewModel.hideNextTime, onCheckedChange = null)
            Spacer(Modifier.width(8.dp))
            Text("No volver a mostrar", style = MaterialTheme.typography.bodyLarge)
        }
        Spacer(Modifier.height(8.dp))
        if (isLastPage) {
            PrimaryButton(text = "Comenzar", onClick = { viewModel.finish(onFinished) })
        } else {
            PrimaryButton(
                text = "Siguiente",
                icon = Icons.AutoMirrored.Rounded.ArrowForward,
                onClick = { scope.launch { pagerState.animateScrollToPage(currentPage + 1) } }
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun OnboardingPageContent(page: OnboardingPage) {
    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        // Mismo tamaño y posición que la forma de atrás, para que la ilustración quede centrada.
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 16.dp)
                .fillMaxWidth(0.78f)
                .aspectRatio(1f),
            contentAlignment = Alignment.Center
        ) { page.illustration() }
        Spacer(Modifier.weight(1f))
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
        Spacer(Modifier.height(24.dp))
    }
}

/** Puntos de avance, centrados como el texto. */
@Composable
private fun PageIndicator(count: Int, current: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Página ${current + 1} de $count" },
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
