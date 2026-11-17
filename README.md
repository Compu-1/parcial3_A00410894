# Icesi Live Arena

Sistema de reserva de boletas con backend Java 17 y ZeroC Ice 3.7, y una SPA en JavaScript vainilla empaquetada con Webpack. El inventario tiene 90 sillas, bloqueos de 90 segundos y confirmación de compra por RPC.

## Requisitos

- Java 17 o superior.
- Node.js 22.15 o superior y npm.
- Los puertos TCP `10000`, WebSocket `10002` y desarrollo web `3000` disponibles.

## Ejecución local

En una terminal, inicia el backend:

```powershell
cd codigo_base
.\gradlew.bat runServer
```

En otra terminal, inicia la aplicación web:

```powershell
cd client-js
npm ci
npm start
```

Abre `http://localhost:3000`. El cliente se conecta a `ws://<hostname-de-la-página>:10002`; la página y el backend deben ser accesibles desde el mismo host de red.

Para generar el bundle de producción:

```powershell
cd client-js
npm run build
```

Webpack escribe la distribución en `client-js/dist/`. Los comandos `npm start` y `npm run build` generan los stubs JavaScript desde `codigo_base/common/src/main/slice/BookingService.ice` antes de arrancar o compilar.

## Validación del backend

Ejecuta las pruebas automáticas de integridad:

```powershell
cd codigo_base
.\gradlew.bat test
```

Las pruebas automatizadas comprueban que diez clientes simultáneos produzcan un solo bloqueo exitoso; que solo el propietario pueda renovar o liberar una silla; que las retenciones vencidas se liberen y no se puedan comprar; que una solicitud de compra inválida no venda parcialmente otras sillas; que no se acepten correos inválidos ni IDs repetidos; y que el reinicio restaure el inventario y limpie el historial.

Para repetir la prueba desde el cliente de consola, inicia el servidor y ejecuta:

```powershell
cd codigo_base
.\gradlew.bat runClient --args="--test-concurrency"
```

El cliente interactivo también está disponible con `.\gradlew.bat runClient`.

## Organización y decisiones

- `codigo_base/common/src/main/slice/BookingService.ice`: contrato único de las operaciones y estructuras RPC.
- `codigo_base/server/src/main/java/co/icesi/booking/server/BookingServiceImpl.java`: inventario en memoria, validaciones de compra, expiración automática y exclusión mutua mediante el monitor del servicio.
- `codigo_base/server/src/test/java/co/icesi/booking/server/BookingServiceImplTest.java`: pruebas de concurrencia y de integridad de compra.
- `client-js/src/services/ice-booking-client.js`: inicialización del runtime ZeroC Ice y llamadas por WebSocket.
- `client-js/src/components/`: mapa de sillas, carrito, temporizador, checkout e historial desacoplados.
- `client-js/src/app.js`: coordinación de componentes y sondeo RPC para reflejar cambios de otros clientes.
- `client-js/webpack.config.js`: servidor de desarrollo en puerto 3000 y bundle de producción.

Las operaciones del inventario son sincronizadas en una única instancia del servicio: validación, cambio de estado y registro de la compra no se intercalan con otra solicitud. Un planificador libera bloqueos vencidos cada segundo; las lecturas también normalizan expiraciones para mantener consistentes el mapa y los contadores. El frontend muestra el tiempo restante a partir de la marca temporal entregada por el backend y actualiza la disponibilidad periódicamente.

Los archivos Java, JavaScript, CSS, HTML, Slice y Gradle tienen comentarios explicativos junto a cada línea de código para facilitar su sustentación. Los JSON (`package.json` y `package-lock.json`) se mantienen válidos, ya que el formato no permite comentarios; los stubs Slice generados se reconstruyen automáticamente y no se editan a mano.

## Alcance actual

El inventario y el historial viven en memoria y se reinician al detener el servidor o ejecutar `resetSeats()`. El identificador de cliente distingue pestañas de navegador para el ejercicio académico; no sustituye un mecanismo de autenticación para un sistema comercial.
