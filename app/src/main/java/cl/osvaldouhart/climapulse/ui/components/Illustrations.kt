package cl.osvaldouhart.climapulse.ui.components

import android.provider.Settings
import androidx.annotation.DrawableRes
import androidx.compose.animation.graphics.ExperimentalAnimationGraphicsApi
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cl.osvaldouhart.climapulse.R
import cl.osvaldouhart.climapulse.ui.theme.LocalDarkTheme
import kotlinx.coroutines.delay

// Ilustraciones con versión clara y oscura. Los SVG originales están en docs/ilustraciones
// y se convierten con docs/ilustraciones/convertir.py, que también genera las animaciones (avd_*).
// cycleMillis es lo que dura una pasada de la animación; debe coincidir con la tabla ANIMACIONES.
enum class Illustration(
    @param:DrawableRes val light: Int,
    @param:DrawableRes val dark: Int,
    @param:DrawableRes val animatedLight: Int? = null,
    @param:DrawableRes val animatedDark: Int? = null,
    val cycleMillis: Long = 0
) {
    SENSOR_SEARCH(
        R.drawable.ill_sensor_search, R.drawable.ill_sensor_search_dark,
        R.drawable.avd_ill_sensor_search, R.drawable.avd_ill_sensor_search_dark, 1_100
    ),
    ONBOARDING_ALERT(
        R.drawable.ill_onboarding_alert, R.drawable.ill_onboarding_alert_dark,
        R.drawable.avd_ill_onboarding_alert, R.drawable.avd_ill_onboarding_alert_dark, 1_800
    ),
    ONBOARDING_LINK(
        R.drawable.ill_onboarding_link, R.drawable.ill_onboarding_link_dark,
        R.drawable.avd_ill_onboarding_link, R.drawable.avd_ill_onboarding_link_dark, 1_400
    ),
    EMAIL_VERIFY(
        R.drawable.ill_email_verify, R.drawable.ill_email_verify_dark,
        R.drawable.avd_ill_email_verify, R.drawable.avd_ill_email_verify_dark, 1_550
    ),
    EMAIL_SENT(
        R.drawable.ill_email_sent, R.drawable.ill_email_sent_dark,
        R.drawable.avd_ill_email_sent, R.drawable.avd_ill_email_sent_dark, 1_200
    ),
    REPORTS_EMPTY(
        R.drawable.ill_reports_empty, R.drawable.ill_reports_empty_dark,
        R.drawable.avd_ill_reports_empty, R.drawable.avd_ill_reports_empty_dark, 2_200
    ),
    DELETE_ACCOUNT(R.drawable.ill_delete_account, R.drawable.ill_delete_account_dark),
    OFFLINE(R.drawable.ill_offline, R.drawable.ill_offline_dark),
    EMPTY_HISTORY(R.drawable.ill_empty_history, R.drawable.ill_empty_history_dark)
}

// Pausa entre una pasada y la siguiente, para que el movimiento no canse.
private const val PAUSE_MILLIS = 1_400L

@Composable
fun illustrationPainter(illustration: Illustration): Painter =
    painterResource(if (LocalDarkTheme.current) illustration.dark else illustration.light)

// Son decorativas: el texto de la pantalla ya explica lo mismo, así que no se leen en voz alta.
// forceLight usa siempre la versión clara (para dibujarlas sobre formas claras, como en la bienvenida).
@Composable
fun IllustrationImage(
    illustration: Illustration,
    modifier: Modifier = Modifier,
    size: Dp = 160.dp,
    forceLight: Boolean = false
) {
    val dark = LocalDarkTheme.current && !forceLight
    val animated = if (dark) illustration.animatedDark else illustration.animatedLight
    if (animated != null && animationsEnabled()) {
        LoopingIllustration(animated, illustration.cycleMillis, modifier.size(size))
    } else {
        Image(
            painterResource(if (dark) illustration.dark else illustration.light),
            contentDescription = null,
            modifier = modifier.size(size)
        )
    }
}

// Reproduce la animación hacia adelante y luego hacia atrás, con una pausa entre cada pasada.
// Como cada animación vuelve a su estado inicial, las dos direcciones se ven igual de naturales.
@OptIn(ExperimentalAnimationGraphicsApi::class)
@Composable
private fun LoopingIllustration(@DrawableRes resource: Int, cycleMillis: Long, modifier: Modifier) {
    val image = AnimatedImageVector.animatedVectorResource(resource)
    var atEnd by remember { mutableStateOf(false) }
    LaunchedEffect(resource) {
        delay(400)
        while (true) {
            atEnd = !atEnd
            delay(cycleMillis + PAUSE_MILLIS)
        }
    }
    Image(rememberAnimatedVectorPainter(image, atEnd), contentDescription = null, modifier = modifier)
}

// Respeta la opción de Android "Quitar animaciones" (accesibilidad o ajustes de desarrollador).
@Composable
fun animationsEnabled(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }
}
