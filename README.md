# ClimaPulse

App Android para monitorear temperatura, humedad y luz en un ambiente. Hecha en Kotlin con Jetpack Compose y Material 3.

Sumativa Unidad 2 (TI3042): un **ESP8266 (NodeMCU)** con un sensor **DHT11** y un módulo **LDR** funciona como **nodo sensor** y envía lecturas reales por **WiFi**. La app Android funciona como **monitor**: recibe las lecturas, las guarda en la nube con Firebase y controla el sensor a distancia. Se pueden conectar hasta 3 teléfonos al mismo sensor.

## Problemática

Queremos saber si un espacio (sala, bodega, invernadero) tiene una temperatura, humedad y luz adecuadas sin tener que estar ahí. El nodo sensor mide el ambiente y el monitor muestra los datos, los guarda y puede encender un ventilador o una lámpara si los valores salen del rango ideal.

## Herramientas

| Herramienta | Para qué se usa | Por qué |
|---|---|---|
| Android Studio + Kotlin | Desarrollo de la app | Lenguaje oficial recomendado por Google para Android |
| Jetpack Compose + Material 3 | Interfaz | Estándar actual de diseño en Android |
| Firebase Authentication | Cuentas (correo y contraseña, o Google) | Firebase guarda las contraseñas, verifica el correo y limita los intentos fallidos |
| Credential Manager | "Continuar con Google" | Es la forma actual que recomienda Google para iniciar sesión en Android |
| Cloud Firestore | Perfil e historial de lecturas | Base de datos en la nube con copia local, así funciona sin internet |
| Firebase App Check | Proteger el backend | Firebase puede distinguir las peticiones de la app original de las de copias o scripts |
| ESP8266 + Arduino CLI | Nodo sensor (`sensores/sensores.ino`) | Tiene WiFi incluido y se programa igual que un Arduino |
| Sockets TCP + NSD/mDNS | Comunicación entre el sensor y la app | Funciona en la red WiFi local, sin internet y sin servicios externos |
| AES-GCM + PBKDF2 | Cifrado | Incluidos en Java/Android, no hace falta otra librería |
| Android Keystore + BiometricPrompt | Desbloqueo con huella | La llave queda en hardware seguro y solo se libera con una huella válida |
| R8 | Build release | Reduce, optimiza y ofusca el código (el APK baja de 27 MB a 4 MB) |
| DataStore | Preferencias | Reemplazo moderno de SharedPreferences |

## Comunicación inalámbrica: WiFi

Se eligió **WiFi local** en lugar de Bluetooth o MQTT:

- **Distancia**: WiFi llega a todo el edificio; Bluetooth alcanza unos 10 metros.
- **Estabilidad**: una conexión TCP avisa si se corta y la app lo muestra al usuario.
- **Consumo**: el sensor envía un mensaje pequeño cada 3 segundos.
- **Sin dependencias**: MQTT necesita un broker externo e internet; aquí basta con que el sensor y el teléfono estén en la misma red.

Cómo funciona (`sensores/sensores.ino` y `data/WifiLink.kt`):

1. El **ESP8266** se conecta al WiFi, abre el puerto `5050` y se anuncia en la red con mDNS (`_climapulse._tcp`). Su IP y su **PIN de 6 dígitos** aparecen en el monitor serie.
2. La **app** busca el sensor en la red (o se escribe la IP a mano) e ingresa el PIN.
3. El sensor envía una lectura cada 3 segundos. La app la muestra en tiempo real y guarda una cada 15 segundos en Firestore.
4. La app puede encender el **ventilador** (LED D0), la **lámpara** (LED D4) o **pausar** el envío. En esos pines irían los relés de un ventilador o una lámpara reales.
5. Si el sensor se reinicia o se corta la conexión, la app se reconecta sola cada 3 segundos.

Cada mensaje es un JSON cifrado que viaja en una línea de texto.

## Nodo sensor (ESP8266)

| Componente | Pin del NodeMCU |
|---|---|
| DHT11 (datos) | D1 |
| LDR, salida AO | A0 |
| VCC de los sensores | **3V3** (no usar 5V: A0 solo mide hasta 3.3 V) |
| GND | G |

Para cargar el programa:

1. Copia `sensores/config.example.h` como `sensores/config.h` y escribe el nombre y la clave del WiFi (2.4 GHz) y el PIN. Este archivo no se sube a git.
2. Compila y sube: `arduino-cli compile --fqbn esp8266:esp8266:nodemcuv2 -u -p /dev/ttyUSB0 sensores`
3. Abre el monitor serie a 115200 baudios para ver la IP, el PIN y las lecturas.

La luz se envía en una escala de 0 a 1000 (no son lux exactos). Si la luz sube al tapar el LDR, cambia `INVERTIR_LUZ` en el sketch.

## Login y autenticación: Firebase Authentication

- **Correo y contraseña**: al registrarse, Firebase envía un correo de verificación. La cuenta no entra al Inicio hasta abrir ese enlace (`ui/auth/VerifyEmailScreen.kt`).
- **Continuar con Google**: con Credential Manager se elige una cuenta de Google del teléfono (`ui/auth/GoogleSignIn.kt`). Si es la primera vez, se crea la cuenta. Google ya verificó el correo, así que entra directo.
- **Recuperar contraseña**: Firebase envía un enlace para crear una nueva. No se avisa si el correo no tiene cuenta, para que nadie pueda averiguar qué correos están registrados.
- **Cambiar contraseña** (Perfil → Seguridad): pide la contraseña actual antes de cambiarla. Solo aparece en cuentas con contraseña.
- **Eliminar cuenta** (Perfil → Seguridad): pide la contraseña o elegir de nuevo la cuenta de Google, y borra las lecturas, el perfil y la cuenta.
- La página para verificar el correo y cambiar la contraseña desde el enlace está en `web/accion/` (Firebase Hosting).

Todo esto está en `data/SessionRepository.kt`.

## Seguridad (ISO/IEC 27400)

| Medida | Dónde |
|---|---|
| Las contraseñas las maneja **Firebase Authentication**: nunca pasan por nuestra base de datos y Firebase limita los intentos fallidos | `data/SessionRepository.kt` |
| Correo verificado obligatorio antes de entrar | `ui/auth/VerifyEmailScreen.kt` |
| Regla de contraseña: 8 a 128 caracteres, sin repetir un solo carácter y sin las más comunes | `data/SessionRepository.kt` (`validateNewPassword`) |
| Acciones delicadas (cambiar contraseña, eliminar cuenta) piden confirmar la identidad de nuevo | `data/SessionRepository.kt` |
| **Reglas de Firestore**: cada usuario solo ve y cambia sus datos; se valida el tipo, el rango y el largo de cada campo; las lecturas no se pueden editar; todo lo demás está cerrado | `firestore.rules` |
| **App Check** (Play Integrity en release) para que Firebase reconozca la app original | `ClimaPulseApplication.kt`, `src/debug` y `src/release` |
| Huella con **CryptoObject**: una llave AES del Keystore que solo se libera con una huella fuerte. Si se agrega una huella nueva en Android, la llave se invalida | `data/BiometricKey.kt`, `ui/auth/Biometric.kt` |
| Enlace WiFi cifrado con **AES-GCM**; la llave sale del PIN (PBKDF2). Sin el PIN no se pueden leer ni enviar comandos | `data/LinkCrypto.kt`, `sensores/sensores.ino` |
| Build release con **R8** (código ofuscado) y firmado; la clave de firma y sus contraseñas no se suben a git | `app/build.gradle.kts`, `keystore.properties` en `.gitignore` |
| La clave del WiFi, el PIN del sensor y `google-services.json` no se suben a git | `.gitignore` |
| Respaldo automático desactivado (`allowBackup="false"`) | `AndroidManifest.xml` |

## Almacenamiento y consulta de datos

- **Perfil**: documento `users/{uid}` en Firestore con `nombre` y `telefono`.
- **Lecturas**: subcolección `users/{uid}/lecturas` con temperatura, humedad, luz, fecha y origen (el nombre del nodo sensor). Se guardan las 200 más recientes; las más antiguas se borran solas (`data/ReadingsStore.kt`).
- Firestore guarda una copia en el teléfono: el historial se puede ver y seguir llenando sin internet, y se sube cuando vuelve la conexión.
- Las preferencias (tema, alertas, huella) quedan en DataStore, en el teléfono.
- La pestaña **Historial** muestra promedio, mínimo, máximo y cada medición.
- La pestaña **Reportes** y el detalle de cada métrica usan los mismos datos.

## Cómo probarlo

1. Carga el programa en el ESP8266 (ver **Nodo sensor**) y anota la IP y el PIN del monitor serie.
2. Conecta el teléfono a la **misma red WiFi** que el ESP8266.
3. Instala la app y crea una cuenta (Regístrate y abre el enlace del correo) o usa **Continuar con Google**.
4. Pestaña **Sensor** → **Buscar sensor**. Elige "ClimaPulse ESP8266" de la lista (o escribe la IP), ingresa el PIN y toca **Conectar**.
5. La pestaña **Inicio** muestra las lecturas reales. Tapa el LDR o sopla el DHT11 y mira cómo cambian los valores.

Nota: algunas redes WiFi de universidades bloquean la conexión entre equipos o piden iniciar sesión en una página. En ese caso usa el punto de acceso (hotspot) de un teléfono en 2.4 GHz.

En un emulador la búsqueda automática no funciona: escribe la IP del sensor a mano (el PC debe estar en la misma red que el ESP8266).

## Abrir en Android Studio

1. Abre Android Studio.
2. Selecciona **Open** y elige la carpeta `ClimaPulse`.
3. Copia el archivo `google-services.json` del proyecto de Firebase en `app/` (no está en git).
4. Espera a que termine la sincronización de Gradle.
5. Ejecuta la configuración `app` en un emulador o dispositivo Android (Android 8.0 o superior).

## Firebase

- Proyecto: `climapulse-app-1112` (Firestore en `southamerica-west1`).
- Subir cambios de las reglas: `npx firebase-tools deploy --only firestore:rules`
- Para que "Continuar con Google" funcione, la huella SHA-1 de la clave que firma la app debe estar registrada en Firebase (Configuración del proyecto → Tus apps).
- **App Check** está en modo *monitoreo*: registra las peticiones pero no bloquea ninguna. Play Integrity solo entrega tokens válidos a apps instaladas desde Google Play, y la app se entrega como APK. Para bloquear, sube la app a Google Play (sirve una prueba interna) y en la consola ve a App Check → Firestore / Authentication → **Aplicar**.
- En builds debug, App Check usa un token de prueba que aparece en Logcat (etiqueta `DebugAppCheckProvider`). Hay que registrarlo en App Check → Administrar tokens de depuración.

## Build release

1. Crea `keystore.properties` en la raíz del proyecto (no se sube a git):
   ```
   storeFile=/ruta/a/climapulse-release.jks
   storePassword=...
   keyAlias=climapulse
   keyPassword=...
   ```
2. Genera el APK: `./gradlew assembleRelease` → `app/build/outputs/apk/release/app-release.apk`
3. Si cambias de un build debug a uno release en el mismo teléfono, desinstala antes la app (están firmadas con claves distintas).

Si falta `keystore.properties`, el APK release sale sin firmar. Las reglas de R8 propias están en `app/src/main/keepRules/climapulse.keep`.

## Pruebas

**Pruebas unitarias** (reglas de contraseña, cifrado del enlace WiFi y modelos):

```
./gradlew testDebugUnitTest
```

**Pruebas de las reglas de Firestore** con el emulador de Firebase (necesita Node.js y Java):

```
cd firebase/tests
npm install
npm test
```

Revisan que un usuario no pueda leer ni cambiar los datos de otro, que sin sesión no se pueda leer nada, que se rechacen valores fuera de rango, fechas del futuro y campos extra, y que una lectura guardada no se pueda editar.

## Pantallas

| Pantalla | Dónde |
|---|---|
| Splash + Router | `MainActivity.kt`, `MainViewModel.kt` |
| Onboarding | `ui/onboarding/` |
| Login / Registro / Recuperación + huella | `ui/auth/` |
| Inicio con lecturas en tiempo real | `ui/home/DashboardScreen.kt` |
| Enlace WiFi con el sensor y control remoto | `ui/home/SensorScreen.kt`, `ui/home/SensorViewModel.kt` |
| Historial (Firestore) | `ui/history/` |
| Reportes | `ui/home/ReportsScreen.kt` |
| Detalle de métrica y rango ideal | `ui/detail/` |
| Perfil / Ajustes / Términos / Cerrar sesión | `ui/settings/` |
| Cambiar contraseña / Eliminar cuenta | `ui/settings/AccountScreens.kt` |

## Notas

- Las lecturas son **reales** y vienen del ESP8266. Sin un sensor conectado, la app muestra la última lectura guardada marcada como "Sin conexión".
- **Al abrir la app**: primero el logo (Splash, al menos 1,2 s) y luego la **bienvenida** de 3 páginas. La bienvenida sale en cada apertura, salvo que se marque "No volver a mostrar"; se puede reactivar en Perfil → Ayuda. Después sigue al login, o directo al Inicio si la sesión está abierta.
- La ventana de permisos y el tutorial aparecen solo en el **primer ingreso de cada usuario** (por ejemplo, justo después de registrarse). La bandera se guarda por cuenta en DataStore (`intro_done_<uid>`). El tutorial se puede repetir desde Perfil → Ayuda.
- **Alertas**: si el permiso de notificaciones está concedido y "Alertas de ambiente" está activado en Perfil, llega una notificación cuando el ambiente pasa de cómodo a fuera de rango (`data/AlertNotifier.kt`). Funciona mientras la app está abierta o en segundo plano reciente; no hay un servicio que siga midiendo con la app cerrada.
- En Perfil → Apariencia se elige el tema: **Sistema**, **Claro** u **Oscuro**. La elección queda guardada.
- La app se muestra en **pantalla completa**: las barras del sistema se ocultan y aparecen un momento al deslizar desde el borde.
