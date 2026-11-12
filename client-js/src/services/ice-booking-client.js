const Ice = window.Ice; // Obtiene el runtime browser oficial cargado antes del bundle.
const BookingApp = window.BookingApp; // Obtiene los proxies generados desde el contrato Slice compartido.

export class IceBookingClient { // Encapsula conexión y llamadas RPC del navegador.
    constructor(options = {}) { // Permite sustituir host y puerto durante despliegues.
        this.host = options.host || window.location.hostname || "localhost"; // Usa el host actual y reserva localhost para ejecución local.
        this.port = options.port || 10002; // Selecciona el endpoint WebSocket exigido por el proyecto.
        this.identity = options.identity || "BookingService"; // Conserva la identidad registrada en el adaptador Java.
        this.communicator = null; // Guarda el recurso Ice mientras exista una conexión.
        this.service = null; // Guarda el proxy tipado cuando la conexión se valida.
    } // Finaliza la inicialización de la conexión.

    async connect() { // Inicializa el runtime y obtiene el proxy remoto.
        if (this.service) return this.service; // Reutiliza una conexión ya establecida.
        this.communicator = Ice.initialize(); // Crea el communicator oficial de Ice en el navegador.
        try { // Convierte fallos de conexión en errores recuperables por la interfaz.
            const proxyText = `${this.identity}:ws -h ${this.host} -p ${this.port}`; // Construye el proxy con el transporte WebSocket.
            const untypedProxy = this.communicator.stringToProxy(proxyText); // Crea el proxy base para la identidad del servicio.
            this.service = await BookingApp.BookingServicePrx.checkedCast(untypedProxy); // Verifica remotamente que la interfaz Slice esté disponible.
            if (!this.service) throw new Error("El objeto remoto no implementa BookingService."); // Evita continuar con un proxy nulo.
            return this.service; // Expone el proxy tipado a los métodos RPC.
        } catch (error) { // Libera recursos si la conexión o el cast remoto fallan.
            await this.close(); // Destruye el communicator para permitir reintentos limpios.
            throw error; // Mantiene el error original para mostrar un diagnóstico útil.
        } // Finaliza el tratamiento de la conexión.
    } // Finaliza la inicialización RPC.

    async getEventInfo() { // Solicita metadata y contadores del evento.
        return (await this.connect()).getEventInfo(); // Ejecuta la operación del contrato Slice.
    } // Finaliza la consulta del evento.

    async getAllSeats() { // Solicita el mapa completo de sillas.
        return (await this.connect()).getAllSeats(); // Ejecuta la operación del contrato Slice.
    } // Finaliza la consulta del inventario.

    async getMyLockedSeats(clientId) { // Consulta bloqueos vigentes del identificador de sesión.
        return (await this.connect()).getMyLockedSeats(clientId); // Ejecuta la operación del contrato Slice.
    } // Finaliza la consulta de bloqueos propios.

    async getMyBookings(clientId) { // Consulta compras confirmadas de esta sesión.
        return (await this.connect()).getMyBookings(clientId); // Ejecuta la operación del contrato Slice.
    } // Finaliza la consulta del historial.

    async lockSeat(seatId, clientId) { // Solicita la retención temporal de una silla.
        return (await this.connect()).lockSeat(seatId, clientId); // Devuelve el resultado y vencimiento enviados por el backend.
    } // Finaliza la solicitud de bloqueo.

    async releaseSeat(seatId, clientId) { // Solicita la liberación voluntaria de una silla.
        return (await this.connect()).releaseSeat(seatId, clientId); // Devuelve si el servidor liberó el bloqueo.
    } // Finaliza la solicitud de liberación.

    async confirmBooking(seatIds, clientId, customerName, customerEmail) { // Confirma transaccionalmente la reserva del cliente.
        return (await this.connect()).confirmBooking(seatIds, clientId, customerName, customerEmail); // Devuelve el comprobante generado por el servidor.
    } // Finaliza la confirmación de compra.

    async close() { // Destruye de forma asíncrona los recursos del runtime.
        const communicator = this.communicator; // Conserva el communicator antes de limpiar el estado local.
        this.communicator = null; // Marca la conexión como cerrada para permitir un reintento.
        this.service = null; // Invalida el proxy tipado asociado al communicator anterior.
        if (communicator) await communicator.destroy(); // Cierra conexiones y libera recursos del runtime.
    } // Finaliza el cierre del cliente.
} // Finaliza la capa RPC del frontend.
