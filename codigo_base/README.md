# Backend ZeroC Ice

Este módulo contiene el contrato Slice compartido, el servidor Java 17 y el cliente de consola. La guía completa de ejecución está en [`../README.md`](../README.md).

## Comandos

Desde esta carpeta:

```powershell
.\gradlew.bat test
.\gradlew.bat runServer
.\gradlew.bat runClient
.\gradlew.bat runClient --args="--test-concurrency"
```

El servidor atiende clientes TCP nativos en `10000` y navegadores mediante WebSocket en `10002`. Las pruebas de servicio confirman que una sola solicitud puede bloquear cada silla y que las compras deben pertenecer al cliente que mantiene bloqueos vigentes.
