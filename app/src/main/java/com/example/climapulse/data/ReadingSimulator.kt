package com.example.climapulse.data

import kotlin.math.cos
import kotlin.math.sin

/**
 * Simula un sensor de temperatura, humedad y luz. Los actuadores remotos
 * (ventilador y lámpara) cambian los valores para que el control se note en el monitor.
 */
class ReadingSimulator {
    private var phase = 0f

    fun next(actuators: Actuators = Actuators(), origin: String = ORIGIN_LOCAL): EnvironmentReading {
        phase += 0.42f
        var temperature = 22.6f + sin(phase) * 1.1f
        var humidity = 54f + cos(phase * 0.75f) * 4f
        var light = 480f + sin(phase * 0.5f) * 120f

        // El ventilador enfría y seca el ambiente; la lámpara sube la luz.
        if (actuators.fan) {
            temperature -= 2.5f
            humidity -= 6f
        }
        if (actuators.lamp) {
            light += 350f
        }

        return EnvironmentReading(
            temperature = temperature,
            humidity = humidity,
            light = light,
            timestampMillis = System.currentTimeMillis(),
            origin = origin
        )
    }

    companion object {
        const val ORIGIN_LOCAL = "Este teléfono"
    }
}
