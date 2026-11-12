package co.icesi.booking.server;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet; // Detecta IDs duplicados antes de cobrar una reserva.
import java.util.List; // Conserva asientos y reservas en estructuras recorribles.
import java.util.Map; // Expone el inventario como pares ID-silla.
import java.util.Set; // Registra los identificadores únicos de una compra.
import java.util.UUID; // Genera códigos de comprobante con alta entropía.
import java.util.concurrent.Executors; // Crea el planificador de expiraciones.
import java.util.concurrent.ScheduledExecutorService; // Programa la liberación periódica de bloqueos.
import java.util.concurrent.TimeUnit; // Convierte duraciones de bloqueo a milisegundos.

import com.zeroc.Ice.Current;

import BookingApp.BookingException;
import BookingApp.BookingRecord;
import BookingApp.BookingResult;
import BookingApp.BookingService;
import BookingApp.EventInfo;
import BookingApp.InvalidBookingException;
import BookingApp.LockExpiredException;
import BookingApp.LockResult;
import BookingApp.Seat;
import BookingApp.SeatNotAvailableException;
import BookingApp.SeatNotFoundException;
import BookingApp.SeatState;
import BookingApp.SeatZone;

public class BookingServiceImpl implements BookingService, AutoCloseable { // Expone el servicio RPC y administra el limpiador de bloqueos.

    public static final long DEFAULT_LOCK_DURATION_SECONDS = 90;

    private final EventInfo eventInfo;
    private final Map<String, Seat> seatsMap = new HashMap<>();
    private final List<BookingRecord> bookingsList = new ArrayList<>();
    private final ScheduledExecutorService expiryExecutor = Executors.newSingleThreadScheduledExecutor(task -> { // Crea un hilo dedicado a expirar bloqueos vencidos.
        Thread thread = new Thread(task, "booking-lock-expirer"); // Da un nombre reconocible al hilo de mantenimiento.
        thread.setDaemon(true); // Permite que la JVM termine aunque el hilo de mantenimiento siga activo.
        return thread; // Entrega el hilo preparado al planificador.
    });

    public BookingServiceImpl() {
        this.eventInfo = new EventInfo(
                "EVT-2026-LIVE",
                "Concierto Icesi Live Rock & Pop 2026",
                "2026-11-28 20:00",
                "Auditorio Manuelita - Universidad Icesi",
                90, 90, 0, 0,
                120000.0, 75000.0, 45000.0
        );
        initSeats();
        expiryExecutor.scheduleAtFixedRate(this::expireLocks, 1, 1, TimeUnit.SECONDS); // Libera automáticamente las sillas vencidas cada segundo.
    }

    private void initSeats() {
        seatsMap.clear();
        bookingsList.clear();

        createSeatsForZone("A", 1, 10, SeatZone.ZONEVIP, 120000.0);
        createSeatsForZone("B", 1, 10, SeatZone.ZONEVIP, 120000.0);

        createSeatsForZone("C", 1, 10, SeatZone.ZONEPLATEA, 75000.0);
        createSeatsForZone("D", 1, 10, SeatZone.ZONEPLATEA, 75000.0);
        createSeatsForZone("E", 1, 10, SeatZone.ZONEPLATEA, 75000.0);

        createSeatsForZone("F", 1, 10, SeatZone.ZONEGENERAL, 45000.0);
        createSeatsForZone("G", 1, 10, SeatZone.ZONEGENERAL, 45000.0);
        createSeatsForZone("H", 1, 10, SeatZone.ZONEGENERAL, 45000.0);
        createSeatsForZone("I", 1, 10, SeatZone.ZONEGENERAL, 45000.0);

        updateEventCounters();
        System.out.printf("[INFO] Inventario inicializado con %d sillas en 3 zonas (VIP, Platea, General).%n", seatsMap.size());
    }

    private void createSeatsForZone(String row, int startNum, int endNum, SeatZone zone, double price) {
        for (int num = startNum; num <= endNum; num++) {
            String seatId = String.format("%s-%d", row, num);
            Seat seat = new Seat(
                    seatId,
                    zone,
                    row,
                    num,
                    price,
                    SeatState.AVAILABLE,
                    "",
                    0L,
                    DEFAULT_LOCK_DURATION_SECONDS
            );
            seatsMap.put(seatId, seat);
        }
    }

    private boolean isLockExpired(Seat seat) { // Determina si el límite exacto de vencimiento ya fue alcanzado.
        if (seat.state != SeatState.LOCKED) return false; // Solo los bloqueos activos pueden expirar.
        long expiresAt = seat.lockTimestamp + TimeUnit.SECONDS.toMillis(seat.lockDurationSeconds); // Calcula el instante límite en milisegundos.
        return System.currentTimeMillis() >= expiresAt; // Considera vencido el bloqueo al llegar al instante límite.
    }

    private void expireSeatIfNeeded(Seat seat) { // Cambia a disponible una silla cuyo bloqueo ya venció.
        if (isLockExpired(seat)) { // Evita modificar sillas disponibles, vendidas o aún retenidas.
            seat.state = SeatState.AVAILABLE; // Devuelve la silla vencida al inventario disponible.
            seat.lockedByClientId = ""; // Elimina la identidad del cliente que ya no la retiene.
            seat.lockTimestamp = 0L; // Limpia la marca temporal del bloqueo anterior.
        }
    }

    private synchronized void expireLocks() { // Serializa la limpieza automática con todas las operaciones RPC.
        expireExpiredSeats(); // Aplica la expiración a cada silla del inventario.
    }

    private void expireExpiredSeats() { // Recorre el inventario para aplicar expiraciones pendientes.
        for (Seat seat : seatsMap.values()) { // Examina cada asiento de forma protegida por el monitor del servicio.
            expireSeatIfNeeded(seat); // Libera el asiento si superó su tiempo de retención.
        }
    }

    private void updateEventCounters() { // Recalcula los contadores usando estados ya normalizados.
        expireExpiredSeats(); // Evita que el aforo informe bloqueos que ya vencieron.
        int avail = 0;
        int locked = 0;
        int booked = 0;
        for (Seat s : seatsMap.values()) {
            if (s.state == SeatState.BOOKED) {
                booked++;
            } else if (s.state == SeatState.LOCKED && !isLockExpired(s)) {
                locked++;
            } else {
                avail++;
            }
        }
        eventInfo.totalSeats = seatsMap.size();
        eventInfo.availableSeats = avail;
        eventInfo.lockedSeats = locked;
        eventInfo.bookedSeats = booked;
    }

    @Override
    public synchronized EventInfo getEventInfo(Current current) { // Protege el inventario y retorna una instantánea del evento.
        updateEventCounters();
        return new EventInfo(eventInfo.id, eventInfo.name, eventInfo.date, eventInfo.venue, eventInfo.totalSeats, eventInfo.availableSeats, eventInfo.lockedSeats, eventInfo.bookedSeats, eventInfo.vipPrice, eventInfo.plateaPrice, eventInfo.generalPrice); // Evita compartir el objeto mutable interno.
    }

    @Override
    public synchronized Seat[] getAllSeats(Current current) { // Crea una instantánea consistente de todo el inventario.
        expireExpiredSeats(); // Actualiza cualquier bloqueo vencido antes de consultar el mapa.
        List<Seat> list = new ArrayList<>(seatsMap.values());
        list.sort(Comparator.comparing((Seat s) -> s.row).thenComparingInt(s -> s.number));
        return list.stream().map(this::copySeat).toArray(Seat[]::new); // Copia cada valor para aislarlo del inventario compartido.
    }

    @Override
    public synchronized Seat[] getSeatsByZone(SeatZone zone, Current current) { // Devuelve una vista consistente y aislada de una zona.
        expireExpiredSeats(); // Refleja la expiración antes de filtrar las sillas.
        List<Seat> list = new ArrayList<>();
        for (Seat s : seatsMap.values()) {
            if (s.zone == zone) {
                list.add(s);
            }
        }
        list.sort(Comparator.comparing((Seat s) -> s.row).thenComparingInt(s -> s.number));
        return list.stream().map(this::copySeat).toArray(Seat[]::new); // Evita exponer instancias mutables internas.
    }

    @Override
    public synchronized Seat getSeat(String seatId, Current current) throws SeatNotFoundException { // Consulta una silla y devuelve una copia independiente.
        expireExpiredSeats(); // Normaliza el estado de expiración antes de consultar la silla.
        Seat seat = seatsMap.get(seatId);
        if (seat == null) {
            throw new SeatNotFoundException("Silla no encontrada en inventario: " + seatId);
        }
        return copySeat(seat); // Impide que el resultado comparta el objeto interno mutable.
    }

    @Override
    public synchronized LockResult lockSeat(String seatId, String clientId, Current current) // Hace atómica la lectura, validación y asignación del bloqueo.
            throws SeatNotFoundException, SeatNotAvailableException {

        if (clientId == null || clientId.isBlank()) { // Rechaza identidades vacías para que cada bloqueo tenga propietario.
            throw new SeatNotAvailableException("El identificador del cliente es obligatorio."); // Informa el dato requerido mediante una excepción RPC existente.
        }
        Seat seat = seatsMap.get(seatId);
        if (seat == null) {
            throw new SeatNotFoundException("Silla no encontrada: " + seatId);
        }

        // Si estaba bloqueada pero expiró el tiempo de retención, liberarla
        if (seat.state == SeatState.LOCKED && isLockExpired(seat)) { // Reconoce la expiración aun si el planificador todavía no se ejecutó.
            System.out.printf("[LOCK-EXPIRED] El bloqueo de la silla %s expiró. Liberando.%n", seatId);
            expireSeatIfNeeded(seat); // Libera y limpia de forma uniforme los metadatos de expiración.
        }

        // Validación de disponibilidad
        if (seat.state != SeatState.AVAILABLE) {
            if (seat.state == SeatState.LOCKED && clientId.equals(seat.lockedByClientId)) {
                // El mismo cliente renueva el bloqueo
                seat.lockTimestamp = System.currentTimeMillis();
                long expiresAt = seat.lockTimestamp + (seat.lockDurationSeconds * 1000L);
                return new LockResult(true, seatId, "Bloqueo renovado con éxito.", expiresAt);
            }
            throw new SeatNotAvailableException(String.format("La silla %s ya no se encuentra disponible (Estado: %s).", seatId, seat.state));
        }

        // Simulación de latencia de red / I/O en base de datos (No remover)
        try { // Conserva la latencia didáctica usada por la prueba concurrente.
            Thread.sleep(25);
        } catch (InterruptedException interrupted) { // Respeta una solicitud de interrupción del hilo RPC.
            Thread.currentThread().interrupt(); // Restaura la bandera de interrupción para no ocultar la señal.
            throw new SeatNotAvailableException("La solicitud de bloqueo fue interrumpida."); // Cancela la operación sin conceder una silla.
        }

        seat.state = SeatState.LOCKED;
        seat.lockedByClientId = clientId;
        seat.lockTimestamp = System.currentTimeMillis();
        seat.lockDurationSeconds = DEFAULT_LOCK_DURATION_SECONDS;

        long expiresAt = seat.lockTimestamp + (DEFAULT_LOCK_DURATION_SECONDS * 1000L);
        System.out.printf("[LOCK-OK] Silla %s bloqueada exitosamente para cliente: %s (expira en %ds)%n",
                seatId, clientId, DEFAULT_LOCK_DURATION_SECONDS);

        return new LockResult(true, seatId, "Silla bloqueada exitosamente.", expiresAt);
    }

    @Override
    public synchronized boolean releaseSeat(String seatId, String clientId, Current current) throws SeatNotFoundException { // Serializa la liberación con bloqueos y confirmaciones.
        Seat seat = seatsMap.get(seatId);
        if (seat == null) {
            throw new SeatNotFoundException("Silla no encontrada: " + seatId);
        }

        // Solo puede liberar el cliente que la bloqueó o si ya expiró
        expireSeatIfNeeded(seat); // Evita conservar un bloqueo vencido al procesar una liberación.
        if (seat.state == SeatState.LOCKED) { // Solo una silla todavía retenida admite liberación voluntaria.
            if (clientId != null && clientId.equals(seat.lockedByClientId)) { // Autoriza la liberación únicamente a su propietario.
                seat.state = SeatState.AVAILABLE;
                seat.lockedByClientId = "";
                seat.lockTimestamp = 0L;
                System.out.printf("[RELEASE-OK] Silla %s liberada por cliente: %s%n", seatId, clientId);
                return true;
            }
        }
        return false;
    }

    @Override
    public synchronized BookingResult confirmBooking(String[] seatIds, String clientId, String customerName, String customerEmail, Current current) // Protege la validación y venta como una sola transacción.
            throws InvalidBookingException, BookingException {

        expireExpiredSeats(); // Garantiza que una compra no use bloqueos que ya expiraron.
        if (seatIds == null || seatIds.length == 0) {
            throw new InvalidBookingException("Debe seleccionar al menos una silla para completar la reserva.");
        }
        if (clientId == null || clientId.isBlank()) { // Requiere una identidad para validar la propiedad de las retenciones.
            throw new InvalidBookingException("El identificador del cliente es obligatorio."); // Evita compras sin sesión de cliente.
        }
        if (customerName == null || customerName.trim().isEmpty()) {
            throw new InvalidBookingException("El nombre del comprador es obligatorio.");
        }
        if (customerEmail == null || !customerEmail.trim().matches("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) { // Valida una estructura básica de correo, no solo la presencia de arroba.
            throw new InvalidBookingException("El correo electrónico del comprador es inválido.");
        }

        double totalAmount = 0.0;
        List<Seat> validatedSeats = new ArrayList<>();
        Set<String> uniqueSeatIds = new HashSet<>(); // Registra los identificadores procesados para impedir cargos duplicados.

        // Paso 1: Validación preliminar de las sillas
        for (String seatId : seatIds) {
            if (seatId == null || !uniqueSeatIds.add(seatId)) { // Rechaza IDs nulos o repetidos antes de calcular el total.
                throw new InvalidBookingException("La reserva contiene identificadores de silla inválidos o duplicados."); // Mantiene el cobro uno a uno con las sillas.
            }
            Seat seat = seatsMap.get(seatId);
            if (seat == null) {
                throw new SeatNotFoundException("Silla no existe: " + seatId);
            }
            if (seat.state == SeatState.BOOKED) {
                throw new BookingException("La silla " + seatId + " ya fue vendida definitivamente.");
            }
            if (seat.state != SeatState.LOCKED) { // Impide confirmar sillas disponibles que el cliente nunca reservó.
                throw new SeatNotAvailableException("La silla " + seatId + " no tiene un bloqueo vigente para esta compra."); // Mantiene el contrato RPC de disponibilidad.
            }
            if (isLockExpired(seat)) { // Comprueba otra vez el tiempo justo antes de confirmar la compra.
                expireSeatIfNeeded(seat); // Devuelve el asiento al inventario cuando el plazo ya terminó.
                throw new LockExpiredException("El bloqueo temporal de la silla " + seatId + " ha expirado."); // Informa que debe reservarse de nuevo.
            }
            if (!clientId.equals(seat.lockedByClientId)) { // Exige que todas las sillas pertenezcan al mismo cliente.
                throw new BookingException("La silla " + seatId + " se encuentra bloqueada por otro comprador."); // Evita confirmar reservas ajenas.
            }
            totalAmount += seat.price;
            validatedSeats.add(seat);
        }

        // Paso 2: Confirmación y cambio a BOOKED dentro del mismo monitor del servicio.
        for (Seat s : validatedSeats) {
            s.state = SeatState.BOOKED;
            s.lockedByClientId = clientId;
        }

        String bookingCode; // Declara el código que se asignará a esta compra.
        do { // Genera un identificador y comprueba su unicidad en el historial actual.
            bookingCode = "RES-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(); // Conserva el formato de reserva solicitado.
        } while (bookingCodeAlreadyExists(bookingCode)); // Reintenta en el improbable caso de colisión.
        long now = System.currentTimeMillis();

        BookingRecord record = new BookingRecord(
                bookingCode,
                eventInfo.id,
                clientId,
                customerName.trim(),
                customerEmail.trim(),
                seatIds.clone(), // Guarda una copia para proteger el historial de mutaciones del arreglo de entrada.
                totalAmount,
                now
        );
        bookingsList.add(record);

        System.out.printf("[BOOKING-CONFIRMED] Código: %s | Cliente: %s (%s) | %d sillas | Total: $%,.0f COP%n",
                bookingCode, customerName, clientId, seatIds.length, totalAmount);

        return new BookingResult(
                true,
                bookingCode,
                "Reserva confirmada con éxito. Disfrute del evento.",
                seatIds.clone(), // Devuelve otra copia para no compartir el arreglo almacenado.
                totalAmount,
                now
        );
    }

    @Override
    public synchronized Seat[] getMyLockedSeats(String clientId, Current current) { // Consulta bloqueos propios desde una instantánea consistente.
        expireExpiredSeats(); // Omite retenciones que hayan vencido justo antes de esta consulta.
        List<Seat> list = new ArrayList<>();
        if (clientId != null && !clientId.isEmpty()) {
            for (Seat s : seatsMap.values()) {
                if (s.state == SeatState.LOCKED && clientId.equals(s.lockedByClientId) && !isLockExpired(s)) {
                    list.add(copySeat(s)); // Desacopla el resultado de la silla mutable interna.
                }
            }
        }
        return list.toArray(new Seat[0]);
    }

    @Override
    public synchronized BookingRecord[] getMyBookings(String clientId, Current current) { // Lee el historial sin competir con confirmaciones.
        List<BookingRecord> list = new ArrayList<>();
        if (clientId != null && !clientId.isEmpty()) {
            for (BookingRecord b : bookingsList) {
                if (clientId.equals(b.clientId)) {
                    list.add(copyBookingRecord(b)); // Retorna registros y arreglos de sillas independientes.
                }
            }
        }
        return list.toArray(new BookingRecord[0]);
    }

    @Override
    public synchronized void resetSeats(Current current) { // Evita reiniciar el mapa durante otra operación de reserva.
        initSeats();
        System.out.println("[RESET] Inventario y reservas reiniciadas a estado de fábrica.");
    }

    private Seat copySeat(Seat seat) { // Construye una instantánea inmutable desde la perspectiva del inventario.
        return new Seat(seat.id, seat.zone, seat.row, seat.number, seat.price, seat.state, seat.lockedByClientId, seat.lockTimestamp, seat.lockDurationSeconds); // Copia todos los campos del contrato Slice.
    }

    private BookingRecord copyBookingRecord(BookingRecord booking) { // Copia un registro sin exponer los arreglos guardados internamente.
        return new BookingRecord(booking.bookingCode, booking.eventId, booking.clientId, booking.customerName, booking.customerEmail, booking.seatIds.clone(), booking.totalAmount, booking.timestamp); // Conserva los datos de compra de forma aislada.
    }

    private boolean bookingCodeAlreadyExists(String bookingCode) { // Busca códigos ya asignados sin capturar variables mutables en una lambda.
        return bookingsList.stream().anyMatch(booking -> booking.bookingCode.equals(bookingCode)); // Indica si el código ya está registrado.
    }

    @Override
    public void close() { // Detiene el hilo de limpieza cuando el servidor o una prueba finalizan.
        expiryExecutor.shutdownNow(); // Cancela las tareas periódicas y libera los recursos del planificador.
    }
}
