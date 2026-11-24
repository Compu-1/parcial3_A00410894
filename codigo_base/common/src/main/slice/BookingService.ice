// La carpeta slice define el contrato RPC compartido que Ice genera para cada lenguaje.
module BookingApp { // Agrupa los tipos de datos y el servicio remoto de reservas.

    enum SeatZone { // Enumera las ubicaciones disponibles en el auditorio.
        ZONEVIP, // Identifica la zona de mayor tarifa.
        ZONEPLATEA, // Identifica la zona intermedia del recinto.
        ZONEGENERAL // Identifica la zona de tarifa general.
    }; // Finaliza la definición de zonas.

    enum SeatState { // Define los estados posibles de una silla.
        AVAILABLE, // Indica que cualquier cliente puede reservarla.
        LOCKED, // Indica que un cliente la retiene temporalmente.
        BOOKED // Indica que la compra ya fue confirmada.
    }; // Finaliza la definición de estados.

    struct Seat { // Transporta el estado completo de una silla.
        string id; // Contiene el identificador público de la silla.
        SeatZone zone; // Contiene la zona tarifaria de la silla.
        string row; // Contiene la letra de la fila.
        int number; // Contiene el número de la posición en la fila.
        double price; // Contiene el precio de la ubicación.
        SeatState state; // Contiene el estado actual del inventario.
        string lockedByClientId; // Identifica al cliente que mantiene el bloqueo.
        long lockTimestamp; // Guarda el instante de inicio del bloqueo en milisegundos.
        long lockDurationSeconds; // Guarda la duración configurada del bloqueo.
    }; // Finaliza el tipo de dato Seat.

    sequence<Seat> SeatList; // Permite transportar listas de sillas en una llamada RPC.
    sequence<string> StringSeq; // Permite transportar listas de IDs de asiento.

    struct EventInfo { // Describe el concierto y sus contadores de aforo.
        string id; // Identifica de forma única el evento.
        string name; // Contiene el nombre visible del concierto.
        string date; // Contiene fecha y hora del espectáculo.
        string venue; // Contiene el recinto donde se realiza el evento.
        int totalSeats; // Informa la cantidad total de sillas.
        int availableSeats; // Informa cuántas sillas están disponibles.
        int lockedSeats; // Informa cuántas sillas tienen retención vigente.
        int bookedSeats; // Informa cuántas sillas ya fueron vendidas.
        double vipPrice; // Informa la tarifa de la zona VIP.
        double plateaPrice; // Informa la tarifa de Platea.
        double generalPrice; // Informa la tarifa de General.
    }; // Finaliza el tipo de metadata del evento.

    struct BookingRecord { // Conserva los datos de una compra confirmada.
        string bookingCode; // Contiene el código único del comprobante.
        string eventId; // Identifica el evento comprado.
        string clientId; // Identifica al cliente titular de la reserva.
        string customerName; // Guarda el nombre de facturación.
        string customerEmail; // Guarda el correo de contacto.
        StringSeq seatIds; // Enumera las sillas incluidas en la compra.
        double totalAmount; // Guarda el valor total facturado.
        long timestamp; // Guarda el instante de confirmación en milisegundos.
    }; // Finaliza el tipo de dato BookingRecord.

    sequence<BookingRecord> BookingList; // Permite devolver historial de compras.

    struct LockResult { // Comunica el resultado de retener una silla.
        bool success; // Indica si el bloqueo fue adquirido o renovado.
        string seatId; // Identifica la silla solicitada.
        string message; // Explica el resultado al cliente.
        long expiresAt; // Indica el instante de vencimiento en milisegundos.
    }; // Finaliza el resultado de bloqueo.

    sequence<LockResult> LockResultList; // Permite transportar varios resultados de bloqueo.

    struct BookingResult { // Comunica el resultado de confirmar una compra.
        bool success; // Indica si se confirmó la compra.
        string bookingCode; // Devuelve el código asignado al comprobante.
        string message; // Explica el resultado de la operación.
        StringSeq seatIds; // Devuelve las sillas confirmadas.
        double totalAmount; // Devuelve el importe calculado por el servidor.
        long timestamp; // Devuelve el instante en que se confirmó la compra.
    }; // Finaliza el resultado de compra.

    exception BookingException { // Define el error base de las operaciones de compra.
        string reason; // Describe el motivo que se muestra al cliente.
    }; // Finaliza la excepción base.

    exception SeatNotFoundException extends BookingException {}; // Señala que el ID de silla no existe.
    exception SeatNotAvailableException extends BookingException {}; // Señala que una silla no está disponible.
    exception LockExpiredException extends BookingException {}; // Señala que venció la retención temporal.
    exception InvalidBookingException extends BookingException {}; // Señala datos de compra inválidos.

    interface BookingService { // Publica las operaciones RPC usadas por clientes Java y navegador.
        EventInfo getEventInfo(); // Consulta metadata, tarifas y contadores del evento.
        SeatList getAllSeats(); // Consulta el inventario completo de sillas.
        SeatList getSeatsByZone(SeatZone zone); // Consulta sillas filtradas por zona.
        Seat getSeat(string seatId) throws SeatNotFoundException; // Consulta una silla y reporta IDs inexistentes.

        // Bloqueo temporal de una silla para proceso de compra (90 seg)
        LockResult lockSeat(string seatId, string clientId) throws SeatNotFoundException, SeatNotAvailableException; // Retiene o renueva una silla por tiempo limitado.
        
        // Liberación manual de bloqueo
        bool releaseSeat(string seatId, string clientId) throws SeatNotFoundException; // Libera una retención del cliente.

        // Confirmación y compra de las sillas bloqueadas
        BookingResult confirmBooking(StringSeq seatIds, string clientId, string customerName, string customerEmail) throws InvalidBookingException, BookingException; // Valida y confirma la compra como operación transaccional.

        // Consultas por cliente
        SeatList getMyLockedSeats(string clientId); // Consulta bloqueos vigentes del cliente.
        BookingList getMyBookings(string clientId); // Consulta compras confirmadas del cliente.

        // Utilidad de reinicio para pruebas de laboratorio
        void resetSeats(); // Restaura inventario y reservas para pruebas de laboratorio.
    }; // Finaliza el contrato del servicio.

}; // Finaliza el módulo compartido del contrato.
