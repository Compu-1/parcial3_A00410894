import { iceLongToNumber } from "../utils/ice-number.js"; // Convierte la marca de tiempo Slice antes de presentarla como fecha.

export class BookingHistory { // Presenta comprobantes confirmados para la sesión actual.
    constructor(root) { // Recibe el elemento anfitrión de la lista de reservas.
        this.root = root; // Conserva el contenedor del historial.
    } // Finaliza la construcción del historial.

    render(bookings) { // Sustituye el historial con la respuesta actual del servidor.
        this.root.replaceChildren(); // Elimina datos visuales de una respuesta anterior.
        if (bookings.length === 0) { // Atiende a clientes que aún no han comprado.
            const empty = document.createElement("p"); // Construye el mensaje sin interpretar HTML remoto.
            empty.className = "empty-state"; // Aplica el estilo común de estado vacío.
            empty.textContent = "Tus reservas confirmadas aparecerán aquí."; // Explica qué contiene esta sección.
            this.root.append(empty); // Muestra la guía en el panel.
            return; // Evita crear tarjetas para una lista vacía.
        } // Finaliza el estado sin compras.

        for (const booking of bookings) { // Renderiza cada registro recibido mediante getMyBookings.
            const card = document.createElement("article"); // Crea una superficie independiente para el comprobante.
            card.className = "booking-card"; // Aplica el diseño de historial.
            const identity = document.createElement("div"); // Agrupa código y asientos adquiridos.
            const code = document.createElement("strong"); // Destaca el código de reserva.
            code.textContent = booking.bookingCode; // Inserta el código producido por el servidor.
            const seats = document.createElement("span"); // Describe las sillas asociadas con el código.
            seats.textContent = ` · Sillas: ${booking.seatIds.join(", ")}`; // Muestra el listado de asientos confirmados.
            const details = document.createElement("span"); // Agrupa fecha y monto pagado.
            details.textContent = `${new Date(iceLongToNumber(booking.timestamp)).toLocaleString("es-CO")} · ${this.formatCurrency(booking.totalAmount)} COP`; // Muestra el total y la marca temporal remotos.
            identity.append(code, seats); // Organiza el identificador y sus sillas.
            card.append(identity, details); // Combina identidad y detalles financieros.
            this.root.append(card); // Añade el comprobante a la lista.
        } // Finaliza la lista de comprobantes.
    } // Finaliza la representación del historial.

    formatCurrency(amount) { // Presenta el total con separadores locales.
        return new Intl.NumberFormat("es-CO", { style: "currency", currency: "COP", maximumFractionDigits: 0 }).format(amount); // Usa formato colombiano sin fracciones.
    } // Finaliza el formato monetario.
} // Finaliza el componente de historial.
