package com.example.climapulse

import android.app.Application
import com.example.climapulse.data.AlertNotifier
import com.example.climapulse.data.AppDatabase
import com.example.climapulse.data.ConnectivityObserver
import com.example.climapulse.data.EnvironmentRepository
import com.example.climapulse.data.ReadingSimulator
import com.example.climapulse.data.SessionRepository
import com.example.climapulse.data.TokenCipher
import com.example.climapulse.data.WifiLink
import com.example.climapulse.data.appDataStore

class ClimaPulseApplication : Application() {
    val container by lazy { AppContainer(this) }
}

/** Contenedor de dependencias manual compartido por los ViewModels. */
class AppContainer(application: Application) {
    val connectivity = ConnectivityObserver(application)
    val database = AppDatabase(application)
    val simulator = ReadingSimulator()
    val alertNotifier = AlertNotifier(application)
    // El enlace vive en el contenedor para que la conexión siga activa al cambiar de pantalla.
    val wifiLink = WifiLink(application, database, simulator)
    val sessionRepository = SessionRepository(application.appDataStore, TokenCipher(), database)
    val environmentRepository = EnvironmentRepository(
        application, application.appDataStore, connectivity, database, wifiLink, simulator
    )
}
