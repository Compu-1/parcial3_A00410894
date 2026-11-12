import { BookingCart } from "./components/booking-cart.js"; // Presenta bloqueos, importes y temporizadores.
import { BookingHistory } from "./components/booking-history.js"; // Presenta compras confirmadas del cliente.
import { CheckoutDialog } from "./components/checkout-dialog.js"; // Solicita y valida datos de compra.
import { SeatMap } from "./components/seat-map.js"; // Representa las sillas y permite bloquear disponibles.
import { IceBookingClient } from "./services/ice-booking-client.js"; // Encapsula el transporte RPC oficial Ice/WebSocket.

const BookingApp = window.BookingApp; // Lee enums generados antes de cargar el bundle de la aplicación.
const REFRESH_INTERVAL_MS = 4000; // Mantiene el estado visual actualizado entre clientes concurrentes.

export function mountBookingApp(root) { // Construye la SPA dentro del elemento de montaje.
    const connectionStatus = root.querySelector("#connection-status"); // Ubica el indicador del canal WebSocket.
    const feedback = root.querySelector("#feedback"); // Ubica el área de notificaciones de acciones.
    const clientId = getSessionClientId(); // Recupera o crea la identidad persistente de esta pestaña.
    const rpc = new IceBookingClient(); // Construye el cliente ZeroC Ice en el navegador.
    const seatMap = new SeatMap(root.querySelector("#seat-grid"), root.querySelector("#zone-filter"), seatId => handleSeatSelection(seatId), BookingApp.SeatState, BookingApp.SeatZone); // Conecta el mapa visual con las operaciones de asiento.
    const history = new BookingHistory(root.querySelector("#booking-history")); // Construye el historial asociado a esta sesión.
    let checkout; // Reserva el controlador modal para enlazarlo con el carrito.
    const cart = new BookingCart(root.querySelector("#cart-items"), root.querySelector("#cart-count"), root.querySelector("#cart-total"), root.querySelector("#checkout-button"), seatId => handleSeatRelease(seatId), seats => checkout.open(seats)); // Conecta liberar y checkout con las acciones RPC.
    checkout = new CheckoutDialog(root.querySelector("#checkout-dialog"), values => handleCheckout(values)); // Conecta el modal con la confirmación de compra.
    root.querySelector("#session-label").textContent = `Sesión ${clientId}`; // Facilita distinguir clientes al probar pestañas concurrentes.
    let refreshing = false; // Evita que las consultas periódicas se solapen.
    let hasLoadedData = false; // Permite mantener el último mapa al perder conexión.

    async function refresh() { // Consulta el estado remoto y actualiza cada componente.
        if (refreshing) return; // Descarta un tick si otra consulta continúa activa.
        refreshing = true; // Marca que hay una lectura RPC en curso.
        try { // Conserva control explícito sobre desconexiones y errores del servicio.
            await rpc.connect(); // Abre el canal WebSocket y valida el proxy del servicio.
            const [eventInfo, seats, lockedSeats, bookings] = await Promise.all([rpc.getEventInfo(), rpc.getAllSeats(), rpc.getMyLockedSeats(clientId), rpc.getMyBookings(clientId)]); // Solicita aforo, inventario, carrito e historial.
            renderEvent(eventInfo); // Actualiza nombre, fecha, aforo y tarifas remotas.
            seatMap.render(seats, clientId); // Representa las sillas de la última instantánea.
            cart.render(lockedSeats); // Sincroniza carrito y cuenta regresiva del servidor.
            history.render(bookings); // Refresca los comprobantes propios.
            connectionStatus.textContent = "Conectado · actualización en tiempo real"; // Indica que el último ciclo RPC fue exitoso.
            connectionStatus.dataset.state = "online"; // Cambia el indicador visual a conectado.
            hasLoadedData = true; // Conserva la señal para diferenciar carga inicial de datos desactualizados.
        } catch (error) { // Evita que un error RPC detenga la SPA.
            connectionStatus.textContent = hasLoadedData ? "Sin conexión · mostrando último estado" : "Sin conexión con el servidor"; // Explica si la pantalla conserva datos anteriores.
            connectionStatus.dataset.state = "offline"; // Señala visualmente la desconexión.
            if (!hasLoadedData) showFeedback(errorMessage(error), "error"); // Informa el primer error de conexión al usuario.
        } finally { // Restablece el bloqueo incluso si la operación remota falla.
            refreshing = false; // Permite el siguiente ciclo periódico o acción RPC.
        } // Finaliza el refresco del estado.
    } // Finaliza la consulta del inventario.

    function renderEvent(eventInfo) { // Actualiza el resumen del evento usando la respuesta RPC.
        root.querySelector("#event-name").textContent = eventInfo.name; // Presenta el nombre remitido por el servidor.
        root.querySelector("#event-date").textContent = `${eventInfo.date} · ${eventInfo.venue}`; // Presenta fecha y lugar remotos.
        root.querySelector("#available-count").textContent = String(eventInfo.availableSeats); // Muestra sillas disponibles.
        root.querySelector("#locked-count").textContent = String(eventInfo.lockedSeats); // Muestra bloqueos vigentes.
        root.querySelector("#booked-count").textContent = String(eventInfo.bookedSeats); // Muestra ventas definitivas.
        root.querySelector("#vip-price").textContent = formatCurrency(eventInfo.vipPrice); // Muestra precio VIP del backend.
        root.querySelector("#platea-price").textContent = formatCurrency(eventInfo.plateaPrice); // Muestra precio Platea del backend.
        root.querySelector("#general-price").textContent = formatCurrency(eventInfo.generalPrice); // Muestra precio General del backend.
    } // Finaliza la presentación del evento.

    async function handleSeatSelection(seatId) { // Solicita un bloqueo exclusivo para la silla seleccionada.
        try { // Trata conflictos de disponibilidad como errores recuperables de interacción.
            const result = await rpc.lockSeat(seatId, clientId); // Pide al servicio reservar por 90 segundos.
            showFeedback(result.message || `Silla ${seatId} agregada a tu carrito.`, "success"); // Confirma la respuesta de bloqueo al usuario.
            await refresh(); // Consulta el estado oficial después de la operación.
        } catch (error) { // Informa bloqueos de otro cliente o desconexiones.
            showFeedback(errorMessage(error), "error"); // Presenta el mensaje remoto sin borrar el estado local.
            await refresh(); // Sincroniza el mapa tras un conflicto de carrera.
        } // Finaliza la selección de silla.
    } // Finaliza la solicitud de bloqueo.

    async function handleSeatRelease(seatId) { // Libera una silla propia desde el carrito.
        try { // Valida el resultado booleano de releaseSeat.
            const released = await rpc.releaseSeat(seatId, clientId); // Solicita la liberación voluntaria al backend.
            if (!released) throw new Error(`La silla ${seatId} ya no está bloqueada por esta sesión.`); // Explica una retención vencida o modificada.
            showFeedback(`Silla ${seatId} liberada.`, "success"); // Confirma la liberación remota.
            await refresh(); // Actualiza mapa, carrito y aforo.
        } catch (error) { // Mantiene la aplicación activa ante errores de red o expiración.
            showFeedback(errorMessage(error), "error"); // Informa al usuario del motivo de la operación fallida.
            await refresh(); // Recupera el estado verdadero del servidor.
        } // Finaliza la liberación manual.
    } // Finaliza la operación de releaseSeat.

    async function handleCheckout(values) { // Valida bloqueos vigentes antes de registrar la compra.
        const lockedSeats = await rpc.getMyLockedSeats(clientId); // Recupera las sillas que el servidor aún retiene para la sesión.
        if (lockedSeats.length === 0) throw new Error("No tienes sillas con bloqueo vigente para confirmar."); // Evita confirmar un carrito expirado.
        const result = await rpc.confirmBooking(lockedSeats.map(seat => seat.id), clientId, values.name, values.email); // Envía IDs únicos y datos de contacto al servicio.
        showFeedback(`Reserva ${result.bookingCode} confirmada · ${formatCurrency(result.totalAmount)}.`, "success"); // Muestra el comprobante devuelto por RPC.
        await refresh(); // Actualiza disponibilidad, carrito e historial de compras.
    } // Finaliza el checkout.

    function showFeedback(message, kind) { // Presenta una notificación accesible de operación.
        feedback.textContent = message; // Inserta texto plano, no contenido HTML remoto.
        feedback.dataset.kind = kind; // Aplica estilo de éxito o error según la respuesta.
    } // Finaliza la presentación del mensaje.

    refresh(); // Carga inmediatamente evento, inventario y reservas de sesión.
    const refreshTimer = window.setInterval(() => refresh(), REFRESH_INTERVAL_MS); // Sondea el servicio para reflejar actividad de otros clientes.
    window.addEventListener("beforeunload", () => { // Libera conexiones cuando se cierra o recarga la pestaña.
        window.clearInterval(refreshTimer); // Detiene el sondeo de una aplicación que ya no existe.
        rpc.close(); // Destruye el communicator Ice del navegador.
    }); // Finaliza la limpieza de la pestaña.
} // Finaliza el montaje de la SPA.

function getSessionClientId() { // Genera una identidad estable durante la vida de esta pestaña.
    const storageKey = "icesi-live-arena-client-id"; // Evita colisiones con otras claves del navegador.
    let clientId = window.sessionStorage.getItem(storageKey); // Recupera la identidad si la pestaña ya inició.
    if (!clientId) { // Crea una sesión nueva cuando no hay identidad guardada.
        const token = window.crypto && window.crypto.randomUUID ? window.crypto.randomUUID() : `${Date.now()}-${Math.random().toString(16).slice(2)}`; // Usa un UUID criptográfico con alternativa de compatibilidad.
        clientId = `WEB-${token}`; // Marca el origen web y conserva el token generado.
        window.sessionStorage.setItem(storageKey, clientId); // Mantiene la identidad entre recargas de esta pestaña.
    } // Finaliza la inicialización de sesión.
    return clientId; // Devuelve el identificador usado por bloqueos e historial.
} // Finaliza la generación de sesión.

function formatCurrency(amount) { // Formatea tarifas y totales con localización colombiana.
    return new Intl.NumberFormat("es-CO", { style: "currency", currency: "COP", maximumFractionDigits: 0 }).format(amount); // Evita centavos en valores expresados como pesos.
} // Finaliza el formato de moneda.

function errorMessage(error) { // Extrae un diagnóstico legible de excepciones locales o Slice.
    return error.reason || error.message || String(error); // Prefiere el motivo remoto y conserva una alternativa explícita.
} // Finaliza la extracción del error.
