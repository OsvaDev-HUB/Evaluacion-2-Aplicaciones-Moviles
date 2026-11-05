// Nodo sensor de ClimaPulse para ESP8266 (NodeMCU).
// Lee el DHT11 y el LDR, y envia las lecturas por WiFi a la app (modo Monitor).
// Usa el mismo enlace que la app: puerto 5050, mensajes JSON cifrados con AES-GCM
// y una llave que sale del PIN (PBKDF2 con SHA-256).

#include <ESP8266WiFi.h>
#include <ESP8266mDNS.h>
#include <DHT.h>
#include <bearssl/bearssl.h>
#include <base64.h>
#include <libb64/cdecode.h>
#include "config.h"  // WIFI_NOMBRE, WIFI_CLAVE y PIN_ENLACE

// Pines
constexpr uint8_t PIN_DHT = D1;
constexpr uint8_t PIN_LUZ = A0;          // salida AO del modulo LDR
constexpr uint8_t PIN_VENTILADOR = D0;   // LED de la placa (aqui iria el rele del ventilador)
constexpr uint8_t PIN_LAMPARA = D4;      // LED del ESP8266 (aqui iria el rele de la lampara)

// Si la luz sube cuando tapas el LDR, cambia esto a false.
constexpr bool INVERTIR_LUZ = true;

constexpr uint16_t PUERTO = 5050;
constexpr unsigned long INTERVALO_ENVIO = 3000;  // una lectura cada 3 segundos
constexpr int MAX_MONITORES = 3;
const char* NOMBRE_NODO = "ClimaPulse ESP8266";

DHT dht(PIN_DHT, DHT11);
WiFiServer servidor(PUERTO);
uint8_t llave[32];  // llave AES de 256 bits creada a partir del PIN

// Un telefono conectado en modo Monitor
struct Monitor {
  WiFiClient cliente;
  String linea;        // texto recibido que aun no termina en salto de linea
  bool saludo;         // true cuando ya mando "hola" con el PIN correcto
  unsigned long conectadoEn;
};
Monitor monitores[MAX_MONITORES];

// Actuadores que la app puede controlar
bool ventilador = false;
bool lampara = false;
bool pausado = false;

unsigned long ultimoEnvio = 0;
String ultimaLectura = "";  // JSON de la ultima lectura, para mandarla al conectarse

// ===================== Cifrado =====================

// PBKDF2-HMAC-SHA256 con 20000 vueltas, igual que LinkCrypto.kt en la app.
void crearLlave() {
  const char* sal = "climapulse-enlace-wifi";
  br_hmac_key_context claveHmac;
  br_hmac_key_init(&claveHmac, &br_sha256_vtable, PIN_ENLACE, strlen(PIN_ENLACE));

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

// Devuelve false si el PIN no coincide o el mensaje fue modificado
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

// ===================== Mensajes =====================

String textoBool(bool valor) {
  return valor ? "true" : "false";
}

String jsonActuadores() {
  return "{\"fan\":" + textoBool(ventilador) + ",\"lamp\":" + textoBool(lampara) +
         ",\"paused\":" + textoBool(pausado) + "}";
}

void enviar(Monitor& monitor, const String& json) {
  monitor.cliente.print(cifrar(json) + "\n");
}

// Envia un mensaje a todos los monitores que ya saludaron
void enviarATodos(const String& json) {
  for (int i = 0; i < MAX_MONITORES; i++) {
    if (monitores[i].saludo && monitores[i].cliente.connected()) enviar(monitores[i], json);
  }
}

void aplicarActuadores() {
  // Los LED de la placa se encienden con LOW
  digitalWrite(PIN_VENTILADOR, ventilador ? LOW : HIGH);
  digitalWrite(PIN_LAMPARA, lampara ? LOW : HIGH);
}

// ===================== Lecturas =====================

void enviarLectura() {
  float humedad = dht.readHumidity();
  float temperatura = dht.readTemperature();
  if (isnan(humedad) || isnan(temperatura)) {
    Serial.println("Error al leer el DHT11; revisa su conexion.");
    return;
  }

  // A0 entrega 0 a 1023. Se pasa a una escala de 0 a 1000 para la app.
  // analogRead puede llegar a 1024, por eso se limita a 1023.
  int lecturaLuz = min(analogRead(PIN_LUZ), 1023);
  if (INVERTIR_LUZ) lecturaLuz = 1023 - lecturaLuz;
  float luz = lecturaLuz * 1000.0 / 1023.0;

  Serial.printf("Temperatura: %.1f C | Humedad: %.1f %% | Luz: %.0f\n", temperatura, humedad, luz);

  // La hora la pone el telefono al recibirla (el ESP8266 no tiene reloj).
  ultimaLectura = "{\"temperature\":" + String(temperatura, 1) +
                  ",\"humidity\":" + String(humedad, 1) +
                  ",\"light\":" + String(luz, 0) +
                  ",\"timestampMillis\":0,\"usingHardware\":true,\"origin\":\"" + NOMBRE_NODO + "\"}";
  enviarATodos("{\"tipo\":\"lectura\",\"lectura\":" + ultimaLectura + "}");
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

void desconectar(Monitor& monitor) {
  monitor.cliente.stop();
  monitor.saludo = false;
  monitor.linea = "";
}

void procesarLinea(Monitor& monitor) {
  String texto;
  if (!descifrar(monitor.linea, texto)) {
    Serial.println("Mensaje rechazado: PIN incorrecto.");
    desconectar(monitor);
    return;
  }

  if (!monitor.saludo) {
    // El primer mensaje debe ser "hola"
    if (texto.indexOf("\"tipo\":\"hola\"") < 0) {
      desconectar(monitor);
      return;
    }
    monitor.saludo = true;
    Serial.println("Monitor conectado con el PIN correcto.");
    enviar(monitor, "{\"tipo\":\"bienvenida\",\"nombre\":\"" + String(NOMBRE_NODO) +
                    "\",\"actuadores\":" + jsonActuadores() + "}");
    if (ultimaLectura.length() > 0) {
      enviar(monitor, "{\"tipo\":\"lectura\",\"lectura\":" + ultimaLectura + "}");
    }
    return;
  }

  if (texto.indexOf("\"tipo\":\"comando\"") >= 0) {
    // La app omite los valores en false, por eso solo se busca ":true"
    ventilador = texto.indexOf("\"fan\":true") >= 0;
    lampara = texto.indexOf("\"lamp\":true") >= 0;
    pausado = texto.indexOf("\"paused\":true") >= 0;
    aplicarActuadores();
    Serial.println("Comando recibido: " + jsonActuadores());
    enviarATodos("{\"tipo\":\"estado\",\"actuadores\":" + jsonActuadores() + "}");
  }
}

void leerMonitor(Monitor& monitor) {
  if (!monitor.cliente.connected()) {
    if (monitor.saludo) Serial.println("Un monitor se desconecto.");
    monitor.saludo = false;
    return;
  }

  // Si no manda "hola" en 10 segundos se corta la conexion
  if (!monitor.saludo && millis() - monitor.conectadoEn > 10000) {
    desconectar(monitor);
    return;
  }

  while (monitor.cliente.available()) {
    char letra = monitor.cliente.read();
    if (letra == '\n') {
      procesarLinea(monitor);
      monitor.linea = "";
      if (!monitor.cliente.connected()) return;
    } else if (letra != '\r') {
      monitor.linea += letra;
      if (monitor.linea.length() > 2000) {  // mensaje demasiado largo
        desconectar(monitor);
        return;
      }
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
  aplicarActuadores();
  dht.begin();

  Serial.println("Creando la llave a partir del PIN...");
  crearLlave();

  WiFi.mode(WIFI_STA);
  WiFi.setSleepMode(WIFI_NONE_SLEEP);  // sin ahorro de energia: la conexion responde mas rapido
  WiFi.begin(WIFI_NOMBRE, WIFI_CLAVE);
  Serial.print("Conectando a ");
  Serial.print(WIFI_NOMBRE);
  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
  }
  Serial.println();

  servidor.begin();

  // Se anuncia en la red para que la app lo encuentre sola
  if (MDNS.begin("climapulse")) {
    MDNS.setInstanceName(NOMBRE_NODO);
    MDNS.addService("climapulse", "tcp", PUERTO);
  }

  Serial.println("Listo. Datos para la app (modo Monitor):");
  Serial.println("  IP:  " + WiFi.localIP().toString());
  Serial.println("  PIN: " + String(PIN_ENLACE));
}

void loop() {
  MDNS.update();
  aceptarMonitores();
  for (int i = 0; i < MAX_MONITORES; i++) leerMonitor(monitores[i]);

  if (millis() - ultimoEnvio >= INTERVALO_ENVIO) {
    ultimoEnvio = millis();
    if (!pausado) enviarLectura();
  }

  // Muestra la IP otra vez cada 30 segundos por si abriste el monitor tarde
  static unsigned long ultimoAviso = 0;
  if (millis() - ultimoAviso >= 30000) {
    ultimoAviso = millis();
    Serial.println("IP: " + WiFi.localIP().toString() + " | PIN: " + String(PIN_ENLACE));
  }
}
