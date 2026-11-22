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
| ArduinoJson 7 | Leer y armar los mensajes en el ESP8266 | Revisa que cada mensaje sea un JSON válido en vez de buscar texto a mano |
| Sockets TCP + NSD/mDNS | Comunicación entre el sensor y la app | Funciona en la red WiFi local, sin internet y sin servicios externos |
| AES-GCM + PBKDF2 | Cifrado | Incluidos en Java/Android, no hace falta otra librería |
| Android Keystore + BiometricPrompt | Desbloqueo con huella | La llave queda en hardware seguro y solo se libera con una huella válida |
| R8 | Build release | Reduce, optimiza y ofusca el código (el APK baja de 27 MB a 4 MB) |
| DataStore | Preferencias | Reemplazo moderno de SharedPreferences |

## Escala de apreciación (evaluación 3)

Dónde está cada indicador. El enlace es por WiFi (sockets TCP), que el profesor aceptó como comunicación serial.

| Código | Indicador | Dónde se cumple |
|---|---|---|
| 3.1.1.1 | Envío de información seguro | AES-256-GCM con llave PBKDF2 de la clave del sensor (10 a 32 caracteres, revisada en la app y al compilar), reto + sesión + contador contra mensajes repetidos (`data/LinkCrypto.kt`, `data/LinkSession.kt`, `sensores/sensores.ino`). Ver [Protocolo](#protocolo-del-enlace-versión-2) y [Seguridad](#seguridad-isoiec-27400) |
| 3.1.1.2 | Integridad de los datos | La etiqueta GCM detecta cualquier cambio; el ESP8266 revisa el JSON con ArduinoJson; la app descarta valores imposibles (`ReadingRules`) y Firestore vuelve a validar tipo y rango (`firestore.rules`) |
| 3.1.1.3 | Reconexión automática | App: reintenta a los 2, 4, 8, 16 y 30 s, y al instante cuando vuelve la red (`data/WifiLink.kt`). ESP8266: revisa su WiFi en cada vuelta y reintenta cada 10 s (`revisarWifi()`) |
| 3.1.1.4 | Almacenamiento temporal en el teléfono y en el microcontrolador | ESP8266: guarda hasta 240 lecturas (1 hora) sin teléfono y las entrega al reconectar. Teléfono: caché de Firestore + última lectura en DataStore. Ver [Plan de contingencia](#plan-de-contingencia-y-continuidad) |
| 3.1.2.5 | La app envía instrucciones | Comandos de ventilador, lámpara, pausa y modo ahorro; confirmación de lecturas recibidas (`sendCommand`, mensaje `recibido`) |
| 3.1.2.6 | La app recibe instrucciones | Mensajes `bienvenida`, `lectura`, `estado`, `historial`, `aviso` y `pulso` (`WifiLink.openSession`) |
| 3.1.2.7 | Almacenamiento temporal en la app y sincronización | Sin internet las lecturas quedan en el teléfono y se suben solas al volver; el Historial muestra "N mediciones por sincronizar" (`ReadingsStore.pendingSync`) |
| 3.1.3.8 | El microcontrolador envía instrucciones | Lecturas, estado de los actuadores, historial guardado, latido y **avisos por su cuenta**: falla y recuperación del DHT11, señal WiFi débil, memoria llena |
| 3.1.3.9 | El microcontrolador recibe instrucciones | Comandos de la app por WiFi y también comandos escritos en el monitor serie (`ventilador on`, `estado`...) |
| 3.1.3.10 | Consumo de energía | Modo ahorro en el ESP8266 y en la app; análisis en [Consumo de energía](#consumo-de-energía) |
| 3.1.4.11 | Estándares ISO de seguridad | [Relación con las normas ISO](#relación-con-las-normas-iso) |
| 3.1.4.12 | Protección de datos personales | [Protección de datos personales](#protección-de-datos-personales) |
| 3.1.4.13 | Plan de contingencia | [Plan de contingencia y continuidad](#plan-de-contingencia-y-continuidad) |
| 3.1.4.14 | Compatibilidad con versiones y arquitecturas | [Compatibilidad](#compatibilidad) |

## Comunicación inalámbrica: WiFi

Se eligió **WiFi local** en lugar de Bluetooth o MQTT:

- **Distancia**: WiFi llega a todo el edificio; Bluetooth alcanza unos 10 metros.
- **Estabilidad**: una conexión TCP avisa si se corta y la app lo muestra al usuario.
- **Consumo**: el sensor envía un mensaje pequeño cada 3 segundos.
- **Sin dependencias**: MQTT necesita un broker externo e internet; aquí basta con que el sensor y el teléfono estén en la misma red.

Cómo funciona (`sensores/sensores.ino` y `data/WifiLink.kt`):

1. El **ESP8266** se conecta al WiFi, abre el puerto `5050` y se anuncia en la red con mDNS (`_climapulse._tcp`). Su IP aparece en el monitor serie; la **clave del enlace** es la que se puso en `config.h`.
2. La **app** busca el sensor en la red (o se escribe la IP a mano) e ingresa la clave del enlace.
3. El sensor envía una lectura cada 3 segundos (cada 10 en modo ahorro). La app la muestra en tiempo real y guarda una cada 15 segundos en Firestore.
4. La app puede encender el **ventilador** (LED D0), la **lámpara** (LED D4), **pausar** el envío o activar el **modo ahorro**. En esos pines irían los relés de un ventilador o una lámpara reales.
5. Si se corta la conexión, la app se reconecta sola: espera 2, 4, 8, 16 y luego 30 segundos entre intentos, y reintenta al instante cuando el teléfono recupera la red.
6. Mientras no hay ningún teléfono conectado, el sensor guarda las lecturas y las entrega cuando uno se conecta.

Cada mensaje es un JSON cifrado que viaja en una línea de texto.

## Protocolo del enlace (versión 2)

Cada línea es `Base64(IV de 12 bytes + JSON cifrado + etiqueta GCM de 16 bytes)`. Las reglas están en `data/LinkSession.kt` y en `sensores/sensores.ino`.

1. La app abre la conexión y manda `hola` con un **reto** aleatorio de 64 bits.
2. El nodo responde `bienvenida` con el mismo reto y un **número de sesión** nuevo. Si el reto no coincide, la app corta: puede ser una respuesta grabada.
3. Desde ahí cada mensaje lleva `sesion` y un contador `n` que siempre sube, en las dos direcciones. Un mensaje repetido, viejo o de otra sesión se descarta.

| Mensaje | Dirección | Para qué |
|---|---|---|
| `hola` | app → nodo | Saludo con el reto y el nombre del teléfono |
| `bienvenida` | nodo → app | Reto, sesión, estado de los actuadores y lecturas pendientes |
| `lectura` | nodo → app | Temperatura, humedad y luz (cada 3 s, o 10 s en modo ahorro) |
| `historial` | nodo → app | Hasta 20 lecturas guardadas sin conexión, con su `edad` en milisegundos |
| `recibido` | app → nodo | Confirma un lote del historial; recién ahí el nodo lo borra |
| `comando` | app → nodo | Ventilador, lámpara, pausa y modo ahorro |
| `estado` | nodo → app | Estado de los actuadores después de un cambio (desde la app o el monitor serie) |
| `aviso` | nodo → app | `sensor_falla`, `sensor_ok`, `wifi_debil` o `memoria_llena`, con un texto para el usuario |
| `pulso` | nodo → app | Latido cada 8 s si no se mandó nada; si pasan 20 s sin mensajes la app da la conexión por perdida |

## Nodo sensor (ESP8266)

| Componente | Pin del NodeMCU |
|---|---|
| DHT11 (datos) | D1 |
| LDR, salida AO | A0 |
| VCC de los sensores | **3V3** (no usar 5V: A0 solo mide hasta 3.3 V) |
| GND | G |

Para cargar el programa:

1. Copia `sensores/config.example.h` como `sensores/config.h` y escribe el nombre y la clave del WiFi (2.4 GHz) y la **clave del enlace** (`CLAVE_ENLACE`). Este archivo no se sube a git.
2. Instala las librerías: `arduino-cli lib install "DHT sensor library" ArduinoJson@7.2.1`
3. Compila y sube: `arduino-cli compile --fqbn esp8266:esp8266:nodemcuv2 -u -p /dev/ttyUSB0 sensores`
4. Abre el monitor serie a 115200 baudios para ver la IP y las lecturas. La clave solo se muestra al escribir `estado`, para que no quede a la vista.

En el monitor serie también se pueden escribir comandos: `ventilador on|off`, `lampara on|off`, `pausa on|off`, `ahorro on|off` y `estado`. Los cambios le llegan a la app al tiro.

Si el WiFi se cae, el nodo sigue midiendo, guarda una lectura cada 15 segundos (hasta 240, una hora) y reintenta conectarse cada 10 segundos.

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
| Enlace WiFi cifrado con **AES-GCM**; la llave sale de la clave del sensor (PBKDF2). Sin la clave no se pueden leer ni enviar comandos | `data/LinkCrypto.kt`, `sensores/sensores.ino` |
| **Clave del enlace robusta**: 10 a 32 caracteres con letras y números. Con un PIN de 6 dígitos hay un millón de opciones y quien grabe el tráfico las prueba todas en segundos; con 10 caracteres son más de 10^17. Si la clave no cumple, el sketch no compila y la app no deja conectar | `sensores/clave.h`, `LinkCrypto.keyProblem` |
| **Anti-replay** en el enlace: reto en el saludo, sesión nueva por conexión y contador que siempre sube. Un comando grabado y reenviado se descarta; la pantalla Sensor muestra cuántos se descartaron | `data/LinkSession.kt`, `sensores/sensores.ino` |
| **Validación de lecturas**: la app descarta valores imposibles para el DHT11 y el LDR antes de mostrarlos o guardarlos | `ReadingRules` en `data/LinkSession.kt` |
| Build release con **R8** (código ofuscado) y firmado; la clave de firma y sus contraseñas no se suben a git | `app/build.gradle.kts`, `keystore.properties` en `.gitignore` |
| La clave del WiFi, la clave del enlace y `google-services.json` no se suben a git | `.gitignore` |
| Respaldo automático desactivado (`allowBackup="false"`) | `AndroidManifest.xml` |

### Relación con las normas ISO

ISO/IEC 27400 (seguridad y privacidad en IoT) pide proteger la comunicación entre el dispositivo y la app, la identidad de los usuarios y los datos. Los controles se tomaron del Anexo A de ISO/IEC 27001:2022:

| Control | Cómo se aplica en ClimaPulse |
|---|---|
| 8.24 Uso de criptografía | AES-256-GCM en el enlace WiFi, PBKDF2 para la llave, TLS hacia Firebase, llave de la huella en el Keystore |
| 8.20 Seguridad de redes | El enlace solo funciona en la red local, con una clave robusta, máximo 3 monitores y corte si no saludan en 10 s |
| 8.5 Autenticación segura | Firebase Authentication, correo verificado, reautenticación para acciones delicadas, huella con CryptoObject |
| 5.15 y 8.3 Control de acceso | Reglas de Firestore: cada usuario solo ve sus datos; todo lo demás cerrado |
| 8.26 Requisitos de seguridad de la aplicación | Anti-replay, validación de rangos en la app y en Firestore, mensajes de largo máximo |
| 8.16 Monitoreo | El nodo avisa fallas del sensor y señal débil; la app cuenta los mensajes descartados |
| 5.34 Privacidad y protección de datos personales | Ver [Protección de datos personales](#protección-de-datos-personales) |
| 5.30 Continuidad de las TIC | Ver [Plan de contingencia y continuidad](#plan-de-contingencia-y-continuidad) |
| 8.28 Codificación segura | R8, sin claves en el código ni en git, pruebas unitarias del protocolo y pruebas de las reglas |

## Protección de datos personales

Se siguen los principios de ISO/IEC 29100 y la ley chilena 19.628 (y la ley 21.719, que entra en vigencia en diciembre de 2026):

- **Mínimos datos**: la cuenta guarda solo nombre, teléfono (opcional) y correo. Las lecturas no tienen datos personales: solo valores, fecha y el nombre del nodo.
- **Por el enlace WiFi no viajan datos personales**: solo lecturas, comandos y el modelo del teléfono (por ejemplo "Google Pixel 7") para que el monitor serie muestre quién se conectó. Además todo va cifrado.
- **Cada uno ve solo lo suyo**: las reglas de Firestore lo garantizan en el servidor, no solo en la app.
- **Control del usuario**: puede editar su perfil, borrar su historial y **eliminar su cuenta**, lo que borra lecturas, perfil y cuenta.
- **Contraseñas**: nunca pasan por nuestra base de datos; las maneja Firebase.
- **En el teléfono**: sin respaldo automático (`allowBackup="false"`), la huella no sale del Keystore y la sesión se cierra desde Perfil.
- **Transparencia**: los Términos (Perfil → Términos) explican qué se guarda y para qué.

## Almacenamiento y consulta de datos

- **Perfil**: documento `users/{uid}` en Firestore con `nombre` y `telefono`.
- **Lecturas**: subcolección `users/{uid}/lecturas` con temperatura, humedad, luz, fecha y origen (el nombre del nodo sensor). Se guardan las 200 más recientes; las más antiguas se borran solas (`data/ReadingsStore.kt`).
- Firestore guarda una copia en el teléfono: el historial se puede ver y seguir llenando sin internet, y se sube cuando vuelve la conexión. Mientras tanto, el Historial muestra "N mediciones por sincronizar".
- **En el ESP8266**: sin teléfono conectado guarda hasta 240 lecturas en memoria (una cada 15 s, una hora). Al conectarse un teléfono se las manda en lotes de 20 y las borra solo cuando la app confirma que las guardó.
- Las preferencias (tema, alertas, huella) quedan en DataStore, en el teléfono.
- La pestaña **Historial** muestra promedio, mínimo, máximo y cada medición.
- La pestaña **Reportes** y el detalle de cada métrica usan los mismos datos.

## Consumo de energía

Valores aproximados sacados de las hojas de datos (ESP8266EX, DHT11 y módulo LDR con LM393). Para medirlos de verdad se puede poner un medidor USB entre el cargador y el NodeMCU.

| Parte | Modo normal | Modo ahorro |
|---|---|---|
| ESP8266 (radio WiFi siempre despierta / *modem sleep*) | 70 a 80 mA | 15 a 25 mA |
| Placa NodeMCU (regulador, chip USB, LED) | 8 a 10 mA | 8 a 10 mA |
| DHT11 (mide 1 vez cada 3 s / cada 10 s) | menos de 1 mA en promedio | menos de 0,5 mA |
| Módulo LDR (divisor + LED de encendido) | 2 a 3 mA | 2 a 3 mA |
| **Total aproximado** | **~85 mA** | **~30 mA** |
| Con una batería de 2000 mAh | **~23 horas** | **~65 horas** |

Lo que se hizo para gastar menos:

- **Nodo**: el modo ahorro mide cada 10 s y deja dormir la radio entre mensajes (`WIFI_MODEM_SLEEP`). Se activa desde la app o con `ahorro on` en el monitor serie. No se usa *deep sleep* porque el nodo tiene que seguir escuchando los comandos de la app.
- **Nodo**: cada lectura cifrada ocupa unos 300 bytes y el latido solo se manda si no hubo otro mensaje en 8 s.
- **Teléfono**: no hay un servicio funcionando con la app cerrada. Firestore guarda una lectura cada 15 s, no cada 3. Los reintentos de conexión se espacian hasta 30 s. La búsqueda del sensor (NSD) se detiene al conectar.
- **Teléfono**: las animaciones que se repiten se detienen solas con el ahorro de batería de Android o con "Quitar animaciones". El tema oscuro gasta menos en pantallas OLED.

## Plan de contingencia y continuidad

| Falla | Cómo se detecta | Qué pasa automáticamente | Qué puede hacer el usuario |
|---|---|---|---|
| Se corta el WiFi del nodo | El nodo ve `WiFi.status()`; la app deja de recibir mensajes | El nodo sigue midiendo y guarda hasta 1 hora de lecturas; reintenta cada 10 s. La app reintenta con espera creciente | Nada: al volver, las lecturas guardadas llegan al historial |
| El teléfono pierde la red | Aviso de red de Android | La app muestra "Sin conexión" con la última lectura y reintenta al volver la red | Nada |
| Sin internet (pero con WiFi local) | Firestore no confirma las escrituras | Las lecturas se guardan en el teléfono y se suben solas después; el Historial muestra cuántas faltan | Nada |
| El nodo se reinicia | Se corta el socket | La app se reconecta sola; los actuadores vuelven a apagado | Revisar el estado en la pestaña Sensor |
| El DHT11 deja de responder | 3 lecturas seguidas fallidas | El nodo avisa a la app (tarjeta y mensaje) y sigue mandando latidos; avisa de nuevo cuando vuelve | Revisar el cable D1 y la alimentación |
| Señal WiFi débil | RSSI bajo -80 dBm | El nodo avisa a la app | Acercar el nodo al router |
| Se llena la memoria del nodo | Más de 240 lecturas guardadas | Se pierden las más antiguas y el nodo avisa cuántas al reconectar | Conectar un teléfono más seguido |
| Clave o IP incorrectas | La bienvenida no llega o no se puede descifrar | La app no reintenta y explica qué revisar | Corregir la IP o la clave |
| Alguien repite mensajes grabados | Sesión o contador inválido | El mensaje se descarta y se cuenta | Nada; si el número sube mucho, cambiar la clave del enlace |

Límites conocidos: la memoria del nodo es RAM, así que si se corta la luz se pierden las lecturas que aún no se entregaron. El teléfono guarda las 200 lecturas más recientes.

## Compatibilidad

- **Versiones de Android**: desde Android 8.0 (API 26) hasta Android 17 (API 37, `targetSdk`). Lo que cambia entre versiones está resuelto: el permiso de notificaciones solo se pide en Android 13 o más, y la huella usa BiometricPrompt, que funciona igual en todas.
- **Arquitecturas**: la app es Kotlin, sin código nativo propio. Las dos librerías nativas pequeñas que trae AndroidX vienen para **arm64-v8a, armeabi-v7a, x86 y x86_64**, así que el APK funciona en teléfonos ARM de 32 y 64 bits y en emuladores x86.
- **Probada en**: HONOR ABR-NX3 (Android 16, ARM 64 bits), moto g(100) (Android 12, ARM 64 bits) y emulador Pixel 7 (Android 14, x86_64).
- **Sin huella fuerte**: si el teléfono no tiene un sensor de huella clase 3, la opción no aparece y se entra con la contraseña.
- **Pantallas**: la interfaz usa Compose con medidas relativas, tema claro y oscuro, y textos que se adaptan al tamaño de letra del sistema.

## Cómo probarlo

1. Carga el programa en el ESP8266 (ver **Nodo sensor**) y anota la IP que muestra el monitor serie.
2. Conecta el teléfono a la **misma red WiFi** que el ESP8266.
3. Instala la app y crea una cuenta (Regístrate y abre el enlace del correo) o usa **Continuar con Google**.
4. Pestaña **Sensor** → **Buscar sensor**. Elige "ClimaPulse ESP8266" de la lista (o escribe la IP), ingresa la clave del enlace y toca **Conectar**.
5. La pestaña **Inicio** muestra las lecturas reales. Tapa el LDR o sopla el DHT11 y mira cómo cambian los valores.

Nota: algunas redes WiFi de universidades bloquean la conexión entre equipos o piden iniciar sesión en una página. En ese caso usa el punto de acceso (hotspot) de un teléfono en 2.4 GHz.

En un emulador la búsqueda automática no funciona: escribe la IP del sensor a mano (el PC debe estar en la misma red que el ESP8266).

### Sin la placa: simulador del nodo

`sensores/simulador.mjs` habla el mismo protocolo que el ESP8266 (necesita Node.js, sin otras dependencias):

```
node sensores/simulador.mjs          # clave cambia-esta-clave-2026, puerto 5050
```

En el emulador de Android la IP del PC es `10.0.2.2`. Con teclas se cambia el escenario (`c` calor, `x` todo mal...), se simula una falla del DHT11 (`e`), un reinicio del nodo (`r`) o un **ataque de repetición** (`z`, la app debe descartar el mensaje). Escribe `?` para ver todas.

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

**Pruebas unitarias** (reglas de contraseña, cifrado del enlace WiFi, protocolo anti-replay, validación de lecturas y modelos):

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
