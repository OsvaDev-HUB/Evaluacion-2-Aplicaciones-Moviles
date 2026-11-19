package cl.osvaldouhart.climapulse.ui.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import cl.osvaldouhart.climapulse.R
import cl.osvaldouhart.climapulse.data.AuthException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException

// Abre el selector de cuentas de Google con Credential Manager y devuelve el token de la cuenta elegida.
// Devuelve null si el usuario cierra el selector sin elegir nada.
suspend fun requestGoogleIdToken(activityContext: Context): String? {
    // default_web_client_id lo genera el plugin de google-services a partir de google-services.json
    val option = GetSignInWithGoogleOption.Builder(activityContext.getString(R.string.default_web_client_id)).build()
    val request = GetCredentialRequest.Builder().addCredentialOption(option).build()

    val credential = try {
        CredentialManager.create(activityContext).getCredential(activityContext, request).credential
    } catch (e: GetCredentialCancellationException) {
        return null
    } catch (e: NoCredentialException) {
        throw AuthException("No hay cuentas de Google en este teléfono. Agrega una en Ajustes e intenta de nuevo.")
    } catch (e: GetCredentialException) {
        throw AuthException("No pudimos abrir el inicio con Google. Intenta de nuevo en un momento.")
    }

    if (credential !is CustomCredential || credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
        throw AuthException("No pudimos leer la cuenta de Google. Intenta de nuevo.")
    }
    return try {
        GoogleIdTokenCredential.createFrom(credential.data).idToken
    } catch (e: GoogleIdTokenParsingException) {
        throw AuthException("No pudimos leer la cuenta de Google. Intenta de nuevo.")
    }
}
