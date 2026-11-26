// Nodo sensor de ClimaPulse para ESP8266 (NodeMCU).
// Lee el DHT11 y el LDR, y envia las lecturas por WiFi a la app (modo Monitor).
// Usa el mismo enlace que la app: puerto 5050, mensajes JSON cifrados con AES-GCM
// y una llave que sale de la clave del sensor (PBKDF2 con SHA-256).
//
// Protocolo (version 2):
// - La app saluda con "hola" y un reto aleatorio. El nodo responde "bienvenida" con el mismo
//   reto y un numero de sesion nuevo. Desde ahi cada mensaje lleva la sesion y un contador "n"
//   que siempre sube: un mensaje grabado y reenviado despues se rechaza (anti-replay).
// - Sin telefono conectado, el nodo guarda lecturas en memoria y las entrega al reconectarse.
// - El nodo avisa por su cuenta cuando el DHT11 falla, cuando se recupera, cuando la senal
//   WiFi esta debil o cuando se llena la memoria.
// - Tambien recibe comandos por el monitor serie (escribe "ayuda").
//
// Necesita la libreria ArduinoJson 7 y "DHT sensor library" de Adafruit.

#include <ESP8266WiFi.h>
#include <ESP8266mDNS.h>
#include <DHT.h>
#include <ArduinoJson.h>
#include <bearssl/bearssl.h>
#include <base64.h>
#include <libb64/cdecode.h>
#include "config.h"  // WIFI_NOMBRE, WIFI_CLAVE y CLAVE_ENLACE

#include "clave.h"   // revisa al compilar que la clave del enlace sea segura

// Pines
constexpr uint8_t PIN_DHT = D1;
constexpr uint8_t PIN_LUZ = A0;          // salida AO del modulo LDR
constexpr uint8_t PIN_VENTILADOR = D0;   // LED de la placa (aqui iria el rele del ventilador)
constexpr uint8_t PIN_LAMPARA = D4;      // LED del ESP8266 (aqui iria el rele de la lampara)

// Potencia del WiFi en dBm (maximo 20.5). A toda potencia los picos de corriente del radio
// botaban el voltaje del puerto USB y la placa se reiniciaba (wdt reset). Con 12 dBm
// alcanza de sobra si el router esta en la misma sala y ademas gasta menos.
constexpr float POTENCIA_WIFI = 12;

// Si la luz sube cuando tapas el LDR, cambia esto a false.
constexpr bool INVERTIR_LUZ = true;

// Calibracion del DHT11: se compara con un termometro/higrometro de referencia
// y se suma la diferencia. Ejemplo: si el DHT11 marca 25.0 y la referencia 23.8,
// AJUSTE_TEMPERATURA = -1.2
constexpr float AJUSTE_TEMPERATURA = 0.0;
constexpr float AJUSTE_HUMEDAD = 0.0;

// Se envia el promedio de las ultimas lecturas para quitar los saltos.
constexpr int LECTURAS_PROMEDIO = 5;

constexpr uint16_t PUERTO = 5050;
constexpr unsigned long INTERVALO_ENVIO = 3000;        // una lectura cada 3 segundos
constexpr unsigned long INTERVALO_AHORRO = 10000;      // en modo ahorro, cada 10 segundos
constexpr unsigned long INTERVALO_GUARDADO = 15000;    // sin telefono se guarda una cada 15 s
constexpr unsigned long INTERVALO_PULSO = 8000;        // latido si no se mando nada en 8 s
constexpr unsigned long INTERVALO_WIFI = 10000;        // reintento de WiFi cada 10 s
constexpr int MAX_MONITORES = 3;
constexpr int MAX_GUARDADAS = 240;                     // 240 x 15 s = 1 hora sin telefono
constexpr int LOTE_HISTORIAL = 20;                     // lecturas por mensaje "historial"
constexpr int FALLAS_PARA_AVISO = 3;                   // lecturas seguidas fallidas antes de avisar
constexpr int SENAL_DEBIL = -80;                       // dBm
const char* NOMBRE_NODO = "ClimaPulse ESP8266";

DHT dht(PIN_DHT, DHT11);
WiFiServer servidor(PUERTO);
uint8_t llave[32];  // llave AES de 256 bits creada a partir de la clave

// Un telefono conectado en modo Monitor
struct Monitor {
  WiFiClient cliente;
  String linea;               // texto recibido que aun no termina en salto de linea
  bool saludo;                // true cuando ya mando "hola" con la clave correcta
  unsigned long conectadoEn;
  String sesion;              // numero de sesion de esta conexion
  uint32_t enviados;          // contador de mensajes que manda el nodo
  uint32_t ultimoRecibido;    // contador del ultimo mensaje aceptado de la app
  unsigned long ultimoEnvio;  // para saber cuando mandar el latido
};
Monitor monitores[MAX_MONITORES];

// Actuadores que la app puede controlar
bool ventilador = false;
bool lampara = false;
bool pausado = false;
bool ahorro = false;

// Lectura guardada mientras no hay telefono. "momento" es millis() al medir.
struct Guardada {
  float temperatura;
  float humedad;
  float luz;
  unsigned long momento;
};
Guardada guardadas[MAX_GUARDADAS];
int primeraGuardada = 0;   // indice de la mas antigua
int totalGuardadas = 0;
uint32_t perdidas = 0;     // lecturas que se sobrescribieron por falta de espacio
int monitorHistorial = -1; // monitor al que se le esta entregando el historial

unsigned long ultimoEnvio = 0;
unsigned long ultimoGuardado = 0;
unsigned long ultimoIntentoWifi = 0;
bool wifiListo = false;
int fallasSeguidas = 0;
bool avisoFalla = false;
bool avisoSenal = false;
float ultimaTemperatura = NAN, ultimaHumedad = NAN, ultimaLuz = NAN;  // ultima lectura valida
String entradaSerie = "";

// ===================== Cifrado =====================

// PBKDF2-HMAC-SHA256 con 20000 vueltas, igual que LinkCrypto.kt en la app.
void crearLlave() {
  const char* sal = "climapulse-enlace-wifi";
  br_hmac_key_context claveHmac;
  br_hmac_key_init(&claveHmac, &br_sha256_vtable, CLAVE_ENLACE, strlen(CLAVE_ENLACE));

  br_hmac_context hmac;
  uint8_t u[32];
  uint8_t numeroBloque[4] = {0, 0, 0, 1};

  br_hmac_init(&hmac, &claveHmac, 0);
  br_hmac_update(&hmac, sal, strlen(sal));
  br_hmac_update(&hmac, numeroBloque, 4);
  br_hmac_out(&hmac, u);
  memcpy(llave, u, 32);

  for (int i = 1; i < 20000; i++) {
    br_hmac_init(&hmac, &claveHmac, 0);
    br_hmac_update(&hmac, u, 32);
    br_hmac_out(&hmac, u);
    for (int j = 0; j < 32; j++) llave[j] ^= u[j];
    if (i % 500 == 0) yield();  // evita que se reinicie el ESP8266 (watchdog)
  }
}

// Devuelve una linea Base64 con: IV (12 bytes) + texto cifrado + etiqueta (16 bytes)
String cifrar(const String& texto) {
  size_t largo = texto.length();
  uint8_t* datos = (uint8_t*)malloc(12 + largo + 16);
  if (datos == nullptr) return "";

  ESP.random(datos, 12);  // IV aleatorio
  memcpy(datos + 12, texto.c_str(), largo);

  br_aes_ct_ctr_keys aes;
  br_aes_ct_ctr_init(&aes, llave, 32);
  br_gcm_context gcm;
  br_gcm_init(&gcm, &aes.vtable, br_ghash_ctmul32);
  br_gcm_reset(&gcm, datos, 12);
  br_gcm_flip(&gcm);
  br_gcm_run(&gcm, 1, datos + 12, largo);
  br_gcm_get_tag(&gcm, datos + 12 + largo);

  String linea = base64::encode(datos, 12 + largo + 16, false);
  free(datos);
  return linea;
}

// Devuelve false si la clave no coincide o el mensaje fue modificado
bool descifrar(const String& linea, String& texto) {
  uint8_t* datos = (uint8_t*)malloc(linea.length() + 1);
  if (datos == nullptr) return false;

  int largo = base64_decode_chars(linea.c_str(), linea.length(), (char*)datos);
  if (largo < 12 + 16) {
    free(datos);
    return false;
  }
  size_t largoTexto = largo - 12 - 16;

  br_aes_ct_ctr_keys aes;
  br_aes_ct_ctr_init(&aes, llave, 32);
  br_gcm_context gcm;
  br_gcm_init(&gcm, &aes.vtable, br_ghash_ctmul32);
  br_gcm_reset(&gcm, datos, 12);
  br_gcm_flip(&gcm);
  br_gcm_run(&gcm, 0, datos + 12, largoTexto);
  bool valido = br_gcm_check_tag(&gcm, datos + 12 + largoTexto);

  if (valido) {
    texto = "";
    texto.concat((const char*)(datos + 12), largoTexto);
  }
  free(datos);
  return valido;
}

// 16 caracteres hexadecimales al azar (64 bits), para el numero de sesion.
String textoAleatorio() {
  uint8_t bytes[8];
  ESP.random(bytes, sizeof(bytes));
  String texto = "";
  for (uint8_t b : bytes) {
    if (b < 16) texto += "0";
    texto += String(b, HEX);
  }
  return texto;
}

// ===================== Mensajes =====================

void llenarActuadores(JsonObject objeto) {
  objeto["fan"] = ventilador;
  objeto["lamp"] = lampara;
  objeto["paused"] = pausado;
  objeto["eco"] = ahorro;
}

// Agrega la sesion y el contador, cifra y envia. Cada monitor tiene su propia sesion.
void enviar(Monitor& monitor, JsonDocument& mensaje) {
  mensaje["sesion"] = monitor.sesion;
  mensaje["n"] = ++monitor.enviados;
  String json;
  serializeJson(mensaje, json);
  monitor.cliente.print(cifrar(json) + "\n");
  monitor.ultimoEnvio = millis();
}

bool activo(Monitor& monitor) {
  return monitor.saludo && monitor.cliente.connected();
}

int monitoresActivos() {
  int total = 0;
  for (int i = 0; i < MAX_MONITORES; i++) if (activo(monitores[i])) total++;
  return total;
}

void enviarEstado(Monitor& monitor) {
  JsonDocument mensaje;
  mensaje["tipo"] = "estado";
  llenarActuadores(mensaje["actuadores"].to<JsonObject>());
  enviar(monitor, mensaje);
}

void enviarEstadoATodos() {
  for (int i = 0; i < MAX_MONITORES; i++) if (activo(monitores[i])) enviarEstado(monitores[i]);
}

// Aviso que el nodo manda por su cuenta para que la app informe al usuario.
// Estos textos van con tildes porque se muestran en la app (el JSON viaja en UTF-8).
void enviarAviso(Monitor& monitor, const char* codigo, const String& texto) {
  JsonDocument mensaje;
  mensaje["tipo"] = "aviso";
  mensaje["codigo"] = codigo;
  mensaje["mensaje"] = texto;
  enviar(monitor, mensaje);
}

void avisarATodos(const char* codigo, const String& texto) {
  Serial.println(String("Aviso: ") + texto);
  for (int i = 0; i < MAX_MONITORES; i++) if (activo(monitores[i])) enviarAviso(monitores[i], codigo, texto);
}

void llenarLectura(JsonObject lectura, float temperatura, float humedad, float luz) {
  lectura["temperature"] = serialized(String(temperatura, 1));
  lectura["humidity"] = serialized(String(humedad, 1));
  lectura["light"] = serialized(String(luz, 0));
  lectura["timestampMillis"] = 0;  // la hora la pone el telefono (el ESP8266 no tiene reloj)
  lectura["usingHardware"] = true;
  lectura["origin"] = NOMBRE_NODO;
}

void enviarLectura(Monitor& monitor) {
  JsonDocument mensaje;
  mensaje["tipo"] = "lectura";
  llenarLectura(mensaje["lectura"].to<JsonObject>(), ultimaTemperatura, ultimaHumedad, ultimaLuz);
  enviar(monitor, mensaje);
}

void aplicarActuadores() {
  // Los LED de la placa se encienden con LOW
  digitalWrite(PIN_VENTILADOR, ventilador ? LOW : HIGH);
  digitalWrite(PIN_LAMPARA, lampara ? LOW : HIGH);
  // Modo ahorro: la radio WiFi duerme entre mensajes (modem sleep).
  WiFi.setSleepMode(ahorro ? WIFI_MODEM_SLEEP : WIFI_NONE_SLEEP);
}

// ===================== Lecturas guardadas sin conexion =====================

void guardarLectura(float temperatura, float humedad, float luz) {
  int indice = (primeraGuardada + totalGuardadas) % MAX_GUARDADAS;
  if (totalGuardadas == MAX_GUARDADAS) {
    // Memoria llena: se pierde la mas antigua.
    primeraGuardada = (primeraGuardada + 1) % MAX_GUARDADAS;
    perdidas++;
  } else {
    totalGuardadas++;
  }
  guardadas[indice] = {temperatura, humedad, luz, millis()};
  Serial.printf("Sin telefono: lectura guardada (%d en memoria)\n", totalGuardadas);
}

// Envia un lote de lecturas guardadas. "edad" son los milisegundos desde que se midio;
// con eso el telefono calcula la hora real.
void enviarHistorial(int indiceMonitor) {
  Monitor& monitor = monitores[indiceMonitor];
  if (totalGuardadas == 0) {
    monitorHistorial = -1;
    return;
  }
  monitorHistorial = indiceMonitor;
  JsonDocument mensaje;
  mensaje["tipo"] = "historial";
  JsonArray lista = mensaje["lecturas"].to<JsonArray>();
  int cantidad = min(totalGuardadas, LOTE_HISTORIAL);
  unsigned long ahora = millis();
  for (int i = 0; i < cantidad; i++) {
    Guardada& g = guardadas[(primeraGuardada + i) % MAX_GUARDADAS];
    JsonObject item = lista.add<JsonObject>();
    item["temperature"] = serialized(String(g.temperatura, 1));
    item["humidity"] = serialized(String(g.humedad, 1));
    item["light"] = serialized(String(g.luz, 0));
    item["edad"] = ahora - g.momento;
  }
  mensaje["quedan"] = totalGuardadas - cantidad;
  enviar(monitor, mensaje);
}

// La app confirma que guardo el lote: recien ahi se borra de la memoria.
void confirmarHistorial(int indiceMonitor, int cantidad) {
  if (indiceMonitor != monitorHistorial) return;
  cantidad = constrain(cantidad, 0, min(totalGuardadas, LOTE_HISTORIAL));
  primeraGuardada = (primeraGuardada + cantidad) % MAX_GUARDADAS;
  totalGuardadas -= cantidad;
  Serial.printf("Historial entregado: %d lecturas (quedan %d)\n", cantidad, totalGuardadas);
  enviarHistorial(indiceMonitor);
}

// ===================== Lecturas =====================

// Ultimas lecturas del DHT11 para sacar el promedio
float historialTemperatura[LECTURAS_PROMEDIO];
float historialHumedad[LECTURAS_PROMEDIO];
int lecturasGuardadas = 0;
int posicionHistorial = 0;

void medir() {
  float humedadCruda = dht.readHumidity();
  float temperaturaCruda = dht.readTemperature();
  if (isnan(humedadCruda) || isnan(temperaturaCruda)) {
    Serial.println("Error al leer el DHT11; revisa su conexion.");
    fallasSeguidas++;
    if (fallasSeguidas == FALLAS_PARA_AVISO && !avisoFalla) {
      avisoFalla = true;
      avisarATodos("sensor_falla", "El DHT11 no responde. Revisa el cable de datos (D1) y la alimentación.");
    }
    return;
  }
  fallasSeguidas = 0;
  if (avisoFalla) {
    avisoFalla = false;
    avisarATodos("sensor_ok", "El DHT11 volvió a medir.");
  }

  // Se guarda la lectura nueva encima de la mas antigua
  historialTemperatura[posicionHistorial] = temperaturaCruda;
  historialHumedad[posicionHistorial] = humedadCruda;
  posicionHistorial = (posicionHistorial + 1) % LECTURAS_PROMEDIO;
  if (lecturasGuardadas < LECTURAS_PROMEDIO) lecturasGuardadas++;

  float sumaTemperatura = 0;
  float sumaHumedad = 0;
  for (int i = 0; i < lecturasGuardadas; i++) {
    sumaTemperatura += historialTemperatura[i];
    sumaHumedad += historialHumedad[i];
  }

  // Promedio + ajuste de calibracion. La humedad no puede salir de 0 a 100 %.
  ultimaTemperatura = sumaTemperatura / lecturasGuardadas + AJUSTE_TEMPERATURA;
  ultimaHumedad = constrain(sumaHumedad / lecturasGuardadas + AJUSTE_HUMEDAD, 0.0f, 100.0f);

  // A0 entrega 0 a 1023. Se pasa a una escala de 0 a 1000 para la app.
  // analogRead puede llegar a 1024, por eso se limita a 1023.
  int lecturaLuz = min(analogRead(PIN_LUZ), 1023);
  if (INVERTIR_LUZ) lecturaLuz = 1023 - lecturaLuz;
  ultimaLuz = lecturaLuz * 1000.0 / 1023.0;

  // Entre parentesis va la lectura cruda del DHT11 (sirve para calibrar)
  Serial.printf("Temperatura: %.1f C (cruda %.1f) | Humedad: %.1f %% (cruda %.1f) | Luz: %.0f\n",
                ultimaTemperatura, temperaturaCruda, ultimaHumedad, humedadCruda, ultimaLuz);

  if (monitoresActivos() > 0) {
    for (int i = 0; i < MAX_MONITORES; i++) if (activo(monitores[i])) enviarLectura(monitores[i]);
  } else if (millis() - ultimoGuardado >= INTERVALO_GUARDADO || totalGuardadas == 0) {
    ultimoGuardado = millis();
    guardarLectura(ultimaTemperatura, ultimaHumedad, ultimaLuz);
  }
}

// ===================== Monitores =====================

void aceptarMonitores() {
  WiFiClient nuevo = servidor.accept();
  if (!nuevo) return;

  for (int i = 0; i < MAX_MONITORES; i++) {
    if (!monitores[i].cliente.connected()) {
      monitores[i].cliente = nuevo;
      monitores[i].linea = "";
      monitores[i].saludo = false;
      monitores[i].conectadoEn = millis();
      Serial.println("Nuevo monitor: " + nuevo.remoteIP().toString());
      return;
    }
  }
  nuevo.stop();  // no hay espacio para mas monitores
}

void desconectar(int indice) {
  Monitor& monitor = monitores[indice];
  monitor.cliente.stop();
  monitor.saludo = false;
  monitor.linea = "";
  if (monitorHistorial == indice) monitorHistorial = -1;
}

void saludar(int indice, JsonDocument& hola) {
  Monitor& monitor = monitores[indice];
  const char* reto = hola["reto"] | "";
  // El reto debe venir y ser corto: el nodo lo devuelve para probar que la respuesta es nueva.
  if (strlen(reto) < 8 || strlen(reto) > 32) {
    desconectar(indice);
    return;
  }
  monitor.saludo = true;
  monitor.sesion = textoAleatorio();
  monitor.enviados = 0;
  monitor.ultimoRecibido = 0;
  Serial.println(String("Monitor conectado: ") + (hola["nombre"] | "sin nombre"));

  JsonDocument bienvenida;
  bienvenida["tipo"] = "bienvenida";
  bienvenida["nombre"] = NOMBRE_NODO;
  bienvenida["reto"] = reto;
  bienvenida["version"] = 2;
  bienvenida["pendientes"] = totalGuardadas;
  llenarActuadores(bienvenida["actuadores"].to<JsonObject>());
  enviar(monitor, bienvenida);

  if (!isnan(ultimaTemperatura) && !pausado) enviarLectura(monitor);
  if (avisoFalla) enviarAviso(monitor, "sensor_falla", "El DHT11 no responde. Revisa el cable de datos (D1) y la alimentación.");
  if (perdidas > 0) {
    enviarAviso(monitor, "memoria_llena", "Se perdieron " + String(perdidas) + " lecturas antiguas porque se llenó la memoria del sensor.");
    perdidas = 0;
  }
  // Entrega lo que se guardo sin conexion (de a un monitor a la vez).
  if (totalGuardadas > 0 && monitorHistorial < 0) enviarHistorial(indice);
}

void procesarLinea(int indice) {
  Monitor& monitor = monitores[indice];
  String texto;
  if (!descifrar(monitor.linea, texto)) {
    Serial.println("Mensaje rechazado: clave incorrecta o mensaje alterado.");
    desconectar(indice);
    return;
  }

  JsonDocument mensaje;
  if (deserializeJson(mensaje, texto) != DeserializationError::Ok) {
    Serial.println("Mensaje rechazado: JSON invalido.");
    desconectar(indice);
    return;
  }
  const char* tipo = mensaje["tipo"] | "";

  if (!monitor.saludo) {
    // El primer mensaje debe ser "hola"
    if (strcmp(tipo, "hola") == 0) saludar(indice, mensaje);
    else desconectar(indice);
    return;
  }

  // Anti-replay: la sesion debe ser la de esta conexion y el contador siempre debe subir.
  const char* sesion = mensaje["sesion"] | "";
  uint32_t n = mensaje["n"] | 0;
  if (monitor.sesion != sesion || n <= monitor.ultimoRecibido) {
    Serial.println("Mensaje rechazado: repetido o de otra sesion.");
    return;
  }
  monitor.ultimoRecibido = n;

  if (strcmp(tipo, "comando") == 0) {
    // La app omite los valores en false
    JsonObject a = mensaje["actuadores"];
    ventilador = a["fan"] | false;
    lampara = a["lamp"] | false;
    pausado = a["paused"] | false;
    ahorro = a["eco"] | false;
    aplicarActuadores();
    Serial.printf("Comando recibido: ventilador=%d lampara=%d pausa=%d ahorro=%d\n", ventilador, lampara, pausado, ahorro);
    enviarEstadoATodos();
  } else if (strcmp(tipo, "recibido") == 0) {
    confirmarHistorial(indice, mensaje["cantidad"] | 0);
  }
}

void leerMonitor(int indice) {
  Monitor& monitor = monitores[indice];
  if (!monitor.cliente.connected()) {
    if (monitor.saludo) Serial.println("Un monitor se desconecto.");
    monitor.saludo = false;
    if (monitorHistorial == indice) monitorHistorial = -1;
    return;
  }

  // Si no manda "hola" en 10 segundos se corta la conexion
  if (!monitor.saludo && millis() - monitor.conectadoEn > 10000) {
    desconectar(indice);
    return;
  }

  while (monitor.cliente.available()) {
    char letra = monitor.cliente.read();
    if (letra == '\n') {
      procesarLinea(indice);
      monitor.linea = "";
      if (!monitor.cliente.connected()) return;
    } else if (letra != '\r') {
      monitor.linea += letra;
      if (monitor.linea.length() > 2000) {  // mensaje demasiado largo
        desconectar(indice);
        return;
      }
    }
  }

  // Latido: si no se mando nada en un rato (pausa o falla del DHT11), la app sabe que el nodo sigue vivo.
  if (monitor.saludo && millis() - monitor.ultimoEnvio >= INTERVALO_PULSO) {
    JsonDocument pulso;
    pulso["tipo"] = "pulso";
    enviar(monitor, pulso);
  }
}

// ===================== WiFi =====================

void anunciarEnRed() {
  MDNS.end();
  if (MDNS.begin("climapulse")) {
    MDNS.setInstanceName(NOMBRE_NODO);
    MDNS.addService("climapulse", "tcp", PUERTO);
  }
  Serial.println("Listo. Datos para la app (modo Monitor):");
  Serial.println("  IP:    " + WiFi.localIP().toString());
  Serial.println("  Clave: la de CLAVE_ENLACE en config.h (escribe \"estado\" para verla)");
}

// Explica en palabras por que no hay WiFi, para verlo en el monitor serie.
const char* motivoWifi() {
  switch (WiFi.status()) {
    case WL_NO_SSID_AVAIL: return "no se encuentra la red";
    case WL_WRONG_PASSWORD: return "clave incorrecta";
    case WL_CONNECT_FAILED: return "la red rechazo la conexion";
    case WL_IDLE_STATUS: return "conectando";
    case WL_DISCONNECTED: return "desconectado";
    default: return "otro motivo";
  }
}

// Revisa el WiFi sin bloquear: si se corta, reintenta cada 10 s y sigue midiendo y guardando.
void revisarWifi() {
  if (WiFi.status() == WL_CONNECTED) {
    if (!wifiListo) {
      wifiListo = true;
      anunciarEnRed();
    }
    // Senal debil: se avisa una vez y se vuelve a avisar solo si se recupero antes.
    int senal = WiFi.RSSI();
    if (senal < SENAL_DEBIL && !avisoSenal) {
      avisoSenal = true;
      avisarATodos("wifi_debil", "La señal WiFi del sensor es débil (" + String(senal) + " dBm). Acércalo al router.");
    } else if (senal > SENAL_DEBIL + 5) {
      avisoSenal = false;
    }
    return;
  }
  if (wifiListo) {
    wifiListo = false;
    Serial.println("Se perdio el WiFi. Las lecturas se guardan hasta que vuelva.");
  }
  if (millis() - ultimoIntentoWifi >= INTERVALO_WIFI) {
    ultimoIntentoWifi = millis();
    Serial.print("Reintentando WiFi (");
    Serial.print(motivoWifi());
    Serial.println(")...");
    WiFi.reconnect();
  }
}

// ===================== Monitor serie =====================

void mostrarEstado() {
  Serial.printf("WiFi: %s | IP: %s | Senal: %d dBm\n", wifiListo ? "conectado" : "sin conexion",
                WiFi.localIP().toString().c_str(), WiFi.RSSI());
  Serial.println("Clave del enlace: " + String(CLAVE_ENLACE));
  Serial.printf("Monitores: %d | Guardadas: %d | Ventilador: %d | Lampara: %d | Pausa: %d | Ahorro: %d\n",
                monitoresActivos(), totalGuardadas, ventilador, lampara, pausado, ahorro);
}

// Comandos escritos en el monitor serie (115200 baudios), por ejemplo "ventilador on".
void procesarSerie(String linea) {
  linea.trim();
  linea.toLowerCase();
  if (linea.length() == 0) return;
  bool encender = linea.endsWith(" on");
  bool apagar = linea.endsWith(" off");

  if (linea.startsWith("ventilador") && (encender || apagar)) ventilador = encender;
  else if (linea.startsWith("lampara") && (encender || apagar)) lampara = encender;
  else if (linea.startsWith("pausa") && (encender || apagar)) pausado = encender;
  else if (linea.startsWith("ahorro") && (encender || apagar)) ahorro = encender;
  else if (linea == "estado") {
    mostrarEstado();
    return;
  } else {
    Serial.println("Comandos: ventilador on|off, lampara on|off, pausa on|off, ahorro on|off, estado");
    return;
  }
  aplicarActuadores();
  Serial.println("Listo.");
  enviarEstadoATodos();  // la app ve el cambio al tiro
}

void leerSerie() {
  while (Serial.available()) {
    char letra = Serial.read();
    if (letra == '\n' || letra == '\r') {
      if (entradaSerie.length() > 0) procesarSerie(entradaSerie);
      entradaSerie = "";
    } else if (entradaSerie.length() < 60) {
      entradaSerie += letra;
    }
  }
}

// ===================== Programa principal =====================

void setup() {
  Serial.begin(115200);
  Serial.println();
  Serial.println("ClimaPulse: nodo sensor ESP8266");

  pinMode(PIN_VENTILADOR, OUTPUT);
  pinMode(PIN_LAMPARA, OUTPUT);
  dht.begin();

  Serial.println("Creando la llave a partir de la clave...");
  crearLlave();

  WiFi.mode(WIFI_STA);
  WiFi.persistent(false);
  WiFi.setAutoReconnect(true);
  aplicarActuadores();
  WiFi.setOutputPower(POTENCIA_WIFI);
  WiFi.begin(WIFI_NOMBRE, WIFI_CLAVE);
  Serial.print("Conectando a ");
  Serial.print(WIFI_NOMBRE);
  // Se espera hasta 20 s; si no hay WiFi el nodo igual mide y guarda, y sigue reintentando.
  unsigned long inicio = millis();
  while (WiFi.status() != WL_CONNECTED && millis() - inicio < 20000) {
    delay(500);
    Serial.print(".");
  }
  Serial.println();

  servidor.begin();
  revisarWifi();
  Serial.println("Escribe \"ayuda\" para ver los comandos del monitor serie.");
}

void loop() {
  revisarWifi();
  if (wifiListo) MDNS.update();
  leerSerie();
  aceptarMonitores();
  for (int i = 0; i < MAX_MONITORES; i++) leerMonitor(i);

  unsigned long intervalo = ahorro ? INTERVALO_AHORRO : INTERVALO_ENVIO;
  if (millis() - ultimoEnvio >= intervalo) {
    ultimoEnvio = millis();
    if (!pausado) medir();
  }

  // Muestra la IP otra vez cada 30 segundos por si abriste el monitor tarde
  static unsigned long ultimoAviso = 0;
  if (millis() - ultimoAviso >= 30000) {
    ultimoAviso = millis();
    if (wifiListo) Serial.println("IP: " + WiFi.localIP().toString());
  }
}
