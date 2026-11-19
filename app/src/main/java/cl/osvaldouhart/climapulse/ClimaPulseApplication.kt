package cl.osvaldouhart.climapulse

import android.app.Application
import androidx.credentials.CredentialManager
import cl.osvaldouhart.climapulse.data.AlertNotifier
import cl.osvaldouhart.climapulse.data.ConnectivityObserver
import cl.osvaldouhart.climapulse.data.EnvironmentRepository
import cl.osvaldouhart.climapulse.data.ReadingsStore
import cl.osvaldouhart.climapulse.data.SessionRepository
import cl.osvaldouhart.climapulse.data.WifiLink
import cl.osvaldouhart.climapulse.data.appDataStore
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ClimaPulseApplication : Application() {
    val container by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        // App Check: Firebase solo responde a esta app original, no a copias modificadas ni a scripts.
        // El proveedor cambia según el tipo de build (ver appCheckProviderFactory en debug y release).
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(appCheckProviderFactory())
    }
}

/** Contenedor de dependencias manual compartido por los ViewModels. */
class AppContainer(application: Application) {
    init {
        // El historial ahora vive en Firestore: se borra la base SQLite que usaba la versión anterior.
        application.deleteDatabase("climapulse.db")
    }

    val connectivity = ConnectivityObserver(application)
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    val readingsStore = ReadingsStore(auth, firestore)
    val alertNotifier = AlertNotifier(application)
    // El enlace vive en el contenedor para que la conexión siga activa al cambiar de pantalla.
    val wifiLink = WifiLink(application, readingsStore)
    val sessionRepository = SessionRepository(
        application.appDataStore,
        auth,
        firestore,
        readingsStore,
        CredentialManager.create(application)
    )
    val environmentRepository = EnvironmentRepository(application.appDataStore, readingsStore, wifiLink)
}
