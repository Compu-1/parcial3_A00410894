package co.icesi.booking.client; // Ubica las herramientas de prueba del cliente en su paquete.

import BookingApp.*; // Importa los stubs Slice y tipos del servicio de reservas.
import com.zeroc.Ice.Communicator; // Administra la conexión y los recursos de Ice.
import com.zeroc.Ice.ObjectPrx; // Representa un proxy remoto sin tipo concreto.
import com.zeroc.Ice.Util; // Provee las funciones estándar de inicialización de Ice.

import java.util.*; // Aporta colecciones, fechas, identificadores y lectura de consola.
import java.util.concurrent.*; // Aporta hilos, barreras y ejecución concurrente de pruebas.
import java.util.concurrent.atomic.AtomicInteger; // Cuenta resultados desde varios hilos sin carreras.

public class BookingClientApp { // Implementa el menú interactivo y las pruebas TCP del backend.

    public static final String DEFAULT_HOST = "localhost"; // Usa la máquina local si no se indica otro servidor.
    public static final int DEFAULT_PORT = 10000; // Usa el puerto TCP reservado para clientes Java.
    public static final String SERVICE_IDENTITY = "BookingService"; // Coincide con la identidad publicada por el servidor.

    public static void main(String[] args) { // Conecta al servicio y despacha las opciones del usuario.
        String host = DEFAULT_HOST; // Conserva localhost como destino predeterminado.
        int port = DEFAULT_PORT; // Conserva el puerto TCP como valor inicial.

        for (int i = 0; i < args.length; i++) { // Recorre opciones de host y puerto recibidas por Gradle.
            if (args[i].equals("--host") && i + 1 < args.length) { // Verifica que --host tenga un valor asociado.
                host = args[i + 1]; // Guarda el nombre o la dirección del servidor.
            } else if (args[i].equals("--port") && i + 1 < args.length) { // Verifica que --port tenga un valor asociado.
                try { // Intenta convertir el puerto recibido a entero.
                    port = Integer.parseInt(args[i + 1]); // Usa el puerto configurado por quien ejecuta el cliente.
                } catch (NumberFormatException ignored) { // Conserva el puerto predeterminado si el valor no es numérico.
                } // Finaliza la conversión opcional del puerto.
            } // Finaliza el análisis de la opción actual.
        } // Finaliza el análisis de argumentos.

        String proxyStr = String.format("%s:default -h %s -p %d", SERVICE_IDENTITY, host, port); // Construye el proxy Ice para TCP.
        System.out.println("=========================================================================="); // Dibuja el encabezado del cliente.
        System.out.println("  CLIENTE DE PRUEBAS - RESERVA DE BOLETAS ZEROC ICE (COMPU 1)           "); // Identifica la herramienta de consola.
        System.out.println("=========================================================================="); // Cierra visualmente el encabezado.
        System.out.printf("[INFO] Conectando a: %s%n", proxyStr); // Informa qué endpoint se va a utilizar.

        try (Communicator communicator = Util.initialize(args)) { // Inicializa Ice y cierra sus recursos al salir.
            ObjectPrx base = communicator.stringToProxy(proxyStr); // Crea el proxy sin tipo para la identidad remota.
            BookingServicePrx bookingService = BookingServicePrx.checkedCast(base); // Comprueba y convierte el proxy al contrato esperado.

            if (bookingService == null) { // Detecta un objeto ausente o incompatible con BookingService.
                System.err.println("[ERROR] Proxy inválido o el servicio no responde."); // Explica por qué no puede continuar.
                return; // Sale antes de usar un proxy nulo.
            } // Finaliza la validación del proxy.

            System.out.println("[OK] Conexión establecida con éxito con el servidor RPC."); // Confirma que el proxy fue validado.
            String clientId = "CLI-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase(); // Genera una identidad para esta ejecución.
            System.out.printf("[INFO] Identificador de sesión de este cliente: %s%n", clientId); // Muestra la identidad usada por las reservas.

            // Si se pasa el argumento --test-concurrency, ejecuta la prueba automática
            if (Arrays.asList(args).contains("--test-concurrency")) { // Comprueba si se pidió ejecutar el test sin mostrar el menú.
                runConcurrencyTest(bookingService, "A-1"); // Ejecuta el test automático sobre la silla A-1.
                return; // Sale después de completar la prueba solicitada.
            } // Finaliza la comprobación del modo de estrés.

            // Menú interactivo
            Scanner scanner = new Scanner(System.in); // Lee las opciones y datos introducidos en la consola.
            boolean exit = false; // Mantiene activo el menú hasta elegir salir.

            while (!exit) { // Repite el menú mientras el cliente permanezca abierto.
                printMenu(); // Presenta las acciones que se pueden ejecutar.
                System.out.print("Seleccione una opción: "); // Solicita una acción al operador.
                String input = scanner.nextLine().trim(); // Lee y limpia la opción recibida.

                switch (input) { // Despacha la opción seleccionada al método correspondiente.
                    case "1": // Selecciona la consulta de evento y aforo.
                        showEventInfo(bookingService); // Solicita metadata y contadores por RPC.
                        break; // Evita ejecutar otra opción del menú.
                    case "2": // Selecciona la consulta del mapa.
                        showSeatMap(bookingService); // Consulta y dibuja el inventario actual.
                        break; // Finaliza esta opción.
                    case "3": // Selecciona un bloqueo temporal.
                        lockSeatInteractive(scanner, bookingService, clientId); // Solicita el ID y ejecuta lockSeat.
                        break; // Finaliza esta opción.
                    case "4": // Selecciona la confirmación de compra.
                        confirmBookingInteractive(scanner, bookingService, clientId); // Solicita datos y confirma las sillas retenidas.
                        break; // Finaliza esta opción.
                    case "5": // Selecciona la liberación voluntaria.
                        releaseSeatInteractive(scanner, bookingService, clientId); // Solicita el ID y ejecuta releaseSeat.
                        break; // Finaliza esta opción.
                    case "6": // Selecciona las consultas asociadas a esta sesión.
                        showMyStatus(bookingService, clientId); // Muestra bloqueos propios e historial de compras.
                        break; // Finaliza esta opción.
                    case "7": // Selecciona la prueba multi-hilo de exclusión mutua.
                        runConcurrencyTest(bookingService, "A-1"); // Compite por la misma silla con diez clientes.
                        break; // Finaliza esta opción.
                    case "8": // Selecciona el reinicio de inventario para laboratorio.
                        bookingService.resetSeats(); // Solicita al servicio borrar bloqueos y ventas actuales.
                        System.out.println("[OK] Inventario reiniciado con éxito."); // Confirma la respuesta del reinicio.
                        break; // Finaliza esta opción.
                    case "0": // Acepta el número de salida del menú.
                    case "exit": // Acepta la palabra de salida.
                    case "q": // Acepta la abreviatura de salida.
                        exit = true; // Termina el ciclo del menú.
                        System.out.println("[INFO] Cerrando cliente. ¡Hasta pronto!"); // Informa el cierre solicitado.
                        break; // Finaliza la opción de salida.
                    default: // Atiende cualquier valor no reconocido.
                        System.out.println("[WARN] Opción no válida. Intente nuevamente."); // Solicita volver a elegir una opción válida.
                } // Finaliza el despacho de la opción.
            } // Finaliza el ciclo interactivo.
        } catch (Exception e) { // Informa errores de conexión o RPC no controlados por una opción.
            System.err.println("[ERROR] Fallo de comunicación: " + e.getMessage()); // Expone el mensaje principal del error.
            e.printStackTrace(); // Facilita el diagnóstico completo en laboratorio.
        } // Finaliza el ciclo de vida del cliente.
    } // Finaliza el punto de entrada.

    private static void printMenu() { // Presenta las operaciones disponibles para el usuario.
        System.out.println("\n--------------------------------------------------------------------------"); // Delimita visualmente el menú.
        System.out.println("1. Ver información del evento y aforo"); // Ofrece la consulta de datos del evento.
        System.out.println("2. Consultar mapa de sillas"); // Ofrece la consulta del inventario.
        System.out.println("3. Bloquear silla (lockSeat)"); // Ofrece una retención temporal.
        System.out.println("4. Confirmar compra de sillas bloqueadas (confirmBooking)"); // Ofrece convertir bloqueos propios en venta.
        System.out.println("5. Liberar silla bloqueada (releaseSeat)"); // Ofrece devolver voluntariamente una silla.
        System.out.println("6. Ver mis sillas bloqueadas y reservas confirmadas"); // Ofrece consultas asociadas al cliente.
        System.out.println("7. VALIDAR EXCLUSIÓN MUTUA DEL INVENTARIO"); // Nombra la prueba que verifica un solo ganador por asiento.
        System.out.println("8. Reiniciar catálogo de sillas (resetSeats)"); // Ofrece reiniciar los datos del laboratorio.
        System.out.println("0. Salir"); // Ofrece terminar la sesión del cliente.
        System.out.println("--------------------------------------------------------------------------"); // Cierra visualmente el menú.
    } // Finaliza la presentación del menú.

    private static void showEventInfo(BookingServicePrx service) { // Recupera y muestra la información del evento.
        EventInfo info = service.getEventInfo(); // Consulta metadata y contadores actuales al backend.
        System.out.println("\n--- DETALLES DEL EVENTO ---"); // Titula los datos del evento.
        System.out.printf("Evento      : %s (%s)%n", info.name, info.id); // Imprime el nombre y el identificador del evento.
        System.out.printf("Fecha/Hora  : %s%n", info.date); // Imprime la fecha y hora configuradas.
        System.out.printf("Lugar       : %s%n", info.venue); // Imprime el recinto del concierto.
        System.out.printf( // Muestra el aforo global dividido por disponibilidad y estado.
                "Aforo Total : %d sillas (Disponibles: %d | Bloqueadas: %d | Vendidas: %d)%n", // Define el formato del resumen de aforo.
                info.totalSeats, info.availableSeats, info.lockedSeats, info.bookedSeats); // Muestra el aforo y sus estados.
        System.out.printf( // Muestra las tarifas de las zonas en pesos colombianos.
                "Precios     : VIP: $%,.0f | Platea: $%,.0f | General: $%,.0f COP%n", // Define el formato de las tarifas.
                info.vipPrice, info.plateaPrice, info.generalPrice); // Muestra la tarifa configurada para cada zona.
    } // Finaliza la impresión del evento.

    private static void showSeatMap(BookingServicePrx service) { // Dibuja las sillas ordenadas que devuelve el backend.
        Seat[] seats = service.getAllSeats(); // Consulta la instantánea actual del inventario.
        System.out.println("\n--- MAPA DE SILLAS (AUDITORIO MANUELITA) ---"); // Titula el mapa de la arena.
        String currentRow = ""; // Recuerda la fila impresa para añadir títulos una sola vez.
        for (Seat s : seats) { // Recorre las sillas ordenadas por fila y número.
            if (!s.row.equals(currentRow)) { // Detecta el inicio de una fila nueva.
                currentRow = s.row; // Guarda la fila que se está dibujando.
                System.out.printf("%n[Fila %s - %s]: ", currentRow, s.zone); // Imprime la fila y su zona.
            } // Finaliza la impresión condicional del título de fila.
            char stateIcon = switch (s.state) { // Convierte el enum Slice en un símbolo de consola.
                case AVAILABLE -> 'O'; // Representa una silla libre.
                case LOCKED -> 'L'; // Representa una silla temporalmente bloqueada.
                case BOOKED -> 'X'; // Representa una silla vendida.
            }; // Finaliza la traducción del estado a símbolo.
            System.out.printf("[%s:%c] ", s.id, stateIcon); // Imprime el código y estado de la silla.
        } // Finaliza el recorrido del mapa.
        System.out.println("\n\nConvenciones: [O] Disponible | [L] Bloqueada temporal | [X] Vendida definitiva"); // Explica los símbolos usados.
    } // Finaliza el mapa de asientos.

    private static void lockSeatInteractive(Scanner sc, BookingServicePrx service, String clientId) { // Solicita y bloquea una silla para el cliente.
        System.out.print("Ingrese el ID de la silla a bloquear (ej. A-1, C-5, F-2): "); // Pide el identificador del asiento.
        String seatId = sc.nextLine().trim().toUpperCase(); // Normaliza el ID antes de enviarlo al backend.
        try { // Distingue resultados exitosos de errores conocidos del contrato.
            LockResult res = service.lockSeat(seatId, clientId); // Solicita el bloqueo temporal por RPC.
            System.out.printf("%s (Expira a las: %s)%n", res.message, new Date(res.expiresAt)); // Muestra confirmación y hora de vencimiento.
        } catch (SeatNotFoundException e) { // Atiende IDs que no existen en el inventario.
            System.out.println(" Error: " + e.reason); // Muestra el motivo entregado por el servidor.
        } catch (SeatNotAvailableException e) { // Atiende sillas retenidas o vendidas.
            System.out.println(" No disponible: " + e.reason); // Explica por qué el bloqueo fue rechazado.
        } // Finaliza la respuesta del bloqueo interactivo.
    } // Finaliza la selección de una silla.

    private static void confirmBookingInteractive(Scanner sc, BookingServicePrx service, String clientId) { // Confirma las sillas que retiene el cliente.
        Seat[] locked = service.getMyLockedSeats(clientId); // Consulta bloqueos vigentes propios antes del checkout.
        if (locked.length == 0) { // Evita pedir información de compra si no hay asientos reservados.
            System.out.println("No tienes sillas bloqueadas actualmente para confirmar."); // Explica por qué no se puede continuar.
            return; // Sale sin llamar al servidor de compras.
        } // Finaliza la comprobación de carrito vacío.

        System.out.printf("Tienes %d sillas bloqueadas actualmente: ", locked.length); // Informa la cantidad a comprar.
        List<String> seatIds = new ArrayList<>(); // Reúne IDs para formar la secuencia del contrato Slice.
        double total = 0; // Acumula el total informativo del cliente.
        for (Seat s : locked) { // Recorre las sillas propias obtenidas del backend.
            System.out.print(s.id + " "); // Muestra cada silla que se incluirá.
            seatIds.add(s.id); // Añade su identificador a la solicitud de compra.
            total += s.price; // Suma el precio de la silla para el resumen previo.
        } // Finaliza la construcción de la lista y el total.
        System.out.printf("%nTotal a pagar: $%,.0f COP%n", total); // Muestra el importe estimado en pesos.

        System.out.print("Nombre completo del comprador: "); // Solicita el dato obligatorio de facturación.
        String name = sc.nextLine().trim(); // Lee y limpia el nombre del comprador.
        System.out.print("Correo electrónico: "); // Solicita el correo de contacto.
        String email = sc.nextLine().trim(); // Lee y limpia el correo del comprador.

        try { // Envía la operación y muestra errores sin terminar el cliente.
            BookingResult res = service.confirmBooking(seatIds.toArray(new String[0]), clientId, name, email); // Confirma los bloqueos y datos del comprador.
            System.out.println(" ¡RESERVA CONFIRMADA!"); // Informa el éxito de la compra.
            System.out.printf("   Código de Reserva : %s%n", res.bookingCode); // Muestra el código emitido por el servidor.
            System.out.printf("   Total Facturado   : $%,.0f COP%n", res.totalAmount); // Muestra el valor definitivo calculado por el backend.
            System.out.printf("   Sillas Adquiridas : %s%n", Arrays.toString(res.seatIds)); // Enumera las sillas confirmadas.
        } catch (Exception e) { // Atiende validaciones, expiraciones y fallos RPC.
            System.out.println(" Error al confirmar compra: " + e.getMessage()); // Expone el motivo del rechazo.
        } // Finaliza el resultado del checkout.
    } // Finaliza la confirmación interactiva.

    private static void releaseSeatInteractive(Scanner sc, BookingServicePrx service, String clientId) { // Libera un bloqueo solicitado por consola.
        System.out.print("Ingrese el ID de la silla a liberar: "); // Solicita el código de la silla retenida.
        String seatId = sc.nextLine().trim().toUpperCase(); // Normaliza el código antes de enviarlo por RPC.
        try { // Distingue una liberación exitosa de respuestas fallidas.
            boolean ok = service.releaseSeat(seatId, clientId); // Solicita al backend liberar la silla propia.
            if (ok) { // Comprueba la respuesta del servicio.
                System.out.println(" Silla liberada correctamente."); // Confirma que el asiento volvió al inventario.
            } else { // Atiende bloqueos ajenos, vencidos o inexistentes.
                System.out.println(" No se pudo liberar la silla (no la tienes bloqueada o ya expiró)."); // Explica por qué no cambió el inventario.
            } // Finaliza la presentación del resultado booleano.
        } catch (Exception e) { // Atiende errores RPC distintos a un resultado falso.
            System.out.println(" Error: " + e.getMessage()); // Expone el mensaje recibido del servidor.
        } // Finaliza la liberación interactiva.
    } // Finaliza el método de liberación.

    private static void showMyStatus(BookingServicePrx service, String clientId) { // Presenta reservas temporales e historial del cliente.
        Seat[] locked = service.getMyLockedSeats(clientId); // Consulta retenciones todavía vigentes para esta identidad.
        System.out.printf("%n--- MIS SILLAS BLOQUEADAS (%d) ---%n", locked.length); // Muestra la cantidad de bloqueos propios.
        for (Seat s : locked) { // Recorre las sillas retenidas por el cliente.
            System.out.printf("  • Silla %s (%s) - $%,.0f COP%n", s.id, s.zone, s.price); // Muestra identificación, zona y precio.
        } // Finaliza la lista de bloqueos.

        BookingRecord[] bookings = service.getMyBookings(clientId); // Consulta compras confirmadas asociadas al mismo cliente.
        System.out.printf("%n--- MIS RESERVAS CONFIRMADAS (%d) ---%n", bookings.length); // Muestra la cantidad de comprobantes.
        for (BookingRecord b : bookings) { // Recorre cada compra del historial.
            System.out.printf( // Presenta cada compra confirmada en una sola línea.
                    "  • Código: %s | Sillas: %s | Total: $%,.0f | Fecha: %s%n", // Define los campos visibles de cada reserva.
                    b.bookingCode, Arrays.toString(b.seatIds), b.totalAmount, new Date(b.timestamp)); // Presenta código, sillas, valor y fecha. // Presenta comprobante, asientos, importe y fecha.
        } // Finaliza la lista de compras.
    } // Finaliza la consulta del estado propio.

    // =========================================================================
    // [PRUEBA DE ESTRÉS DE CONCURRENCIA]
    // Lanza diez hilos que intentan reservar la misma silla al mismo tiempo.
    // El resultado correcto es un bloqueo exitoso y nueve rechazos.
    // Si hay varios ganadores o ninguno, la prueba informa un error.
    // =========================================================================
    private static void runConcurrencyTest(BookingServicePrx service, String targetSeatId) { // Prueba varios clientes compitiendo por una silla.
        System.out.println("\n=========================================================================="); // Dibuja el encabezado del test de estrés.
        System.out.printf("  PRUEBA DE CONCURRENCIA: 10 HILOS COMPITIENDO POR LA SILLA '%s' %n", targetSeatId); // Identifica la silla que compartirán los hilos.
        System.out.println("=========================================================================="); // Cierra visualmente el encabezado de la prueba.

        service.resetSeats(); // Parte de un inventario libre para que la competencia sea reproducible.

        int numThreads = 10; // Define la cantidad de clientes que competirán.
        ExecutorService executor = Executors.newFixedThreadPool(numThreads); // Crea un hilo independiente por solicitante.
        CountDownLatch startLatch = new CountDownLatch(1); // Mantiene a los hilos preparados hasta dispararlos simultáneamente.
        CountDownLatch doneLatch = new CountDownLatch(numThreads); // Permite esperar a que todas las solicitudes terminen.

        AtomicInteger successLocks = new AtomicInteger(0); // Cuenta los clientes que obtuvieron el asiento.
        AtomicInteger rejectedLocks = new AtomicInteger(0); // Cuenta los clientes rechazados por disponibilidad.

        for (int i = 1; i <= numThreads; i++) { // Prepara un intento de bloqueo por cada cliente simulado.
            final String threadClientId = "CONCURRENT-CLIENT-" + i; // Asigna una identidad independiente al hilo actual.
            executor.submit(() -> { // Programa el intento de RPC en el grupo de hilos.
                try { // Maneja tanto el resultado como las excepciones de cada solicitud.
                    startLatch.await(); // Todos los hilos esperan aquí para disparar al unísono.
                    LockResult res = service.lockSeat(targetSeatId, threadClientId); // Intenta reservar la misma silla remota.
                    if (res.success) { // Cuenta solo las respuestas exitosas del servicio.
                        successLocks.incrementAndGet(); // Incrementa de forma atómica el número de ganadores.
                        System.out.printf("   [+] %s: ¡Bloqueo exitoso! (Expira: %d)%n", threadClientId, res.expiresAt); // Muestra cliente ganador y vencimiento.
                    } // Finaliza el manejo del bloqueo exitoso.
                } catch (SeatNotAvailableException e) { // Atiende clientes que perdieron la competencia.
                    rejectedLocks.incrementAndGet(); // Cuenta atómicamente el rechazo por contención.
                    System.out.printf("   [-] %s: Rechazado (Silla ocupada)%n", threadClientId); // Muestra que el cliente perdió la competencia.
                } catch (Exception e) { // Registra errores inesperados de comunicación o ejecución.
                    System.out.printf("   [!] %s: Excepción: %s%n", threadClientId, e.getMessage()); // Expone errores imprevistos de esta solicitud.
                } finally { // Señala la finalización incluso si el intento falla.
                    doneLatch.countDown(); // Reduce el contador usado por el hilo principal.
                } // Finaliza la limpieza de la tarea concurrente.
            }); // Finaliza el envío del intento al ejecutor.
        } // Finaliza la preparación de clientes concurrentes.

        System.out.println("[INFO] Disparando 10 peticiones simultáneas..."); // Informa que empieza la carrera.
        startLatch.countDown(); // Libera todos los hilos desde la misma señal.

        try { // Espera de forma acotada para evitar bloquear el programa indefinidamente.
            doneLatch.await(5, TimeUnit.SECONDS); // Da hasta cinco segundos a todas las respuestas RPC.
        } catch (InterruptedException interrupted) { // Atiende una interrupción del proceso de prueba.
            Thread.currentThread().interrupt(); // Restaura la bandera de interrupción para el código llamador.
        } // Finaliza la espera de los resultados.

        executor.shutdown(); // Deja de aceptar tareas y cierra los hilos al terminar las solicitudes activas.

        System.out.println("\n--- RESULTADOS DE LA PRUEBA CONCURRENTE ---"); // Titula el resumen del test.
        System.out.printf("Peticiones que creyeron ganar el bloqueo : %d%n", successLocks.get()); // Imprime el número de bloqueos exitosos.
        System.out.printf("Peticiones correctamente rechazadas     : %d%n", rejectedLocks.get()); // Imprime el número de conflictos rechazados.

        if (successLocks.get() > 1) { // Detecta que una silla haya sido concedida a más de un cliente.
            System.out.println(" [ERROR]: ¡CONDICIÓN DE CARRERA DETECTADA!"); // Señala que más de una petición ganó la misma silla.
            System.out.printf( // Describe el resultado cuando más de un hilo gana incorrectamente.
                    "   Más de un cliente (%d) logró bloquear la misma silla simultáneamente.%n", // Explica el fallo de exclusión detectado.
                    successLocks.get()); // Indica cuántos clientes fueron aceptados incorrectamente.
            System.out.println("   Una sola silla fue concedida a varios clientes."); // Explica el incumplimiento de exclusión mutua.
        } else if (successLocks.get() == 1) { // Considera correcto que exactamente una solicitud gane.
            System.out.println(" [OK]: Solo 1 hilo obtuvo el bloqueo; los demás fueron rechazados."); // Confirma el resultado seguro esperado.
        } else { // Detecta un fallo que no es una carrera, como que ningún cliente logre reservar.
            System.out.println(" [ERROR]: Ningún hilo obtuvo el bloqueo; revise el servicio."); // Evita etiquetar una prueba fallida como exitosa.
        } // Finaliza la evaluación del resultado concurrente.
        System.out.println("==========================================================================\n"); // Cierra la salida de la prueba de estrés.
    } // Finaliza la prueba concurrente.
} // Finaliza la clase cliente de consola.
