package cl.osvaldouhart.climapulse

import android.app.Application
import cl.osvaldouhart.climapulse.data.AlertNotifier
import cl.osvaldouhart.climapulse.data.AppDatabase
import cl.osvaldouhart.climapulse.data.ConnectivityObserver
import cl.osvaldouhart.climapulse.data.EnvironmentRepository
import cl.osvaldouhart.climapulse.data.SessionRepository
import cl.osvaldouhart.climapulse.data.TokenCipher
import cl.osvaldouhart.climapulse.data.WifiLink
import cl.osvaldouhart.climapulse.data.appDataStore

class ClimaPulseApplication : Application() {
    val container by lazy { AppContainer(this) }
}

/** Contenedor de dependencias manual compartido por los ViewModels. */
class AppContainer(application: Application) {
    val connectivity = ConnectivityObserver(application)
    val database = AppDatabase(application)
    val alertNotifier = AlertNotifier(application)
    // El enlace vive en el contenedor para que la conexión siga activa al cambiar de pantalla.
    val wifiLink = WifiLink(application, database)
    val sessionRepository = SessionRepository(application.appDataStore, TokenCipher(), database)
    val environmentRepository = EnvironmentRepository(application.appDataStore, database, wifiLink)
}
