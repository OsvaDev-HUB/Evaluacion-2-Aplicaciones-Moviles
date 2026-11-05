# ClimaPulse

App Android para monitorear temperatura, humedad y luz en un ambiente. Hecha en Kotlin con Jetpack Compose y Material 3.

Sumativa Unidad 2 (TI3042): un **ESP8266 (NodeMCU)** con un sensor **DHT11** y un módulo **LDR** funciona como **nodo sensor** y envía lecturas reales por **WiFi**. La app Android funciona como **monitor**: recibe las lecturas, las guarda y controla el sensor a distancia. Se pueden conectar hasta 3 teléfonos al mismo sensor.

## Problemática

Queremos saber si un espacio (sala, bodega, invernadero) tiene una temperatura, humedad y luz adecuadas sin tener que estar ahí. El nodo sensor mide el ambiente y el monitor muestra los datos, los guarda y puede encender un ventilador o una lámpara si los valores salen del rango ideal.

## Herramientas

| Herramienta | Para qué se usa | Por qué |
|---|---|---|
| Android Studio + Kotlin | Desarrollo de la app | Lenguaje oficial recomendado por Google para Android |
| Jetpack Compose + Material 3 | Interfaz | Estándar actual de diseño en Android |
| SQLite (`SQLiteOpenHelper`) | Usuarios y lecturas | Viene incluido en Android, funciona sin internet y no necesita servidor |
| ESP8266 + Arduino CLI | Nodo sensor (`sensores/sensores.ino`) | Tiene WiFi incluido y se programa igual que un Arduino |
| Sockets TCP + NSD/mDNS | Comunicación entre el sensor y la app | Funciona en la red WiFi local, sin internet y sin servicios externos |
| AES-GCM + PBKDF2 | Cifrado | Incluidos en Java/Android, no hace falta otra librería |
| Android Keystore | Token de sesión | La llave queda en hardware seguro del teléfono |
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
3. El sensor envía una lectura cada 3 segundos. La app la muestra en tiempo real y guarda una cada 15 segundos en SQLite.
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

## Login y autenticación: SQLite

- Registro e inicio de sesión con correo y contraseña guardados en la tabla `usuarios` (`data/AppDatabase.kt`).
- Funciona sin conexión y es fácil de implementar. Para muchos usuarios en varios teléfonos convendría pasar a Firebase o a una API con MySQL.

## Seguridad (ISO/IEC 27400)

| Medida | Dónde |
|---|---|
| Contraseñas guardadas como hash **PBKDF2** con sal aleatoria, nunca en texto plano | `data/PasswordHasher.kt` |
| Bloqueo de 30 segundos después de 5 intentos fallidos | `data/SessionRepository.kt` |
| Token de sesión cifrado con AES-GCM usando una llave del **Android Keystore**, con vencimiento de 7 días | `data/TokenCipher.kt` |
| Enlace WiFi cifrado con **AES-GCM**; la llave sale del PIN (PBKDF2). Sin el PIN no se pueden leer ni enviar comandos | `data/LinkCrypto.kt`, `sensores/sensores.ino` |
| La clave del WiFi y el PIN del sensor no se suben a git | `sensores/config.h` en `.gitignore` |
| Consultas SQL con parámetros (`?`) para evitar inyección SQL | `data/AppDatabase.kt` |
| Respaldo automático desactivado (`allowBackup="false"`) para que la base de datos no salga del teléfono | `AndroidManifest.xml` |
| Desbloqueo con huella opcional | `ui/auth/Biometric.kt` |

## Almacenamiento y consulta de datos

- Tabla `lecturas` en SQLite: temperatura, humedad, luz, fecha y origen (el nombre del nodo sensor).
- La pestaña **Historial** consulta la tabla y muestra promedio, mínimo, máximo y cada medición.
- La pestaña **Reportes** y el detalle de cada métrica usan los mismos datos.

## Cómo probarlo

1. Carga el programa en el ESP8266 (ver **Nodo sensor**) y anota la IP y el PIN del monitor serie.
2. Conecta el teléfono a la **misma red WiFi** que el ESP8266.
3. Instala la app y crea una cuenta (Regístrate).
4. Pestaña **Sensor** → **Buscar sensor**. Elige "ClimaPulse ESP8266" de la lista (o escribe la IP), ingresa el PIN y toca **Conectar**.
5. La pestaña **Inicio** muestra las lecturas reales. Tapa el LDR o sopla el DHT11 y mira cómo cambian los valores.

Nota: algunas redes WiFi de universidades bloquean la conexión entre equipos o piden iniciar sesión en una página. En ese caso usa el punto de acceso (hotspot) de un teléfono en 2.4 GHz.

En un emulador la búsqueda automática no funciona: escribe la IP del sensor a mano (el PC debe estar en la misma red que el ESP8266).

## Abrir en Android Studio

1. Abre Android Studio.
2. Selecciona **Open** y elige la carpeta `ClimaPulse`.
3. Espera a que termine la sincronización de Gradle.
4. Ejecuta la configuración `app` en un emulador o dispositivo Android (Android 8.0 o superior).

## Pantallas

| Pantalla | Dónde |
|---|---|
| Splash + Router | `MainActivity.kt`, `MainViewModel.kt` |
| Onboarding | `ui/onboarding/` |
| Login / Registro / Recuperación + huella | `ui/auth/` |
| Inicio con lecturas en tiempo real | `ui/home/DashboardScreen.kt` |
| Enlace WiFi con el sensor y control remoto | `ui/home/SensorScreen.kt`, `ui/home/SensorViewModel.kt` |
| Historial (SQLite) | `ui/history/` |
| Reportes | `ui/home/ReportsScreen.kt` |
| Detalle de métrica y rango ideal | `ui/detail/` |
| Perfil / Ajustes / Términos / Cerrar sesión | `ui/settings/` |

## Notas

- Las lecturas son **reales** y vienen del ESP8266. Sin un sensor conectado, la app muestra la última lectura guardada marcada como "Sin conexión".
- La recuperación de contraseña solo revisa que la cuenta exista en el teléfono, porque no hay un servidor de correo.
- **Al abrir la app**: primero el logo (Splash, al menos 1,2 s) y luego la **bienvenida** de 3 páginas. La bienvenida sale en cada apertura, salvo que se marque "No volver a mostrar"; se puede reactivar en Perfil → Ayuda. Después sigue al login, o directo al Inicio si la sesión está abierta.
- La ventana de permisos y el tutorial aparecen solo en el **primer ingreso de cada usuario** (por ejemplo, justo después de registrarse). La bandera se guarda por correo en DataStore (`intro_done_<correo>`). El tutorial se puede repetir desde Perfil → Ayuda.
- **Alertas**: si el permiso de notificaciones está concedido y "Alertas de ambiente" está activado en Perfil, llega una notificación cuando el ambiente pasa de cómodo a fuera de rango (`data/AlertNotifier.kt`). Funciona mientras la app está abierta o en segundo plano reciente; no hay un servicio que siga midiendo con la app cerrada.
- En Perfil → Apariencia se elige el tema: **Sistema**, **Claro** u **Oscuro**. La elección queda guardada.
