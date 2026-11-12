module BookingApp {

    enum SeatZone {
        ZONEVIP,
        ZONEPLATEA,
        ZONEGENERAL
    };

    enum SeatState {
        AVAILABLE,
        LOCKED,
        BOOKED
    };

    struct Seat {
        string id;
        SeatZone zone;
        string row;
        int number;
        double price;
        SeatState state;
        string lockedByClientId;
        long lockTimestamp;
        long lockDurationSeconds;
    };

    sequence<Seat> SeatList;
    sequence<string> StringSeq;

    struct EventInfo {
        string id;
        string name;
        string date;
        string venue;
        int totalSeats;
        int availableSeats;
        int lockedSeats;
        int bookedSeats;
        double vipPrice;
        double plateaPrice;
        double generalPrice;
    };

    struct BookingRecord {
        string bookingCode;
        string eventId;
        string clientId;
        string customerName;
        string customerEmail;
        StringSeq seatIds;
        double totalAmount;
        long timestamp;
    };

    sequence<BookingRecord> BookingList;

    struct LockResult {
        bool success;
        string seatId;
        string message;
        long expiresAt;
    };

    sequence<LockResult> LockResultList;

    struct BookingResult {
        bool success;
        string bookingCode;
        string message;
        StringSeq seatIds;
        double totalAmount;
        long timestamp;
    };

    exception BookingException {
        string reason;
    };

    exception SeatNotFoundException extends BookingException {};
    exception SeatNotAvailableException extends BookingException {};
    exception LockExpiredException extends BookingException {};
    exception InvalidBookingException extends BookingException {};

    interface BookingService {
        EventInfo getEventInfo();
        SeatList getAllSeats();
        SeatList getSeatsByZone(SeatZone zone);
        Seat getSeat(string seatId) throws SeatNotFoundException;

        // Bloqueo temporal de una silla para proceso de compra (90 seg)
        LockResult lockSeat(string seatId, string clientId) throws SeatNotFoundException, SeatNotAvailableException;
        
        // Liberación manual de bloqueo
        bool releaseSeat(string seatId, string clientId) throws SeatNotFoundException;

        // Confirmación y compra de las sillas bloqueadas
        BookingResult confirmBooking(StringSeq seatIds, string clientId, string customerName, string customerEmail) throws InvalidBookingException, BookingException;

        // Consultas por cliente
        SeatList getMyLockedSeats(string clientId);
        BookingList getMyBookings(string clientId);

        // Utilidad de reinicio para pruebas de laboratorio
        void resetSeats();
    };

};
