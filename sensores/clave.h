// Revisa al compilar que la clave del enlace sea segura.
// Va en un archivo aparte porque el IDE de Arduino crea los prototipos de las funciones
// del .ino antes de la primera funcion, y esta revision no puede ir antes de los struct.
#pragma once

// La clave del enlace se revisa al compilar: con un PIN corto alguien que grabe el
// trafico puede probar todas las opciones en segundos. Misma regla que la app (LinkCrypto.kt).
#ifndef CLAVE_ENLACE
#ifdef PIN_ENLACE
#error "Cambia PIN_ENLACE por CLAVE_ENLACE en config.h, con 10 a 32 caracteres que mezclen letras y numeros."
#else
#error "Falta CLAVE_ENLACE en config.h (mira config.example.h)."
#endif
#endif

constexpr bool claveSegura(const char* clave) {
  int largo = 0;
  bool letra = false, numero = false;
  for (; clave[largo] != '\0'; largo++) {
    char c = clave[largo];
    if (c < 33 || c > 126) return false;  // sin espacios, tildes ni ñ (igual que la app)
    if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) letra = true;
    if (c >= '0' && c <= '9') numero = true;
  }
  return largo >= 10 && largo <= 32 && letra && numero;
}
static_assert(claveSegura(CLAVE_ENLACE),
              "CLAVE_ENLACE debe tener 10 a 32 caracteres, sin espacios ni tildes, con letras y numeros.");
