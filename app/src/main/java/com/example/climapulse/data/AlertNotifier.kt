package com.example.climapulse.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.climapulse.MainActivity
import com.example.climapulse.R

/**
 * Publica la alerta "Revisa el ambiente" cuando una lectura sale del rango ideal.
 * Solo avisa si el usuario dio el permiso de notificaciones.
 */
class AlertNotifier(private val context: Context) {

    init {
        // Desde Android 8 cada notificación necesita un canal.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Alertas de ambiente",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Avisos cuando una lectura sale del rango ideal" }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    fun canNotify(): Boolean {
        val permissionOk = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return permissionOk && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun showOutOfRange(message: String) {
        if (!canNotify()) return
        // Al tocar la notificación se abre la app.
        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Revisa el ambiente")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // El permiso se quitó justo ahora: no se muestra nada.
        }
    }

    private companion object {
        const val CHANNEL_ID = "alertas_ambiente"
        const val NOTIFICATION_ID = 1
    }
}
