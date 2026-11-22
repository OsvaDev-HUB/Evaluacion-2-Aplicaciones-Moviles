// Simulador del nodo sensor ESP8266 para probar la app sin la placa.
// Habla el mismo protocolo que sensores.ino (version 2): puerto 5050, una linea por mensaje,
// JSON cifrado con AES-GCM y llave PBKDF2-SHA256 sacada del PIN, con sesion y contador
// contra mensajes repetidos, lecturas guardadas sin conexion, avisos y latido.
//
// Uso:  node sensores/simulador.mjs [--pin 123456] [--puerto 5050] [--intervalo 3000]
// En el emulador de Android la IP del PC es 10.0.2.2. En un telefono real usa la IP
// del PC en la misma red WiFi (o buscalo en la app si avahi esta activo).

import net from "node:net";
import crypto from "node:crypto";
import readline from "node:readline";
import os from "node:os";
import { spawn } from "node:child_process";

// ===================== Opciones =====================

function opcion(nombre, porDefecto) {
  const i = process.argv.indexOf(`--${nombre}`);
  return i >= 0 && process.argv[i + 1] ? process.argv[i + 1] : porDefecto;
}

const PIN = opcion("pin", "123456");
const PUERTO = Number(opcion("puerto", 5050));
const INTERVALO = Number(opcion("intervalo", 3000));
const INTERVALO_AHORRO = 10000;
const GUARDAR_CADA = Number(opcion("guardar", 15000)); // sin telefono se guarda una lectura cada 15 s
const MAX_GUARDADAS = 240;
const LOTE = 20;
const NOMBRE_NODO = opcion("nombre", "ClimaPulse Simulador");
const MAX_MONITORES = 3;

// ===================== Cifrado (igual que LinkCrypto.kt) =====================

const llave = crypto.pbkdf2Sync(PIN, "climapulse-enlace-wifi", 20000, 32, "sha256");

function cifrar(texto) {
  const iv = crypto.randomBytes(12);
  const cifrador = crypto.createCipheriv("aes-256-gcm", llave, iv);
  const datos = Buffer.concat([cifrador.update(texto, "utf8"), cifrador.final()]);
  return Buffer.concat([iv, datos, cifrador.getAuthTag()]).toString("base64");
}

// Devuelve null si el PIN no coincide o el mensaje fue modificado
function descifrar(linea) {
  try {
    const bytes = Buffer.from(linea.trim(), "base64");
    if (bytes.length < 28) return null;
    const descifrador = crypto.createDecipheriv("aes-256-gcm", llave, bytes.subarray(0, 12));
    descifrador.setAuthTag(bytes.subarray(bytes.length - 16));
    const texto = Buffer.concat([descifrador.update(bytes.subarray(12, bytes.length - 16)), descifrador.final()]);
    return texto.toString("utf8");
  } catch {
    return null;
  }
}

// ===================== Escenarios =====================

// Cada escenario define hacia donde tienden los valores. Los rangos ideales de la app
// son 19-25 C, 40-65 % y 180-750 de luz, asi que varios escenarios salen de ellos a proposito.
const ESCENARIOS = {
  n: { nombre: "normal", temperatura: 22, humedad: 52, luz: 420 },
  c: { nombre: "calor", temperatura: 31, humedad: 38, luz: 650 },
  f: { nombre: "frio", temperatura: 14, humedad: 60, luz: 300 },
  h: { nombre: "humedo", temperatura: 24, humedad: 82, luz: 350 },
  s: { nombre: "seco", temperatura: 23, humedad: 25, luz: 500 },
  o: { nombre: "oscuro", temperatura: 20, humedad: 55, luz: 40 },
  l: { nombre: "mucha luz", temperatura: 26, humedad: 45, luz: 950 },
  x: { nombre: "todo mal", temperatura: 34, humedad: 88, luz: 20 },
};

let escenario = ESCENARIOS[opcion("escenario", "n")] ?? ESCENARIOS.n;
let oscilar = false;  // si esta activo, los valores suben y bajan para ver graficos con forma
let fallaDht = false; // simula que el DHT11 no responde: no se envian lecturas
let apagado = false;  // simula que el ESP8266 se reinicio

const valores = { temperatura: escenario.temperatura, humedad: escenario.humedad, luz: escenario.luz };
const actuadores = { fan: false, lamp: false, paused: false, eco: false };
const guardadas = []; // { temperature, humidity, light, momento }
let perdidas = 0;
let monitorHistorial = null;
let fallasSeguidas = 0;
let avisoFalla = false;

function acercar(actual, meta, velocidad, ruido) {
  return actual + (meta - actual) * velocidad + (Math.random() - 0.5) * ruido;
}

function siguienteLectura() {
  const t = Date.now() / 1000;
  const onda = oscilar ? Math.sin(t / 20) : 0;

  // El ventilador baja la temperatura y la lampara sube la luz, como en la vida real
  const metaTemperatura = escenario.temperatura + onda * 5 - (actuadores.fan ? 3 : 0);
  const metaHumedad = escenario.humedad - onda * 12;
  const metaLuz = escenario.luz + onda * 250 + (actuadores.lamp ? 300 : 0);

  valores.temperatura = acercar(valores.temperatura, metaTemperatura, 0.25, 0.4);
  valores.humedad = Math.min(100, Math.max(0, acercar(valores.humedad, metaHumedad, 0.25, 1.5)));
  valores.luz = Math.min(1000, Math.max(0, acercar(valores.luz, metaLuz, 0.3, 25)));

  return {
    temperature: Number(valores.temperatura.toFixed(1)),
    humidity: Number(valores.humedad.toFixed(1)),
    light: Math.round(valores.luz),
    timestampMillis: 0, // la hora la pone el telefono
    usingHardware: true,
    origin: NOMBRE_NODO,
  };
}

// ===================== Monitores =====================

const monitores = new Set();
let ultimaLectura = null;

function aleatorio() {
  return crypto.randomBytes(8).toString("hex");
}

// Agrega la sesion y el contador de este monitor, cifra y envia.
function enviar(monitor, objeto) {
  if (monitor.socket.destroyed) return;
  const linea = cifrar(JSON.stringify({ ...objeto, sesion: monitor.sesion, n: ++monitor.enviados })) + "\n";
  monitor.ultimaLinea = linea;
  monitor.ultimoEnvio = Date.now();
  monitor.socket.write(linea);
}

function enviarATodos(objeto) {
  for (const monitor of monitores) if (monitor.saludo) enviar(monitor, objeto);
}

function activos() {
  return [...monitores].filter((m) => m.saludo).length;
}

function avisar(codigo, mensaje) {
  console.log(`Aviso: ${mensaje}`);
  enviarATodos({ tipo: "aviso", codigo, mensaje });
}

function guardar(lectura) {
  if (guardadas.length === MAX_GUARDADAS) {
    guardadas.shift();
    perdidas++;
  }
  guardadas.push({ temperature: lectura.temperature, humidity: lectura.humidity, light: lectura.light, momento: Date.now() });
  console.log(`Sin telefono: lectura guardada (${guardadas.length} en memoria)`);
}

function enviarHistorial(monitor) {
  if (guardadas.length === 0) {
    monitorHistorial = null;
    return;
  }
  monitorHistorial = monitor;
  const ahora = Date.now();
  const lote = guardadas.slice(0, LOTE).map((g) => ({
    temperature: g.temperature, humidity: g.humidity, light: g.light, edad: ahora - g.momento,
  }));
  enviar(monitor, { tipo: "historial", lecturas: lote, quedan: guardadas.length - lote.length });
}

function procesarLinea(monitor, linea) {
  const texto = descifrar(linea);
  if (texto === null) {
    console.log("Mensaje rechazado: PIN incorrecto o mensaje alterado.");
    monitor.socket.destroy();
    return;
  }

  let mensaje;
  try {
    mensaje = JSON.parse(texto);
  } catch {
    monitor.socket.destroy();
    return;
  }

  if (!monitor.saludo) {
    const reto = typeof mensaje.reto === "string" ? mensaje.reto : "";
    if (mensaje.tipo !== "hola" || reto.length < 8 || reto.length > 32) {
      monitor.socket.destroy();
      return;
    }
    monitor.saludo = true;
    monitor.sesion = aleatorio();
    monitor.enviados = 0;
    monitor.ultimoRecibido = 0;
    clearTimeout(monitor.espera);
    console.log(`Monitor conectado: ${mensaje.nombre ?? "sin nombre"}`);
    enviar(monitor, { tipo: "bienvenida", nombre: NOMBRE_NODO, reto, version: 2, pendientes: guardadas.length, actuadores });
    if (ultimaLectura && !actuadores.paused) enviar(monitor, { tipo: "lectura", lectura: ultimaLectura });
    if (avisoFalla) enviar(monitor, { tipo: "aviso", codigo: "sensor_falla", mensaje: "El DHT11 no responde. Revisa el cable de datos (D1) y la alimentación." });
    if (perdidas > 0) {
      enviar(monitor, { tipo: "aviso", codigo: "memoria_llena", mensaje: `Se perdieron ${perdidas} lecturas antiguas porque se llenó la memoria del sensor.` });
      perdidas = 0;
    }
    if (guardadas.length > 0 && !monitorHistorial) enviarHistorial(monitor);
    return;
  }

  // Anti-replay: misma sesion y contador que siempre sube.
  if (mensaje.sesion !== monitor.sesion || !(mensaje.n > monitor.ultimoRecibido)) {
    console.log("Mensaje rechazado: repetido o de otra sesion.");
    return;
  }
  monitor.ultimoRecibido = mensaje.n;

  if (mensaje.tipo === "comando") {
    // La app omite los valores en false
    const a = mensaje.actuadores ?? {};
    actuadores.fan = a.fan === true;
    actuadores.lamp = a.lamp === true;
    actuadores.paused = a.paused === true;
    actuadores.eco = a.eco === true;
    console.log(`Comando recibido: ${JSON.stringify(actuadores)}`);
    enviarATodos({ tipo: "estado", actuadores });
    reprogramar();
  } else if (mensaje.tipo === "recibido" && monitor === monitorHistorial) {
    const cantidad = Math.max(0, Math.min(Number(mensaje.cantidad) || 0, LOTE, guardadas.length));
    guardadas.splice(0, cantidad);
    console.log(`Historial entregado: ${cantidad} lecturas (quedan ${guardadas.length})`);
    enviarHistorial(monitor);
  }
}

const servidor = net.createServer((socket) => {
  if (apagado || monitores.size >= MAX_MONITORES) {
    socket.destroy();
    return;
  }
  socket.setNoDelay(true);
  const monitor = { socket, saludo: false, buffer: "", ultimoEnvio: Date.now() };
  monitores.add(monitor);
  console.log(`Nuevo monitor: ${socket.remoteAddress}`);

  // Si no manda "hola" en 10 segundos se corta la conexion
  monitor.espera = setTimeout(() => {
    if (!monitor.saludo) socket.destroy();
  }, 10000);

  socket.on("data", (datos) => {
    monitor.buffer += datos.toString("utf8");
    if (monitor.buffer.length > 4000) socket.destroy();
    let salto;
    while ((salto = monitor.buffer.indexOf("\n")) >= 0) {
      const linea = monitor.buffer.slice(0, salto).replace(/\r$/, "");
      monitor.buffer = monitor.buffer.slice(salto + 1);
      if (linea) procesarLinea(monitor, linea);
    }
  });
  socket.on("close", () => {
    if (monitor.saludo) console.log("Un monitor se desconecto.");
    clearTimeout(monitor.espera);
    monitores.delete(monitor);
    if (monitorHistorial === monitor) monitorHistorial = null;
  });
  socket.on("error", () => {});
});

// ===================== Envio periodico =====================

let ultimoGuardado = 0;
let temporizador = null;

function medir() {
  if (apagado || actuadores.paused) return;
  if (fallaDht) {
    console.log("Error al leer el DHT11 (simulado).");
    fallasSeguidas++;
    if (fallasSeguidas === 3 && !avisoFalla) {
      avisoFalla = true;
      avisar("sensor_falla", "El DHT11 no responde. Revisa el cable de datos (D1) y la alimentación.");
    }
    return;
  }
  fallasSeguidas = 0;
  if (avisoFalla) {
    avisoFalla = false;
    avisar("sensor_ok", "El DHT11 volvió a medir.");
  }
  ultimaLectura = siguienteLectura();
  const l = ultimaLectura;
  if (activos() > 0) {
    enviarATodos({ tipo: "lectura", lectura: ultimaLectura });
  } else if (Date.now() - ultimoGuardado >= GUARDAR_CADA || guardadas.length === 0) {
    ultimoGuardado = Date.now();
    guardar(l);
  }
  console.log(`[${escenario.nombre}] ${l.temperature} C | ${l.humidity} % | luz ${l.light}  (${activos()} monitor/es)`);
}

// En modo ahorro el nodo mide cada 10 s.
function reprogramar() {
  clearInterval(temporizador);
  temporizador = setInterval(medir, actuadores.eco ? INTERVALO_AHORRO : INTERVALO);
}
reprogramar();

// Latido: si un monitor no recibio nada en 8 s, se le manda "pulso".
setInterval(() => {
  for (const monitor of monitores) {
    if (monitor.saludo && Date.now() - monitor.ultimoEnvio >= 8000) enviar(monitor, { tipo: "pulso" });
  }
}, 1000);

// ===================== Anuncio en la red (mDNS) =====================

// Se usa avahi-publish si esta instalado, asi la app encuentra el simulador sola
let avahi = null;
function anunciar() {
  avahi = spawn("avahi-publish-service", [NOMBRE_NODO, "_climapulse._tcp", String(PUERTO)], { stdio: "ignore" });
  const sinAvahi = () => {
    if (!avahi) return;
    avahi = null;
    console.log("(no se pudo anunciar con avahi: escribe la IP a mano en la app)");
  };
  avahi.on("error", sinAvahi);
  // Si avahi-daemon no esta corriendo, avahi-publish termina al tiro
  avahi.on("exit", sinAvahi);
}

// ===================== Teclado =====================

function ayuda() {
  console.log(`
Teclas (escribe la letra y Enter):
  Escenarios:  n normal | c calor | f frio | h humedo | s seco | o oscuro | l mucha luz | x todo mal
  w   activar/desactivar oscilacion (valores que suben y bajan)
  e   activar/desactivar falla del DHT11 (deja de enviar; la app corta a los 10 s)
  k   cortar a todos los monitores (la app deberia reconectarse sola)
  r   simular reinicio del ESP8266 (rechaza conexiones durante 8 s)
  v   ventilador | b lampara | p pausa | a ahorro   (como si el cambio viniera de la placa)
  z   ataque de repeticion: reenvia el ultimo mensaje tal cual (la app debe descartarlo)
  i   ver estado | ? ayuda | q salir
`);
}

function cortarTodos() {
  for (const monitor of monitores) monitor.socket.destroy();
}

const teclado = readline.createInterface({ input: process.stdin });
teclado.on("line", (entrada) => {
  const tecla = entrada.trim().toLowerCase();
  if (ESCENARIOS[tecla]) {
    escenario = ESCENARIOS[tecla];
    console.log(`Escenario: ${escenario.nombre}`);
  } else if (tecla === "w") {
    oscilar = !oscilar;
    console.log(`Oscilacion: ${oscilar ? "si" : "no"}`);
  } else if (tecla === "e") {
    fallaDht = !fallaDht;
    console.log(`Falla del DHT11: ${fallaDht ? "si" : "no"}`);
  } else if (tecla === "k") {
    cortarTodos();
    console.log("Monitores desconectados.");
  } else if (tecla === "r") {
    apagado = true;
    cortarTodos();
    console.log("Reiniciando el nodo (8 s)...");
    setTimeout(() => {
      apagado = false;
      console.log("Nodo encendido otra vez.");
    }, 8000);
  } else if (tecla === "v" || tecla === "b" || tecla === "p" || tecla === "a") {
    const campo = { v: "fan", b: "lamp", p: "paused", a: "eco" }[tecla];
    actuadores[campo] = !actuadores[campo];
    console.log(`Actuadores: ${JSON.stringify(actuadores)}`);
    enviarATodos({ tipo: "estado", actuadores });
    reprogramar();
  } else if (tecla === "z") {
    // Reenvia la misma linea cifrada: mismo contador, asi que la app debe rechazarla.
    for (const monitor of monitores) if (monitor.saludo && monitor.ultimaLinea) monitor.socket.write(monitor.ultimaLinea);
    console.log("Mensaje repetido enviado (prueba de anti-replay).");
  } else if (tecla === "i") {
    console.log({ escenario: escenario.nombre, oscilar, fallaDht, apagado, actuadores, monitores: activos(), guardadas: guardadas.length });
  } else if (tecla === "q") {
    salir();
  } else if (tecla) {
    ayuda();
  }
});

function salir() {
  const proceso = avahi;
  avahi = null;
  proceso?.kill();
  cortarTodos();
  process.exit(0);
}
process.on("SIGINT", salir);
process.on("SIGTERM", salir);

// ===================== Inicio =====================

servidor.listen(PUERTO, "0.0.0.0", () => {
  const ips = Object.values(os.networkInterfaces())
    .flat()
    .filter((d) => d && d.family === "IPv4" && !d.internal)
    .map((d) => d.address);
  console.log("ClimaPulse: simulador del nodo sensor");
  console.log(`  IP:  ${ips.join(", ") || "sin red"}  (emulador: 10.0.2.2)`);
  console.log(`  Puerto: ${PUERTO} | PIN: ${PIN}`);
  anunciar();
  ayuda();
});
