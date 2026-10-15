# ClimaPulse

App Android para monitorear temperatura, humedad y luz en un ambiente. Hecha en Kotlin con Jetpack Compose y Material 3.

Sumativa Unidad 2 (TI3042): la app se instala en **dos teléfonos Android** conectados por **WiFi**. Uno funciona como **nodo sensor** (simula las lecturas) y el otro como **monitor** (recibe las lecturas y controla el sensor a distancia).

## Problemática

Queremos saber si un espacio (sala, bodega, invernadero) tiene una temperatura, humedad y luz adecuadas sin tener que estar ahí. El nodo sensor mide el ambiente y el monitor muestra los datos, los guarda y puede encender un ventilador o una lámpara si los valores salen del rango ideal.

## Herramientas

| Herramienta | Para qué se usa | Por qué |
|---|---|---|
| Android Studio + Kotlin | Desarrollo de la app | Lenguaje oficial recomendado por Google para Android |
| Jetpack Compose + Material 3 | Interfaz | Estándar actual de diseño en Android |
| SQLite (`SQLiteOpenHelper`) | Usuarios y lecturas | Viene incluido en Android, funciona sin internet y no necesita servidor |
| Sockets TCP + NSD | Comunicación entre teléfonos | Funciona en la red WiFi local, sin internet y sin servicios externos |
| AES-GCM + PBKDF2 | Cifrado | Incluidos en Java/Android, no hace falta otra librería |
| Android Keystore | Token de sesión | La llave queda en hardware seguro del teléfono |
| DataStore | Preferencias | Reemplazo moderno de SharedPreferences |

## Comunicación inalámbrica: WiFi

Se eligió **WiFi local** en lugar de Bluetooth o MQTT:

- **Distancia**: WiFi llega a todo el edificio; Bluetooth alcanza unos 10 metros.
- **Estabilidad**: una conexión TCP avisa si se corta y la app lo muestra al usuario.
- **Consumo**: el sensor envía un mensaje pequeño cada 3 segundos, eso gasta poca batería.
- **Sin dependencias**: MQTT necesita un broker externo e internet; aquí basta con que ambos teléfonos estén en la misma red.

Cómo funciona (`data/WifiLink.kt`):

1. El **nodo sensor** abre el puerto `5050`, se anuncia en la red con NSD (`_climapulse._tcp`) y muestra un **PIN de 6 dígitos**.
2. El **monitor** busca sensores en la red (o se escribe la IP a mano) e ingresa el PIN.
3. El sensor envía una lectura cada 3 segundos. El monitor la muestra en tiempo real y guarda una cada 15 segundos en SQLite.
4. El monitor puede encender el **ventilador** (baja la temperatura y la humedad), la **lámpara** (sube la luz) o **pausar** el envío. El sensor recibe el comando y cambia sus lecturas.

Cada mensaje es un JSON cifrado que viaja en una línea de texto.

## Login y autenticación: SQLite

- Registro e inicio de sesión con correo y contraseña guardados en la tabla `usuarios` (`data/AppDatabase.kt`).
- Funciona sin conexión y es fácil de implementar. Para muchos usuarios en varios teléfonos convendría pasar a Firebase o a una API con MySQL.

## Seguridad (ISO/IEC 27400)

| Medida | Dónde |
|---|---|
| Contraseñas guardadas como hash **PBKDF2** con sal aleatoria, nunca en texto plano | `data/PasswordHasher.kt` |
| Bloqueo de 30 segundos después de 5 intentos fallidos | `data/SessionRepository.kt` |
| Token de sesión cifrado con AES-GCM usando una llave del **Android Keystore**, con vencimiento de 7 días | `data/TokenCipher.kt` |
| Enlace WiFi cifrado con **AES-GCM**; la llave sale del PIN. Sin el PIN no se pueden leer ni enviar comandos | `data/LinkCrypto.kt` |
| Consultas SQL con parámetros (`?`) para evitar inyección SQL | `data/AppDatabase.kt` |
| Respaldo automático desactivado (`allowBackup="false"`) para que la base de datos no salga del teléfono | `AndroidManifest.xml` |
| Desbloqueo con huella opcional | `ui/auth/Biometric.kt` |

## Almacenamiento y consulta de datos

- Tabla `lecturas` en SQLite: temperatura, humedad, luz, fecha y origen (este teléfono o el nombre del sensor remoto).
- La pestaña **Historial** consulta la tabla y muestra promedio, mínimo, máximo y cada medición.
- La pestaña **Reportes** y el detalle de cada métrica usan los mismos datos.

## Cómo probarlo con dos teléfonos

1. Instala la app en los dos teléfonos y conéctalos a la **misma red WiFi**.
2. Crea una cuenta en cada uno (Regístrate).
3. Teléfono A: pestaña **Sensor** → **Ser nodo sensor**. Anota el PIN.
4. Teléfono B: pestaña **Sensor** → **Ser monitor**. Elige el sensor de la lista (o escribe la IP que muestra A), ingresa el PIN y toca **Conectar**.
5. En B, la pestaña **Inicio** muestra las lecturas de A. Activa el ventilador o la lámpara y mira cómo cambian los valores.

Nota: algunas redes WiFi de universidades bloquean la conexión entre equipos. Si pasa, usa el punto de acceso (hotspot) de uno de los teléfonos.

Para probar con dos emuladores en el mismo PC: en el emulador sensor ejecuta `adb -s emulator-5554 emu redir add tcp:5050:5050` y en el emulador monitor conéctate a la IP `10.0.2.2`.

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
| Enlace WiFi (sensor / monitor / control remoto) | `ui/home/SensorScreen.kt`, `ui/home/SensorViewModel.kt` |
| Historial (SQLite) | `ui/history/` |
| Reportes | `ui/home/ReportsScreen.kt` |
| Detalle de métrica y rango ideal | `ui/detail/` |
| Perfil / Ajustes / Términos / Cerrar sesión | `ui/settings/` |

## Notas

- Las lecturas son **simuladas** (`data/ReadingSimulator.kt`), como pide la actividad. Si el teléfono tiene sensores reales de temperatura, humedad o luz, se pueden activar cambiando `USE_DEMO_DATA` en `data/EnvironmentRepository.kt`.
- La recuperación de contraseña solo revisa que la cuenta exista en el teléfono, porque no hay un servidor de correo.
- **Al abrir la app**: primero el logo (Splash, al menos 1,2 s) y luego la **bienvenida** de 3 páginas. La bienvenida sale en cada apertura, salvo que se marque "No volver a mostrar"; se puede reactivar en Perfil → Ayuda. Después sigue al login, o directo al Inicio si la sesión está abierta.
- La ventana de permisos y el tutorial aparecen solo en el **primer ingreso de cada usuario** (por ejemplo, justo después de registrarse). La bandera se guarda por correo en DataStore (`intro_done_<correo>`). El tutorial se puede repetir desde Perfil → Ayuda.
- **Alertas**: si el permiso de notificaciones está concedido y "Alertas de ambiente" está activado en Perfil, llega una notificación cuando el ambiente pasa de cómodo a fuera de rango (`data/AlertNotifier.kt`). Funciona mientras la app está abierta o en segundo plano reciente; no hay un servicio que siga midiendo con la app cerrada.
- En Perfil → Apariencia se elige el tema: **Sistema**, **Claro** u **Oscuro**. La elección queda guardada.
