package co.icesi.booking.server;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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

public class BookingServiceImpl implements BookingService {

    public static final long DEFAULT_LOCK_DURATION_SECONDS = 90;

    private final EventInfo eventInfo;
    private final Map<String, Seat> seatsMap = new HashMap<>();
    private final List<BookingRecord> bookingsList = new ArrayList<>();

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

    private boolean isLockExpired(Seat seat) {
        if (seat.state != SeatState.LOCKED) return false;
        long elapsedSeconds = (System.currentTimeMillis() - seat.lockTimestamp) / 1000L;
        return elapsedSeconds > seat.lockDurationSeconds;
    }

    private void updateEventCounters() {
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
    public EventInfo getEventInfo(Current current) {
        updateEventCounters();
        return eventInfo;
    }

    @Override
    public Seat[] getAllSeats(Current current) {
        List<Seat> list = new ArrayList<>(seatsMap.values());
        list.sort(Comparator.comparing((Seat s) -> s.row).thenComparingInt(s -> s.number));
        return list.toArray(new Seat[0]);
    }

    @Override
    public Seat[] getSeatsByZone(SeatZone zone, Current current) {
        List<Seat> list = new ArrayList<>();
        for (Seat s : seatsMap.values()) {
            if (s.zone == zone) {
                list.add(s);
            }
        }
        list.sort(Comparator.comparing((Seat s) -> s.row).thenComparingInt(s -> s.number));
        return list.toArray(new Seat[0]);
    }

    @Override
    public Seat getSeat(String seatId, Current current) throws SeatNotFoundException {
        Seat seat = seatsMap.get(seatId);
        if (seat == null) {
            throw new SeatNotFoundException("Silla no encontrada en inventario: " + seatId);
        }
        return seat;
    }

    @Override
    public LockResult lockSeat(String seatId, String clientId, Current current)
            throws SeatNotFoundException, SeatNotAvailableException {

        Seat seat = seatsMap.get(seatId);
        if (seat == null) {
            throw new SeatNotFoundException("Silla no encontrada: " + seatId);
        }

        // Si estaba bloqueada pero expiró el tiempo de retención, liberarla
        if (seat.state == SeatState.LOCKED && isLockExpired(seat)) {
            System.out.printf("[LOCK-EXPIRED] El bloqueo de la silla %s expiró. Liberando.%n", seatId);
            seat.state = SeatState.AVAILABLE;
            seat.lockedByClientId = "";
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
        try {
            Thread.sleep(25);
        } catch (InterruptedException ignored) {
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
    public boolean releaseSeat(String seatId, String clientId, Current current) throws SeatNotFoundException {
        Seat seat = seatsMap.get(seatId);
        if (seat == null) {
            throw new SeatNotFoundException("Silla no encontrada: " + seatId);
        }

        // Solo puede liberar el cliente que la bloqueó o si ya expiró
        if (seat.state == SeatState.LOCKED) {
            if (clientId.equals(seat.lockedByClientId) || isLockExpired(seat)) {
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
    public BookingResult confirmBooking(String[] seatIds, String clientId, String customerName, String customerEmail, Current current)
            throws InvalidBookingException, BookingException {

        if (seatIds == null || seatIds.length == 0) {
            throw new InvalidBookingException("Debe seleccionar al menos una silla para completar la reserva.");
        }
        if (customerName == null || customerName.trim().isEmpty()) {
            throw new InvalidBookingException("El nombre del comprador es obligatorio.");
        }
        if (customerEmail == null || !customerEmail.contains("@")) {
            throw new InvalidBookingException("El correo electrónico del comprador es inválido.");
        }

        double totalAmount = 0.0;
        List<Seat> validatedSeats = new ArrayList<>();

        // Paso 1: Validación preliminar de las sillas
        for (String seatId : seatIds) {
            Seat seat = seatsMap.get(seatId);
            if (seat == null) {
                throw new SeatNotFoundException("Silla no existe: " + seatId);
            }
            if (seat.state == SeatState.BOOKED) {
                throw new BookingException("La silla " + seatId + " ya fue vendida definitivamente.");
            }
            if (seat.state == SeatState.LOCKED) {
                if (isLockExpired(seat)) {
                    throw new LockExpiredException("El bloqueo temporal de la silla " + seatId + " ha expirado.");
                }
                if (!clientId.equals(seat.lockedByClientId)) {
                    throw new BookingException("La silla " + seatId + " se encuentra bloqueada por otro comprador.");
                }
            }
            totalAmount += seat.price;
            validatedSeats.add(seat);
        }

        // Paso 2: Confirmación y cambio a BOOKED (Proceso no atómico)
        for (Seat s : validatedSeats) {
            s.state = SeatState.BOOKED;
            s.lockedByClientId = clientId;
        }

        String bookingCode = "RES-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        long now = System.currentTimeMillis();

        BookingRecord record = new BookingRecord(
                bookingCode,
                eventInfo.id,
                clientId,
                customerName.trim(),
                customerEmail.trim(),
                seatIds,
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
                seatIds,
                totalAmount,
                now
        );
    }

    @Override
    public Seat[] getMyLockedSeats(String clientId, Current current) {
        List<Seat> list = new ArrayList<>();
        if (clientId != null && !clientId.isEmpty()) {
            for (Seat s : seatsMap.values()) {
                if (s.state == SeatState.LOCKED && clientId.equals(s.lockedByClientId) && !isLockExpired(s)) {
                    list.add(s);
                }
            }
        }
        return list.toArray(new Seat[0]);
    }

    @Override
    public BookingRecord[] getMyBookings(String clientId, Current current) {
        List<BookingRecord> list = new ArrayList<>();
        if (clientId != null && !clientId.isEmpty()) {
            for (BookingRecord b : bookingsList) {
                if (clientId.equals(b.clientId)) {
                    list.add(b);
                }
            }
        }
        return list.toArray(new BookingRecord[0]);
    }

    @Override
    public void resetSeats(Current current) {
        initSeats();
        System.out.println("[RESET] Inventario y reservas reiniciadas a estado de fábrica.");
    }
}
