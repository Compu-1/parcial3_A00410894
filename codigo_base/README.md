# 🎟️ Sistema Distribuido de Reserva de Boletas con ZeroC Ice (Java Backend & Vanilla JS Webpack)

Proyecto distribuido de arquitectura cliente-servidor desarrollado para el curso **Computación en Internet I (`09810 - TIC`)** de la **Universidad Icesi**.

---

## 📌 1. Visión General del Proyecto

El sistema modela una plataforma de venta y reserva de boletería para eventos masivos en vivo (auditorios y arenas). La solución se compone de dos grandes capas:

1. **Backend en Java (ZeroC Ice):**
   * Servicio RPC multi-protocolo escuchando simultáneamente en:
     * **Canal TCP (`10000`):** Para clientes de escritorio o pruebas nativas en Java.
     * **Canal WebSocket (`10002`):** Para clientes web en navegadores modernos mediante la librería oficial `ice`.
   * Administración del aforo del evento (90 sillas divididas en zonas: VIP, Platea y General).
   * Ciclo de vida de boletas: `AVAILABLE` (Disponible) ➔ `LOCKED` (Bloqueo temporal de 90 segundos para compra) ➔ `BOOKED` (Venta confirmada y facturada).
 
2. **Frontend en JavaScript Vainilla con Webpack (Misión del Estudiante):**
   * Los estudiantes **deben construir el frontend desde cero**, sin frameworks (sin React, Vue o Angular).
   * Configurar **Webpack** desde cero con empaquetado moderno y ES Modules.
   * Diseñar una **arquitectura por componentes** en JavaScript puro o TypeScript.
   * Conectarse vía WebSocket al servidor ZeroC Ice y renderizar el mapa interactivo de sillas, cronómetro reactivo de bloqueo y modal de checkout.

---

## 🏗️ 2. Estructura del Repositorio

```text
codigo_base/
├── build.gradle          # Configuración raíz de compilación Gradle
├── settings.gradle       # Inclusión de submódulos (common, server, client)
├── gradlew / gradlew.bat # Gradle Wrapper ejecutable
├── common/               # Contratos Slice y definiciones de datos
│   └── src/main/slice/BookingService.ice
├── server/               # Servidor ZeroC Ice multi-canal (TCP 10000 + WS 10002)
│   └── src/main/java/co/icesi/booking/server/
│       ├── BookingServerApp.java  # Punto de entrada y configuración de adaptadores
│       └── BookingServiceImpl.java # Lógica de negocio y concurrencia didáctica
├── client/               # Cliente Java de consola para pruebas funcionales y de estrés
│   └── src/main/java/co/icesi/booking/client/
│       └── BookingClientApp.java  # Menú interactivo y test de concurrencia
└── README.md             # Esta documentación
```

---

## 🚀 3. Instrucciones de Ejecución del Backend

### Paso 1: Iniciar el Servidor ZeroC Ice
En una terminal en la raíz de `codigo_base`:
```bash
./gradlew runServer
```
*El servidor iniciará escuchando en `TCP 10000` y `WebSocket 10002`.*

### Paso 2: Ejecutar el Cliente Java de Pruebas
En una segunda terminal:
```bash
./gradlew runClient
```
*Permite consultar el aforo, ver el mapa de sillas en consola, bloquear sillas, confirmar compras y reiniciar el inventario.*

### Paso 3: Demostrar la Vulnerabilidad de Concurrencia
Puedes ejecutar la prueba de estrés automatizada directamente desde el cliente:
```bash
./gradlew runClient --args="--test-concurrency"
```
*Esta prueba dispara 10 hilos concurrentes intentando bloquear al unísono la silla `A-1`. Podrás observar cómo múltiples hilos logran bloquear la misma silla debido a la ausencia de sincronización en `BookingServiceImpl.java`.*
