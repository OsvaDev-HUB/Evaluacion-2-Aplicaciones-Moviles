package cl.osvaldouhart.climapulse.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cl.osvaldouhart.climapulse.R
import cl.osvaldouhart.climapulse.ui.theme.LocalDarkTheme

// Ilustraciones con versión clara y oscura. Los SVG originales están en docs/ilustraciones
// y se convierten con docs/ilustraciones/convertir.py.
enum class Illustration(@param:DrawableRes val light: Int, @param:DrawableRes val dark: Int) {
    SENSOR_SEARCH(R.drawable.ill_sensor_search, R.drawable.ill_sensor_search_dark),
    ONBOARDING_ALERT(R.drawable.ill_onboarding_alert, R.drawable.ill_onboarding_alert_dark),
    ONBOARDING_LINK(R.drawable.ill_onboarding_link, R.drawable.ill_onboarding_link_dark),
    EMAIL_VERIFY(R.drawable.ill_email_verify, R.drawable.ill_email_verify_dark),
    EMAIL_SENT(R.drawable.ill_email_sent, R.drawable.ill_email_sent_dark),
    REPORTS_EMPTY(R.drawable.ill_reports_empty, R.drawable.ill_reports_empty_dark),
    DELETE_ACCOUNT(R.drawable.ill_delete_account, R.drawable.ill_delete_account_dark)
}

@Composable
fun illustrationPainter(illustration: Illustration): Painter =
    painterResource(if (LocalDarkTheme.current) illustration.dark else illustration.light)

// Son decorativas: el texto de la pantalla ya explica lo mismo, así que no se leen en voz alta.
@Composable
fun IllustrationImage(illustration: Illustration, modifier: Modifier = Modifier, size: Dp = 160.dp) {
    Image(illustrationPainter(illustration), contentDescription = null, modifier = modifier.size(size))
}
