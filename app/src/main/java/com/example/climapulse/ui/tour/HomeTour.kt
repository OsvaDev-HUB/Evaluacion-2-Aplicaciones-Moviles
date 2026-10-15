package com.example.climapulse.ui.tour

/** Claves de los elementos que recorre el tour del Home. */
object TourKeys {
    const val HERO = "tour_hero"
    const val METRICS = "tour_metrics"
    const val REFRESH = "tour_refresh"
    const val SAVE = "tour_save"
    const val NAVIGATION = "tour_navigation"
}

val homeTourSteps = listOf(
    TourStep(
        key = TourKeys.HERO,
        title = "Tu ambiente de un vistazo",
        body = "Aquí ves si tu espacio está cómodo y la temperatura actual. Tócala para ver el detalle."
    ),
    TourStep(
        key = TourKeys.METRICS,
        title = "Humedad y luz",
        body = "Cada tarjeta indica si la lectura está en tu rango ideal. Toca una para ajustar ese rango."
    ),
    TourStep(
        key = TourKeys.REFRESH,
        title = "Actualiza cuando quieras",
        body = "Pide una lectura nueva al instante. Además, la app se actualiza sola cada 30 segundos."
    ),
    TourStep(
        key = TourKeys.SAVE,
        title = "Guarda tus mediciones",
        body = "Cada medición guardada alimenta el gráfico de tendencia y tu historial."
    ),
    TourStep(
        key = TourKeys.NAVIGATION,
        title = "Explora ClimaPulse",
        body = "Revisa tu historial, conecta un sensor, genera reportes y gestiona tu perfil desde aquí. Puedes repetir este tutorial en Perfil."
    )
)
