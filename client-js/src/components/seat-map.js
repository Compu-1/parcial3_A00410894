export class SeatMap { // Presenta el inventario como filas de botones accesibles.
    constructor(root, filter, onSelect, seatStates, seatZones) { // Recibe nodos y enums generados desde Slice.
        this.root = root; // Conserva el contenedor de filas del plano.
        this.filter = filter; // Conserva el selector de zonas.
        this.onSelect = onSelect; // Conserva la acción para bloquear una silla disponible.
        this.seatStates = seatStates; // Conserva los valores de estado oficiales del contrato.
        this.seatZones = seatZones; // Conserva los valores de zona oficiales del contrato.
        this.filter.addEventListener("change", () => this.render(this.seats || [], this.clientId || "")); // Vuelve a filtrar al cambiar la zona.
        this.root.addEventListener("click", event => { // Delega clics para evitar un listener por silla.
            const button = event.target.closest("button[data-seat-id]"); // Encuentra el botón de silla seleccionado.
            if (button && !button.disabled) this.onSelect(button.dataset.seatId); // Solicita bloquear solo asientos disponibles.
        }); // Finaliza la delegación de clics.
    } // Finaliza la construcción del mapa.

    render(seats, clientId) { // Dibuja el estado actual entregado por el backend.
        this.seats = seats; // Conserva los datos para cambios de filtro.
        this.clientId = clientId; // Conserva la sesión para distinguir bloqueos propios.
        const zoneFilter = this.filter.value; // Obtiene el filtro activo de la interfaz.
        const visibleSeats = seats.filter(seat => zoneFilter === "all" || this.zoneKey(seat.zone) === zoneFilter); // Incluye solo las sillas de la zona seleccionada.
        const groupedSeats = new Map(); // Agrupa las sillas por la fila del auditorio.
        visibleSeats.sort((first, second) => first.row.localeCompare(second.row) || first.number - second.number); // Mantiene el orden visual de filas y números.

        for (const seat of visibleSeats) { // Agrupa las sillas según el dato recibido por RPC.
            if (!groupedSeats.has(seat.row)) groupedSeats.set(seat.row, []); // Prepara una fila cuando aparece por primera vez.
            groupedSeats.get(seat.row).push(seat); // Añade la silla a su fila.
        } // Finaliza el agrupamiento de filas.

        this.root.replaceChildren(); // Retira el mapa anterior para renderizar la última instantánea.
        if (groupedSeats.size === 0) { // Atiende datos vacíos o un filtro sin coincidencias.
            const empty = document.createElement("p"); // Crea un mensaje sin insertar HTML arbitrario.
            empty.className = "empty-state"; // Aplica el estilo común para estados vacíos.
            empty.textContent = "No hay sillas para mostrar en esta zona."; // Explica por qué el mapa aparece vacío.
            this.root.append(empty); // Muestra el mensaje dentro del mapa.
            return; // Evita recorrer un conjunto vacío.
        } // Finaliza el estado vacío.

        for (const [rowName, rowSeats] of groupedSeats) { // Renderiza cada fila en el orden establecido.
            const row = document.createElement("div"); // Crea el contenedor visual de una fila.
            row.className = "seat-row"; // Asocia la fila con el diseño responsive.
            const label = document.createElement("span"); // Crea el rótulo accesible de fila.
            label.className = "row-label"; // Aplica estilo compacto a la letra.
            label.textContent = rowName; // Inserta la letra recibida desde el inventario.
            const seatsContainer = document.createElement("div"); // Agrupa botones de una fila.
            seatsContainer.className = "row-seats"; // Aplica la grilla de diez asientos.

            for (const seat of rowSeats) { // Construye un control por silla.
                const button = document.createElement("button"); // Usa un botón para accesibilidad y foco de teclado.
                button.type = "button"; // Evita enviar formularios al pulsar una silla.
                button.className = "seat-button"; // Aplica el estilo base del plano.
                button.dataset.seatId = seat.id; // Relaciona el control con el ID Slice de la silla.
                button.dataset.state = this.stateKey(seat.state, seat.lockedByClientId); // Expone el estado correcto para colores accesibles.
                button.dataset.zone = this.zoneKey(seat.zone); // Expone la zona del asiento para su distinción visual.
                button.textContent = String(seat.number); // Muestra el número legible de la silla.
                button.disabled = seat.state !== this.seatStates.AVAILABLE; // Impide que el cliente solicite sillas no disponibles.
                button.setAttribute("aria-label", this.accessibleSeatLabel(seat)); // Describe fila, zona, número y estado al lector de pantalla.
                seatsContainer.append(button); // Añade el control a su fila.
            } // Finaliza los botones de la fila.

            row.append(label, seatsContainer); // Combina identificación de fila y asientos.
            this.root.append(row); // Añade la fila al mapa.
        } // Finaliza el renderizado del inventario.
    } // Finaliza la actualización del mapa.

    stateKey(state, owner) { // Traduce los estados de Slice a clases CSS estables.
        if (state === this.seatStates.AVAILABLE) return "available"; // Distingue sillas que pueden bloquearse.
        if (state === this.seatStates.BOOKED) return "booked"; // Distingue compras definitivas.
        return owner === this.clientId ? "yours" : "locked"; // Separa bloqueos propios de los de otros clientes.
    } // Finaliza la traducción de estados.

    zoneKey(zone) { // Traduce la zona Slice para filtrar y aplicar color.
        if (zone === this.seatZones.ZONEVIP) return "vip"; // Asocia el enum VIP con su clave visual.
        if (zone === this.seatZones.ZONEPLATEA) return "platea"; // Asocia el enum Platea con su clave visual.
        return "general"; // Trata el tercer enum como zona General.
    } // Finaliza la traducción de zonas.

    accessibleSeatLabel(seat) { // Construye una etiqueta descriptiva sin depender del color.
        const zoneNames = { vip: "VIP", platea: "Platea", general: "General" }; // Traduce claves visuales a nombres en español.
        const stateNames = { available: "disponible", yours: "en tu carrito", locked: "retenida por otra persona", booked: "vendida" }; // Traduce estados visuales a descripciones.
        const zone = zoneNames[this.zoneKey(seat.zone)]; // Obtiene el nombre de zona del asiento.
        const state = stateNames[this.stateKey(seat.state, seat.lockedByClientId)]; // Obtiene el estado del asiento.
        return `Fila ${seat.row}, silla ${seat.number}, zona ${zone}, ${state}.`; // Devuelve el texto accesible del botón.
    } // Finaliza la etiqueta accesible.
} // Finaliza el componente del mapa.
