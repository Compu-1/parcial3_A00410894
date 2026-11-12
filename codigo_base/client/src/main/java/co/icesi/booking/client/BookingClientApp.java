package co.icesi.booking.client;

import BookingApp.*;
import com.zeroc.Ice.Communicator;
import com.zeroc.Ice.ObjectPrx;
import com.zeroc.Ice.Util;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class BookingClientApp {

    public static final String DEFAULT_HOST = "localhost";
    public static final int DEFAULT_PORT = 10000;
    public static final String SERVICE_IDENTITY = "BookingService";

    public static void main(String[] args) {
        String host = DEFAULT_HOST;
        int port = DEFAULT_PORT;

        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--host") && i + 1 < args.length) {
                host = args[i + 1];
            } else if (args[i].equals("--port") && i + 1 < args.length) {
                try {
                    port = Integer.parseInt(args[i + 1]);
                } catch (NumberFormatException ignored) {
                }
            }
        }

        String proxyStr = String.format("%s:default -h %s -p %d", SERVICE_IDENTITY, host, port);
        System.out.println("==========================================================================");
        System.out.println("  CLIENTE DE PRUEBAS - RESERVA DE BOLETAS ZEROC ICE (COMPU 1)           ");
        System.out.println("==========================================================================");
        System.out.printf("[INFO] Conectando a: %s%n", proxyStr);

        try (Communicator communicator = Util.initialize(args)) {
            ObjectPrx base = communicator.stringToProxy(proxyStr);
            BookingServicePrx bookingService = BookingServicePrx.checkedCast(base);

            if (bookingService == null) {
                System.err.println("[ERROR] Proxy inválido o el servicio no responde.");
                return;
            }

            System.out.println("[OK] Conexión establecida con éxito con el servidor RPC.");
            String clientId = "CLI-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
            System.out.printf("[INFO] Identificador de sesión de este cliente: %s%n", clientId);

            // Si se pasa el argumento --test-concurrency, ejecuta la prueba automática
            if (Arrays.asList(args).contains("--test-concurrency")) {
                runConcurrencyTest(bookingService, "A-1");
                return;
            }

            // Menú interactivo
            Scanner scanner = new Scanner(System.in);
            boolean exit = false;

            while (!exit) {
                printMenu();
                System.out.print("Seleccione una opción: ");
                String input = scanner.nextLine().trim();

                switch (input) {
                    case "1":
                        showEventInfo(bookingService);
                        break;
                    case "2":
                        showSeatMap(bookingService);
                        break;
                    case "3":
                        lockSeatInteractive(scanner, bookingService, clientId);
                        break;
                    case "4":
                        confirmBookingInteractive(scanner, bookingService, clientId);
                        break;
                    case "5":
                        releaseSeatInteractive(scanner, bookingService, clientId);
                        break;
                    case "6":
                        showMyStatus(bookingService, clientId);
                        break;
                    case "7":
                        runConcurrencyTest(bookingService, "A-1");
                        break;
                    case "8":
                        bookingService.resetSeats();
                        System.out.println("[OK] Inventario reiniciado con éxito.");
                        break;
                    case "0":
                    case "exit":
                    case "q":
                        exit = true;
                        System.out.println("[INFO] Cerrando cliente. ¡Hasta pronto!");
                        break;
                    default:
                        System.out.println("[WARN] Opción no válida. Intente nuevamente.");
                }
            }
        } catch (Exception e) {
            System.err.println("[ERROR] Fallo de comunicación: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void printMenu() {
        System.out.println("\n--------------------------------------------------------------------------");
        System.out.println("1. Ver información del evento y aforo");
        System.out.println("2. Consultar mapa de sillas");
        System.out.println("3. Bloquear silla (lockSeat)");
        System.out.println("4. Confirmar compra de sillas bloqueadas (confirmBooking)");
        System.out.println("5. Liberar silla bloqueada (releaseSeat)");
        System.out.println("6. Ver mis sillas bloqueadas y reservas confirmadas");
        System.out.println("7. EJECUTAR PRUEBA DE ESTRÉS DE CONCURRENCIA (Demostrar Race Condition)");
        System.out.println("8. Reiniciar catálogo de sillas (resetSeats)");
        System.out.println("0. Salir");
        System.out.println("--------------------------------------------------------------------------");
    }

    private static void showEventInfo(BookingServicePrx service) {
        EventInfo info = service.getEventInfo();
        System.out.println("\n--- DETALLES DEL EVENTO ---");
        System.out.printf("Evento      : %s (%s)%n", info.name, info.id);
        System.out.printf("Fecha/Hora  : %s%n", info.date);
        System.out.printf("Lugar       : %s%n", info.venue);
        System.out.printf("Aforo Total : %d sillas (Disponibles: %d | Bloqueadas: %d | Vendidas: %d)%n",
                info.totalSeats, info.availableSeats, info.lockedSeats, info.bookedSeats);
        System.out.printf("Precios     : VIP: $%,.0f | Platea: $%,.0f | General: $%,.0f COP%n",
                info.vipPrice, info.plateaPrice, info.generalPrice);
    }

    private static void showSeatMap(BookingServicePrx service) {
        Seat[] seats = service.getAllSeats();
        System.out.println("\n--- MAPA DE SILLAS (AUDITORIO MANUELITA) ---");
        String currentRow = "";
        for (Seat s : seats) {
            if (!s.row.equals(currentRow)) {
                currentRow = s.row;
                System.out.printf("%n[Fila %s - %s]: ", currentRow, s.zone);
            }
            char stateIcon = switch (s.state) {
                case AVAILABLE -> 'O'; // Libre
                case LOCKED -> 'L'; // Bloqueado temporal
                case BOOKED -> 'X'; // Vendido definitivo
            };
            System.out.printf("[%s:%c] ", s.id, stateIcon);
        }
        System.out.println("\n\nConvenciones: [O] Disponible | [L] Bloqueada temporal | [X] Vendida definitiva");
    }

    private static void lockSeatInteractive(Scanner sc, BookingServicePrx service, String clientId) {
        System.out.print("Ingrese el ID de la silla a bloquear (ej. A-1, C-5, F-2): ");
        String seatId = sc.nextLine().trim().toUpperCase();
        try {
            LockResult res = service.lockSeat(seatId, clientId);
            System.out.printf("%s (Expira a las: %s)%n", res.message, new Date(res.expiresAt));
        } catch (SeatNotFoundException e) {
            System.out.println(" Error: " + e.reason);
        } catch (SeatNotAvailableException e) {
            System.out.println(" No disponible: " + e.reason);
        }
    }

    private static void confirmBookingInteractive(Scanner sc, BookingServicePrx service, String clientId) {
        Seat[] locked = service.getMyLockedSeats(clientId);
        if (locked.length == 0) {
            System.out.println("No tienes sillas bloqueadas actualmente para confirmar.");
            return;
        }

        System.out.printf("Tienes %d sillas bloqueadas actualmente: ", locked.length);
        List<String> seatIds = new ArrayList<>();
        double total = 0;
        for (Seat s : locked) {
            System.out.print(s.id + " ");
            seatIds.add(s.id);
            total += s.price;
        }
        System.out.printf("%nTotal a pagar: $%,.0f COP%n", total);

        System.out.print("Nombre completo del comprador: ");
        String name = sc.nextLine().trim();
        System.out.print("Correo electrónico: ");
        String email = sc.nextLine().trim();

        try {
            BookingResult res = service.confirmBooking(seatIds.toArray(new String[0]), clientId, name, email);
            System.out.println(" ¡RESERVA CONFIRMADA!");
            System.out.printf("   Código de Reserva : %s%n", res.bookingCode);
            System.out.printf("   Total Facturado   : $%,.0f COP%n", res.totalAmount);
            System.out.printf("   Sillas Adquiridas : %s%n", Arrays.toString(res.seatIds));
        } catch (Exception e) {
            System.out.println(" Error al confirmar compra: " + e.getMessage());
        }
    }

    private static void releaseSeatInteractive(Scanner sc, BookingServicePrx service, String clientId) {
        System.out.print("Ingrese el ID de la silla a liberar: ");
        String seatId = sc.nextLine().trim().toUpperCase();
        try {
            boolean ok = service.releaseSeat(seatId, clientId);
            if (ok) {
                System.out.println(" Silla liberada correctamente.");
            } else {
                System.out.println(" No se pudo liberar la silla (no la tienes bloqueada o ya expiró).");
            }
        } catch (Exception e) {
            System.out.println(" Error: " + e.getMessage());
        }
    }

    private static void showMyStatus(BookingServicePrx service, String clientId) {
        Seat[] locked = service.getMyLockedSeats(clientId);
        System.out.printf("%n--- MIS SILLAS BLOQUEADAS (%d) ---%n", locked.length);
        for (Seat s : locked) {
            System.out.printf("  • Silla %s (%s) - $%,.0f COP%n", s.id, s.zone, s.price);
        }

        BookingRecord[] bookings = service.getMyBookings(clientId);
        System.out.printf("%n--- MIS RESERVAS CONFIRMADAS (%d) ---%n", bookings.length);
        for (BookingRecord b : bookings) {
            System.out.printf("  • Código: %s | Sillas: %s | Total: $%,.0f | Fecha: %s%n",
                    b.bookingCode, Arrays.toString(b.seatIds), b.totalAmount, new Date(b.timestamp));
        }
    }

    // =========================================================================
    // [PRUEBA DE ESTRÉS DE CONCURRENCIA]
    // Demuestra empíricamente la condición de carrera en el servidor.
    // Lanza 10 hilos simultáneos que compiten por bloquear exactamente la MISMA
    // silla.
    // Con la vulnerabilidad presente, más de un hilo reportará bloqueo exitoso.
    // =========================================================================
    private static void runConcurrencyTest(BookingServicePrx service, String targetSeatId) {
        System.out.println("\n==========================================================================");
        System.out.printf("  PRUEBA DE CONCURRENCIA: 10 HILOS COMPITIENDO POR LA SILLA '%s' %n", targetSeatId);
        System.out.println("==========================================================================");

        // Reiniciar silla primero
        service.resetSeats();

        int numThreads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numThreads);

        AtomicInteger successLocks = new AtomicInteger(0);
        AtomicInteger rejectedLocks = new AtomicInteger(0);

        for (int i = 1; i <= numThreads; i++) {
            final String threadClientId = "CONCURRENT-CLIENT-" + i;
            executor.submit(() -> {
                try {
                    startLatch.await(); // Todos los hilos esperan aquí para disparar al unísono
                    LockResult res = service.lockSeat(targetSeatId, threadClientId);
                    if (res.success) {
                        successLocks.incrementAndGet();
                        System.out.printf("   [+] %s: ¡Bloqueo exitoso! (Expira: %d)%n", threadClientId, res.expiresAt);
                    }
                } catch (SeatNotAvailableException e) {
                    rejectedLocks.incrementAndGet();
                    System.out.printf("   [-] %s: Rechazado (Silla ocupada)%n", threadClientId);
                } catch (Exception e) {
                    System.out.printf("   [!] %s: Excepción: %s%n", threadClientId, e.getMessage());
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        System.out.println("[INFO] Disparando 10 peticiones simultáneas...");
        startLatch.countDown(); // Señal de salida instantánea para todos los hilos

        try {
            doneLatch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
        }

        executor.shutdown();

        System.out.println("\n--- RESULTADOS DE LA PRUEBA CONCURRENTE ---");
        System.out.printf("Peticiones que creyeron ganar el bloqueo : %d%n", successLocks.get());
        System.out.printf("Peticiones correctamente rechazadas     : %d%n", rejectedLocks.get());

        if (successLocks.get() > 1) {
            System.out.println(" [VULNERABILIDAD CONFIRMADA]: ¡CONDICIÓN DE CARRERA DETECTADA!");
            System.out.printf("   Más de un cliente (%d) logró bloquear la misma silla simultáneamente.%n",
                    successLocks.get());
            System.out.println("   Causa: Ausencia de sincronización atómica en 'lockSeat()' del servidor.");
            System.out.println("   Misión del estudiante: Implementar sincronización thread-safe en Java.");
        } else {
            System.out.println(" [SIN CONDICIÓN DE CARRERA]: Solo 1 hilo obtuvo el bloqueo. Servidor seguro.");
        }
        System.out.println("==========================================================================\n");
    }
}
