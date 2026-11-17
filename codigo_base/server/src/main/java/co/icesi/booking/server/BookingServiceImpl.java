package co.icesi.booking.server; // Ubica la implementación del contrato Slice en el módulo del servidor.

import java.util.ArrayList; // Crea resultados ordenados y colecciones de compra.
import java.util.Comparator; // Ordena sillas por fila y número.
import java.util.HashMap; // Indexa el inventario por identificador de silla.
import java.util.HashSet; // Detecta IDs duplicados antes de cobrar una reserva.
import java.util.List; // Conserva asientos y reservas en estructuras recorribles.
import java.util.Map; // Expone el inventario como pares ID-silla.
import java.util.Set; // Registra los identificadores únicos de una compra.
import java.util.UUID; // Genera códigos de comprobante con alta entropía.
import java.util.concurrent.Executors; // Crea el planificador de expiraciones.
import java.util.concurrent.ScheduledExecutorService; // Programa la liberación periódica de bloqueos.
import java.util.concurrent.TimeUnit; // Convierte duraciones de bloqueo a milisegundos.

import com.zeroc.Ice.Current; // Recibe el contexto de despacho de cada llamada remota.

import BookingApp.BookingException; // Representa fallos generales de una reserva.
import BookingApp.BookingRecord; // Conserva los comprobantes guardados.
import BookingApp.BookingResult; // Devuelve el resultado de confirmar una compra.
import BookingApp.BookingService; // Define las operaciones RPC que debe implementar el servidor.
import BookingApp.EventInfo; // Publica metadata y contadores del evento.
import BookingApp.InvalidBookingException; // Rechaza compras con datos inválidos.
import BookingApp.LockExpiredException; // Informa cuando un bloqueo venció antes del checkout.
import BookingApp.LockResult; // Devuelve el resultado y expiración de un bloqueo.
import BookingApp.Seat; // Representa una silla del auditorio.
import BookingApp.SeatNotAvailableException; // Rechaza asientos ocupados o sin retención válida.
import BookingApp.SeatNotFoundException; // Informa IDs que no existen en el inventario.
import BookingApp.SeatState; // Define los estados disponibles, bloqueados y vendidos.
import BookingApp.SeatZone; // Define las zonas y tarifas del recinto.

public class BookingServiceImpl implements BookingService, AutoCloseable { // Expone el servicio RPC y administra el limpiador de bloqueos.

    public static final long DEFAULT_LOCK_DURATION_SECONDS = 90; // Define la duración académica solicitada para cada bloqueo.

    private final EventInfo eventInfo; // Conserva los datos compartidos del concierto.
    private final long lockDurationSeconds; // Permite usar plazos cortos en pruebas sin alterar el plazo normal.
    private final Map<String, Seat> seatsMap = new HashMap<>(); // Mantiene el estado mutable del inventario.
    private final List<BookingRecord> bookingsList = new ArrayList<>(); // Mantiene el historial de compras en memoria.
    private final ScheduledExecutorService expiryExecutor = Executors.newSingleThreadScheduledExecutor(task -> { // Crea un hilo dedicado a expirar bloqueos vencidos.
        Thread thread = new Thread(task, "booking-lock-expirer"); // Da un nombre reconocible al hilo de mantenimiento.
        thread.setDaemon(true); // Permite que la JVM termine aunque el hilo de mantenimiento siga activo.
        return thread; // Entrega el hilo preparado al planificador.
    }); // Finaliza la creación del hilo de mantenimiento.

    public BookingServiceImpl() { // Crea la instancia de producción con el plazo definido por el enunciado.
        this(DEFAULT_LOCK_DURATION_SECONDS); // Mantiene 90 segundos para el servidor real.
    } // Finaliza el constructor de producción.

    BookingServiceImpl(long lockDurationSeconds) { // Construye el servicio con una duración configurable para las pruebas.
        if (lockDurationSeconds <= 0) { // Rechaza una duración inválida antes de inicializar el inventario.
            throw new IllegalArgumentException("La duración del bloqueo debe ser positiva."); // Expone claramente el error de configuración.
        } // Finaliza la validación del plazo configurado.
        this.lockDurationSeconds = lockDurationSeconds; // Guarda el plazo que se aplicará a cada silla.
        this.eventInfo = new EventInfo( // Construye la metadata inicial del evento.
                "EVT-2026-LIVE", // Asigna el identificador público del evento.
                "Concierto Icesi Live Rock & Pop 2026", // Asigna el nombre que verá el cliente.
                "2026-11-28 20:00", // Asigna fecha y hora del espectáculo.
                "Auditorio Manuelita - Universidad Icesi", // Asigna el lugar del concierto.
                90, 90, 0, 0, // Inicia el aforo con 90 sillas disponibles y ninguna ocupada.
                120000.0, 75000.0, 45000.0 // Configura las tarifas VIP, Platea y General.
        ); // Finaliza la creación del evento.
        initSeats(); // Construye el inventario inicial de las tres zonas.
        expiryExecutor.scheduleAtFixedRate(this::expireLocks, 1, 1, TimeUnit.SECONDS); // Libera automáticamente las sillas vencidas cada segundo.
    } // Finaliza la inicialización del servicio y su planificador.

    private void initSeats() { // Restaura inventario e historial a su estado inicial.
        seatsMap.clear(); // Elimina sillas y estados previamente almacenados.
        bookingsList.clear(); // Elimina comprobantes del historial en memoria.

        createSeatsForZone("A", 1, 10, SeatZone.ZONEVIP, 120000.0); // Crea la primera fila VIP.
        createSeatsForZone("B", 1, 10, SeatZone.ZONEVIP, 120000.0); // Crea la segunda fila VIP.

        createSeatsForZone("C", 1, 10, SeatZone.ZONEPLATEA, 75000.0); // Crea la primera fila de Platea.
        createSeatsForZone("D", 1, 10, SeatZone.ZONEPLATEA, 75000.0); // Crea la segunda fila de Platea.
        createSeatsForZone("E", 1, 10, SeatZone.ZONEPLATEA, 75000.0); // Crea la tercera fila de Platea.

        createSeatsForZone("F", 1, 10, SeatZone.ZONEGENERAL, 45000.0); // Crea la primera fila General.
        createSeatsForZone("G", 1, 10, SeatZone.ZONEGENERAL, 45000.0); // Crea la segunda fila General.
        createSeatsForZone("H", 1, 10, SeatZone.ZONEGENERAL, 45000.0); // Crea la tercera fila General.
        createSeatsForZone("I", 1, 10, SeatZone.ZONEGENERAL, 45000.0); // Crea la cuarta fila General.

        updateEventCounters(); // Alinea contadores con el inventario recién creado.
        System.out.printf("[INFO] Inventario inicializado con %d sillas en 3 zonas (VIP, Platea, General).%n", seatsMap.size()); // Registra el aforo construido.
    } // Finaliza la inicialización del inventario.

    private void createSeatsForZone(String row, int startNum, int endNum, SeatZone zone, double price) { // Genera las sillas numeradas de una fila.
        for (int num = startNum; num <= endNum; num++) { // Recorre los números permitidos en la fila.
            String seatId = String.format("%s-%d", row, num); // Construye el ID que usarán clientes y RPC.
            Seat seat = new Seat( // Crea una silla libre con los datos de su zona.
                    seatId, // Guarda el identificador de la silla.
                    zone, // Guarda la zona para filtrado y precios.
                    row, // Guarda la letra de la fila.
                    num, // Guarda la posición numérica de la silla.
                    price, // Guarda la tarifa de esta ubicación.
                    SeatState.AVAILABLE, // Inicia cada silla en estado disponible.
                    "", // Inicia sin cliente retenedor.
                    0L, // Inicia sin marca de tiempo de bloqueo.
                    lockDurationSeconds // Aplica la duración configurada al contrato de la silla.
            ); // Finaliza la creación de la silla.
            seatsMap.put(seatId, seat); // Indexa la silla para búsquedas posteriores.
        } // Finaliza la generación de la fila.
    } // Finaliza la creación de sillas por zona.

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
        } // Finaliza la liberación de los metadatos de una silla vencida.
    } // Finaliza la verificación individual de expiración.

    private synchronized void expireLocks() { // Serializa la limpieza automática con todas las operaciones RPC.
        expireExpiredSeats(); // Aplica la expiración a cada silla del inventario.
    } // Finaliza la tarea de limpieza periódica sincronizada.

    private void expireExpiredSeats() { // Recorre el inventario para aplicar expiraciones pendientes.
        for (Seat seat : seatsMap.values()) { // Examina cada asiento de forma protegida por el monitor del servicio.
            expireSeatIfNeeded(seat); // Libera el asiento si superó su tiempo de retención.
        } // Finaliza el recorrido de asientos para la limpieza.
    } // Finaliza la expiración de bloqueos vencidos.

    private void updateEventCounters() { // Recalcula los contadores usando estados ya normalizados.
        expireExpiredSeats(); // Evita que el aforo informe bloqueos que ya vencieron.
        int avail = 0; // Cuenta sillas disponibles durante el recorrido.
        int locked = 0; // Cuenta bloqueos que todavía no vencen.
        int booked = 0; // Cuenta ventas definitivas.
        for (Seat s : seatsMap.values()) { // Clasifica cada silla según su estado actual.
            if (s.state == SeatState.BOOKED) { // Separa las sillas vendidas.
                booked++; // Suma una venta definitiva.
            } else if (s.state == SeatState.LOCKED && !isLockExpired(s)) { // Separa retenciones activas de expiradas.
                locked++; // Suma una silla temporalmente retenida.
            } else { // Cuenta disponibles y bloqueos que ya vencieron.
                avail++; // Suma una silla que otros clientes pueden reservar.
            } // Finaliza la clasificación del asiento.
        } // Finaliza el recorrido del inventario.
        eventInfo.totalSeats = seatsMap.size(); // Actualiza el aforo total del recinto.
        eventInfo.availableSeats = avail; // Actualiza la cantidad de sillas disponibles.
        eventInfo.lockedSeats = locked; // Actualiza la cantidad de sillas retenidas.
        eventInfo.bookedSeats = booked; // Actualiza la cantidad de sillas vendidas.
    } // Finaliza el recálculo de contadores.

    @Override // Implementa la operación de consulta del evento definida en Slice.
    public synchronized EventInfo getEventInfo(Current current) { // Protege el inventario y retorna una instantánea del evento.
        updateEventCounters(); // Sincroniza los contadores con los estados vigentes.
        return new EventInfo(eventInfo.id, eventInfo.name, eventInfo.date, eventInfo.venue, eventInfo.totalSeats, eventInfo.availableSeats, eventInfo.lockedSeats, eventInfo.bookedSeats, eventInfo.vipPrice, eventInfo.plateaPrice, eventInfo.generalPrice); // Evita compartir el objeto mutable interno.
    } // Finaliza la consulta de información del evento.

    @Override // Implementa la operación de consulta del inventario definida en Slice.
    public synchronized Seat[] getAllSeats(Current current) { // Crea una instantánea consistente de todo el inventario.
        expireExpiredSeats(); // Actualiza cualquier bloqueo vencido antes de consultar el mapa.
        List<Seat> list = new ArrayList<>(seatsMap.values()); // Copia las referencias para poder ordenar la vista.
        list.sort(Comparator.comparing((Seat s) -> s.row).thenComparingInt(s -> s.number)); // Ordena de forma estable por fila y número.
        return list.stream().map(this::copySeat).toArray(Seat[]::new); // Copia cada valor para aislarlo del inventario compartido.
    } // Finaliza la consulta completa del inventario.

    @Override // Implementa la operación de filtrado por zona definida en Slice.
    public synchronized Seat[] getSeatsByZone(SeatZone zone, Current current) { // Devuelve una vista consistente y aislada de una zona.
        expireExpiredSeats(); // Refleja la expiración antes de filtrar las sillas.
        List<Seat> list = new ArrayList<>(); // Prepara el resultado de la zona solicitada.
        for (Seat s : seatsMap.values()) { // Recorre el inventario completo.
            if (s.zone == zone) { // Conserva solamente sillas de la zona elegida.
                list.add(s); // Añade la silla coincidente al resultado.
            } // Finaliza la comparación de zona.
        } // Finaliza el filtrado del inventario.
        list.sort(Comparator.comparing((Seat s) -> s.row).thenComparingInt(s -> s.number)); // Mantiene el mismo orden del mapa completo.
        return list.stream().map(this::copySeat).toArray(Seat[]::new); // Evita exponer instancias mutables internas.
    } // Finaliza la consulta filtrada por zona.

    @Override // Implementa la operación de consulta individual definida en Slice.
    public synchronized Seat getSeat(String seatId, Current current) throws SeatNotFoundException { // Consulta una silla y devuelve una copia independiente.
        expireExpiredSeats(); // Normaliza el estado de expiración antes de consultar la silla.
        Seat seat = seatsMap.get(seatId); // Busca el ID dentro del inventario protegido.
        if (seat == null) { // Comprueba si el identificador no existe.
            throw new SeatNotFoundException("Silla no encontrada en inventario: " + seatId); // Informa el ID inválido al cliente.
        } // Finaliza la validación del identificador.
        return copySeat(seat); // Impide que el resultado comparta el objeto interno mutable.
    } // Finaliza la consulta individual de silla.

    @Override // Implementa la operación de bloqueo definida en Slice.
    public synchronized LockResult lockSeat(String seatId, String clientId, Current current) // Hace atómica la lectura, validación y asignación del bloqueo.
            throws SeatNotFoundException, SeatNotAvailableException { // Expone los errores de silla inexistente o no disponible.

        if (clientId == null || clientId.isBlank()) { // Rechaza identidades vacías para que cada bloqueo tenga propietario.
            throw new SeatNotAvailableException("El identificador del cliente es obligatorio."); // Informa el dato requerido mediante una excepción RPC existente.
        } // Finaliza la validación de identidad.
        Seat seat = seatsMap.get(seatId); // Localiza la silla antes de cambiar su estado.
        if (seat == null) { // Detecta un ID que no pertenece al inventario.
            throw new SeatNotFoundException("Silla no encontrada: " + seatId); // Informa que no se puede reservar ese ID.
        } // Finaliza la validación de silla existente.

        // Si estaba bloqueada pero expiró el tiempo de retención, liberarla
        if (seat.state == SeatState.LOCKED && isLockExpired(seat)) { // Reconoce la expiración aun si el planificador todavía no se ejecutó.
            System.out.printf("[LOCK-EXPIRED] El bloqueo de la silla %s expiró. Liberando.%n", seatId); // Registra la liberación por tiempo.
            expireSeatIfNeeded(seat); // Libera y limpia de forma uniforme los metadatos de expiración.
        } // Finaliza la normalización previa al bloqueo.

        // Validación de disponibilidad
        if (seat.state != SeatState.AVAILABLE) { // Rechaza una silla retenida o vendida por otra operación.
            if (seat.state == SeatState.LOCKED && clientId.equals(seat.lockedByClientId)) { // Reconoce la renovación del mismo cliente.
                // El mismo cliente renueva el bloqueo
                seat.lockTimestamp = System.currentTimeMillis(); // Reinicia el plazo de retención del propietario actual.
                long expiresAt = seat.lockTimestamp + (seat.lockDurationSeconds * 1000L); // Calcula el nuevo instante de expiración.
                return new LockResult(true, seatId, "Bloqueo renovado con éxito.", expiresAt); // Confirma la renovación al cliente.
            } // Finaliza la excepción para propietario que renueva.
            throw new SeatNotAvailableException(String.format("La silla %s ya no se encuentra disponible (Estado: %s).", seatId, seat.state)); // Rechaza a otros clientes.
        } // Finaliza la validación de disponibilidad.

        // Simulación de latencia de red / I/O en base de datos (No remover)
        try { // Conserva la latencia didáctica usada por la prueba concurrente.
            Thread.sleep(25); // Simula latencia mientras el monitor evita que otra solicitud tome la silla.
        } catch (InterruptedException interrupted) { // Respeta una solicitud de interrupción del hilo RPC.
            Thread.currentThread().interrupt(); // Restaura la bandera de interrupción para no ocultar la señal.
            throw new SeatNotAvailableException("La solicitud de bloqueo fue interrumpida."); // Cancela la operación sin conceder una silla.
        } // Finaliza la simulación de latencia.

        seat.state = SeatState.LOCKED; // Marca la silla como retenida para impedir otra reserva.
        seat.lockedByClientId = clientId; // Asocia el bloqueo con el propietario autorizado.
        seat.lockTimestamp = System.currentTimeMillis(); // Inicia el plazo de retención desde el servidor.
        seat.lockDurationSeconds = lockDurationSeconds; // Aplica el plazo configurado al bloqueo recién creado.

        long expiresAt = seat.lockTimestamp + (lockDurationSeconds * 1000L); // Calcula el vencimiento con el mismo plazo del asiento.
        System.out.printf( // Registra la confirmación del bloqueo en la consola del servidor.
                "[LOCK-OK] Silla %s bloqueada exitosamente para cliente: %s (expira en %ds)%n", // Define el mensaje de éxito del bloqueo.
                seatId, clientId, lockDurationSeconds); // Registra el bloqueo y su duración para diagnóstico.

        return new LockResult(true, seatId, "Silla bloqueada exitosamente.", expiresAt); // Devuelve estado y vencimiento al cliente.
    } // Finaliza el bloqueo atómico de una silla.

    @Override // Implementa la operación de liberación definida en Slice.
    public synchronized boolean releaseSeat(String seatId, String clientId, Current current) throws SeatNotFoundException { // Serializa la liberación con bloqueos y confirmaciones.
        Seat seat = seatsMap.get(seatId); // Busca la silla solicitada en el inventario.
        if (seat == null) { // Comprueba si el código no existe.
            throw new SeatNotFoundException("Silla no encontrada: " + seatId); // Informa al cliente del ID inválido.
        } // Finaliza la búsqueda de silla.

        // Solo puede liberar el cliente que la bloqueó o si ya expiró
        expireSeatIfNeeded(seat); // Evita conservar un bloqueo vencido al procesar una liberación.
        if (seat.state == SeatState.LOCKED) { // Solo una silla todavía retenida admite liberación voluntaria.
            if (clientId != null && clientId.equals(seat.lockedByClientId)) { // Autoriza la liberación únicamente a su propietario.
                seat.state = SeatState.AVAILABLE; // Devuelve la silla a cualquier comprador.
                seat.lockedByClientId = ""; // Quita la identidad del propietario anterior.
                seat.lockTimestamp = 0L; // Limpia la marca de tiempo ya innecesaria.
                System.out.printf("[RELEASE-OK] Silla %s liberada por cliente: %s%n", seatId, clientId); // Registra quién liberó la silla.
                return true; // Informa que la operación cambió el inventario.
            } // Finaliza la liberación autorizada.
        } // Finaliza la validación del estado retenido.
        return false; // Informa que no había un bloqueo propio vigente para liberar.
    } // Finaliza la liberación voluntaria.

    @Override // Implementa la operación de compra definida en Slice.
    public synchronized BookingResult confirmBooking(String[] seatIds, String clientId, String customerName, String customerEmail, Current current) // Protege la validación y venta como una sola transacción.
            throws InvalidBookingException, BookingException { // Expone las validaciones de compra del contrato.

        expireExpiredSeats(); // Garantiza que una compra no use bloqueos que ya expiraron.
        if (seatIds == null || seatIds.length == 0) { // Impide confirmar una compra sin asientos.
            throw new InvalidBookingException("Debe seleccionar al menos una silla para completar la reserva."); // Expone el requisito al cliente.
        } // Finaliza la validación de lista vacía.
        if (clientId == null || clientId.isBlank()) { // Requiere una identidad para validar la propiedad de las retenciones.
            throw new InvalidBookingException("El identificador del cliente es obligatorio."); // Evita compras sin sesión de cliente.
        } // Finaliza la validación de identidad del comprador.
        if (customerName == null || customerName.trim().isEmpty()) { // Exige un nombre de contacto legible.
            throw new InvalidBookingException("El nombre del comprador es obligatorio."); // Informa que el nombre no puede omitirse.
        } // Finaliza la validación del nombre.
        if (customerEmail == null || !customerEmail.trim().matches("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) { // Valida una estructura básica de correo, no solo la presencia de arroba.
            throw new InvalidBookingException("El correo electrónico del comprador es inválido."); // Rechaza direcciones incompletas.
        }

        double totalAmount = 0.0; // Acumula el valor únicamente de asientos validados.
        List<Seat> validatedSeats = new ArrayList<>(); // Conserva asientos para venderlos después de validar todos.
        Set<String> uniqueSeatIds = new HashSet<>(); // Registra los identificadores procesados para impedir cargos duplicados.

        // Paso 1: Validación preliminar de las sillas
        for (String seatId : seatIds) { // Verifica todas las sillas antes de cambiar cualquier estado.
            if (seatId == null || !uniqueSeatIds.add(seatId)) { // Rechaza IDs nulos o repetidos antes de calcular el total.
                throw new InvalidBookingException("La reserva contiene identificadores de silla inválidos o duplicados."); // Mantiene el cobro uno a uno con las sillas.
            } // Finaliza la validación de IDs únicos.
            Seat seat = seatsMap.get(seatId); // Localiza cada silla dentro del inventario.
            if (seat == null) { // Comprueba que el ID pertenezca al recinto.
                throw new SeatNotFoundException("Silla no existe: " + seatId); // Rechaza referencias desconocidas.
            } // Finaliza la validación de silla existente.
            if (seat.state == SeatState.BOOKED) { // Impide la reventa de una compra confirmada.
                throw new BookingException("La silla " + seatId + " ya fue vendida definitivamente."); // Informa que el asiento ya tiene comprador.
            } // Finaliza la validación de venta previa.
            if (seat.state != SeatState.LOCKED) { // Impide confirmar sillas disponibles que el cliente nunca reservó.
                throw new SeatNotAvailableException("La silla " + seatId + " no tiene un bloqueo vigente para esta compra."); // Mantiene el contrato RPC de disponibilidad.
            } // Finaliza la validación de bloqueo activo.
            if (isLockExpired(seat)) { // Comprueba otra vez el tiempo justo antes de confirmar la compra.
                expireSeatIfNeeded(seat); // Devuelve el asiento al inventario cuando el plazo ya terminó.
                throw new LockExpiredException("El bloqueo temporal de la silla " + seatId + " ha expirado."); // Informa que debe reservarse de nuevo.
            } // Finaliza la validación de expiración.
            if (!clientId.equals(seat.lockedByClientId)) { // Exige que todas las sillas pertenezcan al mismo cliente.
                throw new BookingException("La silla " + seatId + " se encuentra bloqueada por otro comprador."); // Evita confirmar reservas ajenas.
            } // Finaliza la validación de propiedad.
            totalAmount += seat.price; // Incluye el precio validado una sola vez en el total.
            validatedSeats.add(seat); // Guarda el asiento para la fase de confirmación atómica.
        } // Finaliza la validación de toda la selección.

        // Paso 2: Confirmación y cambio a BOOKED dentro del mismo monitor del servicio.
        for (Seat s : validatedSeats) { // Cambia a vendido solo después de validar todas las sillas.
            s.state = SeatState.BOOKED; // Marca la silla como venta definitiva.
            s.lockedByClientId = clientId; // Conserva quién compró la silla.
        } // Finaliza la confirmación de los asientos.

        String bookingCode; // Declara el código que se asignará a esta compra.
        do { // Genera un identificador y comprueba su unicidad en el historial actual.
            bookingCode = "RES-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(); // Conserva el formato de reserva solicitado.
        } while (bookingCodeAlreadyExists(bookingCode)); // Reintenta en el improbable caso de colisión.
        long now = System.currentTimeMillis(); // Guarda el instante de confirmación para el historial.

        BookingRecord record = new BookingRecord( // Crea el comprobante permanente en memoria.
                bookingCode, // Vincula el código único de la compra.
                eventInfo.id, // Vincula el evento comprado.
                clientId, // Vincula el comprobante al cliente.
                customerName.trim(), // Guarda el nombre sin espacios al inicio o al final.
                customerEmail.trim(), // Guarda el correo ya normalizado.
                seatIds.clone(), // Guarda una copia para proteger el historial de mutaciones del arreglo de entrada.
                totalAmount, // Guarda el total calculado con precios de inventario.
                now // Guarda la marca temporal de la compra.
        ); // Finaliza la creación del comprobante.
        bookingsList.add(record); // Registra la compra para futuras consultas del cliente.

        System.out.printf( // Registra el comprobante y el total confirmados por el servidor.
                "[BOOKING-CONFIRMED] Código: %s | Cliente: %s (%s) | %d sillas | Total: $%,.0f COP%n", // Define el comprobante del total vendido.
                bookingCode, customerName, clientId, seatIds.length, totalAmount); // Registra el resultado financiero de la compra.

        return new BookingResult( // Prepara el comprobante RPC que recibirá el comprador.
                true, // Indica que la operación terminó correctamente.
                bookingCode, // Devuelve el código único de la reserva.
                "Reserva confirmada con éxito. Disfrute del evento.", // Incluye un mensaje de confirmación.
                seatIds.clone(), // Devuelve otra copia para no compartir el arreglo almacenado.
                totalAmount, // Devuelve el valor definitivo cobrado.
                now // Devuelve el instante de compra.
        ); // Finaliza el resultado de la operación.
    } // Finaliza la compra atómica.

    @Override // Implementa la consulta de bloqueos propios definida en Slice.
    public synchronized Seat[] getMyLockedSeats(String clientId, Current current) { // Consulta bloqueos propios desde una instantánea consistente.
        expireExpiredSeats(); // Omite retenciones que hayan vencido justo antes de esta consulta.
        List<Seat> list = new ArrayList<>(); // Prepara una lista independiente de bloqueos propios.
        if (clientId != null && !clientId.isEmpty()) { // Omite consultas sin identidad de cliente.
            for (Seat s : seatsMap.values()) { // Examina el inventario para encontrar bloqueos propios.
                if (s.state == SeatState.LOCKED && clientId.equals(s.lockedByClientId) && !isLockExpired(s)) { // Conserva solo retenciones vigentes de esta sesión.
                    list.add(copySeat(s)); // Desacopla el resultado de la silla mutable interna.
                } // Finaliza la selección de una retención propia.
            } // Finaliza la búsqueda en el inventario.
        } // Finaliza la validación de la identidad.
        return list.toArray(new Seat[0]); // Devuelve la colección en el formato del contrato Slice.
    } // Finaliza la consulta de bloqueos propios.

    @Override // Implementa la consulta del historial definida en Slice.
    public synchronized BookingRecord[] getMyBookings(String clientId, Current current) { // Lee el historial sin competir con confirmaciones.
        List<BookingRecord> list = new ArrayList<>(); // Prepara una lista aislada para el resultado RPC.
        if (clientId != null && !clientId.isEmpty()) { // Evita buscar historial para una identidad vacía.
            for (BookingRecord b : bookingsList) { // Recorre los comprobantes guardados.
                if (clientId.equals(b.clientId)) { // Selecciona solo compras de la sesión solicitante.
                    list.add(copyBookingRecord(b)); // Retorna registros y arreglos de sillas independientes.
                } // Finaliza la comparación de propietario.
            } // Finaliza el recorrido del historial.
        } // Finaliza la validación de identidad.
        return list.toArray(new BookingRecord[0]); // Devuelve el historial con el tipo esperado por Slice.
    } // Finaliza la consulta de compras del cliente.

    @Override // Implementa el reinicio del inventario de laboratorio definido en Slice.
    public synchronized void resetSeats(Current current) { // Evita reiniciar el mapa durante otra operación de reserva.
        initSeats(); // Restablece sillas, estados, contadores y comprobantes.
        System.out.println("[RESET] Inventario y reservas reiniciadas a estado de fábrica."); // Registra el reinicio del laboratorio.
    } // Finaliza la operación de reinicio.

    private Seat copySeat(Seat seat) { // Construye una instantánea inmutable desde la perspectiva del inventario.
        return new Seat(seat.id, seat.zone, seat.row, seat.number, seat.price, seat.state, seat.lockedByClientId, seat.lockTimestamp, seat.lockDurationSeconds); // Copia todos los campos del contrato Slice.
    } // Finaliza la copia defensiva de silla.

    private BookingRecord copyBookingRecord(BookingRecord booking) { // Copia un registro sin exponer los arreglos guardados internamente.
        return new BookingRecord(booking.bookingCode, booking.eventId, booking.clientId, booking.customerName, booking.customerEmail, booking.seatIds.clone(), booking.totalAmount, booking.timestamp); // Conserva los datos de compra de forma aislada.
    } // Finaliza la copia defensiva del comprobante.

    private boolean bookingCodeAlreadyExists(String bookingCode) { // Busca códigos ya asignados sin capturar variables mutables en una lambda.
        return bookingsList.stream().anyMatch(booking -> booking.bookingCode.equals(bookingCode)); // Indica si el código ya está registrado.
    } // Finaliza la búsqueda de códigos en el historial.

    @Override // Implementa la liberación del planificador al cerrar el servicio.
    public void close() { // Detiene el hilo de limpieza cuando el servidor o una prueba finalizan.
        expiryExecutor.shutdownNow(); // Cancela las tareas periódicas y libera los recursos del planificador.
    } // Finaliza el cierre de recursos del servicio.
} // Finaliza la implementación del contrato RPC.
