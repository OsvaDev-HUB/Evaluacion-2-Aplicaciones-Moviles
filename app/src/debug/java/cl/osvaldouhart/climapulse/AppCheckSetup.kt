package cl.osvaldouhart.climapulse

import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

// En debug se usa un token de prueba: al abrir la app sale en Logcat (etiqueta DebugAppCheckProvider)
// y hay que registrarlo en la consola de Firebase, en App Check > Administrar tokens de depuración.
fun appCheckProviderFactory(): AppCheckProviderFactory = DebugAppCheckProviderFactory.getInstance()
