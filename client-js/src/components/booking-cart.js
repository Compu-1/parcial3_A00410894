import { iceLongToNumber } from "../utils/ice-number.js"; // Convierte los campos long de Ice antes de calcular tiempos.

export class BookingCart { // Renderiza los bloqueos propios y su cuenta regresiva.
    constructor(root, count, total, checkoutButton, onRelease, onCheckout) { // Recibe elementos del carrito y acciones del usuario.
        this.root = root; // Conserva la lista de sillas reservadas.
        this.count = count; // Conserva el contador de artículos.
        this.total = total; // Conserva el valor estimado del carrito.
        this.checkoutButton = checkoutButton; // Conserva la acción de confirmar la compra.
        this.onRelease = onRelease; // Conserva la liberación voluntaria de una silla.
        this.onCheckout = onCheckout; // Conserva la acción para abrir el checkout.
        this.timer = null; // Almacena el intervalo para evitar cronómetros duplicados.
        this.checkoutButton.addEventListener("click", () => this.onCheckout(this.seats || [])); // Pasa los bloqueos vigentes al formulario de compra.
    } // Finaliza la construcción del carrito.

    render(seats) { // Actualiza los elementos y el importe del carrito.
        this.seats = seats; // Conserva las sillas para la acción de checkout.
        if (this.timer) window.clearInterval(this.timer); // Detiene el cronómetro anterior antes de reemplazar filas.
        this.count.textContent = String(seats.length); // Actualiza la cantidad de sillas bloqueadas.
        this.checkoutButton.disabled = seats.length === 0; // Evita confirmar una compra sin asientos.
        this.root.replaceChildren(); // Reemplaza la lista por los bloqueos vigentes del servidor.
        const totalAmount = seats.reduce((sum, seat) => sum + seat.price, 0); // Calcula el total preliminar usando precios del backend.
        this.total.textContent = this.formatCurrency(totalAmount); // Presenta el importe calculado en pesos colombianos.

        if (seats.length === 0) { // Explica el panel cuando no existen bloqueos propios.
            const empty = document.createElement("p"); // Crea el mensaje de carrito vacío con DOM seguro.
            empty.className = "empty-state"; // Aplica el estilo neutral de contenido vacío.
            empty.textContent = "Selecciona una silla disponible para iniciar tu reserva."; // Indica el paso siguiente del flujo.
            this.root.append(empty); // Añade la explicación al panel.
            return; // Evita crear un intervalo sin elementos que actualizar.
        } // Finaliza la condición de carrito vacío.

        for (const seat of seats) { // Construye el detalle para cada bloqueo vigente.
            const item = document.createElement("article"); // Agrupa los datos y acciones de la silla.
            item.className = "cart-item"; // Aplica el diseño compacto del carrito.
            const copy = document.createElement("div"); // Agrupa descripción, precio y tiempo restante.
            copy.className = "cart-item-copy"; // Aplica el estilo de texto apilado.
            const title = document.createElement("strong"); // Destaca el ID de la silla.
            title.textContent = `${seat.id} · Fila ${seat.row}`; // Identifica la silla usando información del servidor.
            const price = document.createElement("span"); // Presenta el precio oficial de la silla.
            price.textContent = `${seat.zone} · ${this.formatCurrency(seat.price)}`; // Muestra zona y valor del asiento.
            const countdown = document.createElement("span"); // Reserva el espacio para el tiempo de expiración.
            countdown.className = "remaining-time"; // Destaca el carácter temporal del bloqueo.
            countdown.dataset.expiresAt = String(iceLongToNumber(seat.lockTimestamp) + iceLongToNumber(seat.lockDurationSeconds) * 1000); // Deriva la expiración de la marca horaria del servidor.
            countdown.textContent = this.remainingTime(Number(countdown.dataset.expiresAt)); // Muestra el tiempo sin esperar al próximo tick.
            const releaseButton = document.createElement("button"); // Crea la acción de liberar esta silla.
            releaseButton.type = "button"; // Evita que el botón envíe un formulario circundante.
            releaseButton.className = "text-button"; // Aplica la jerarquía visual secundaria de liberación.
            releaseButton.textContent = "Quitar"; // Describe la acción en un control breve.
            releaseButton.setAttribute("aria-label", `Liberar silla ${seat.id}`); // Explica qué silla se liberará al lector de pantalla.
            releaseButton.addEventListener("click", () => this.onRelease(seat.id)); // Solicita al controlador ejecutar releaseSeat.
            copy.append(title, price, countdown); // Agrupa la información de la silla.
            item.append(copy, releaseButton); // Combina información y acción del carrito.
            this.root.append(item); // Añade el artículo a la lista visible.
        } // Finaliza los artículos del carrito.

        this.timer = window.setInterval(() => { // Sincroniza todos los relojes mientras el carrito tenga sillas.
            this.root.querySelectorAll("[data-expires-at]").forEach(node => { // Actualiza cada cuenta regresiva visible.
                node.textContent = this.remainingTime(Number(node.dataset.expiresAt)); // Calcula el tiempo transcurrido desde el vencimiento del servidor.
            }); // Finaliza la actualización de los temporizadores.
        }, 1000); // Refresca la cuenta regresiva una vez por segundo.
    } // Finaliza la representación del carrito.

    remainingTime(expiresAt) { // Convierte el vencimiento de servidor en minutos y segundos visibles.
        const seconds = Math.max(0, Math.ceil((expiresAt - Date.now()) / 1000)); // Evita mostrar valores negativos tras la expiración.
        const minutes = Math.floor(seconds / 60); // Obtiene el componente de minutos del cronómetro.
        const remainingSeconds = String(seconds % 60).padStart(2, "0"); // Completa con cero los segundos menores a diez.
        return `${minutes}:${remainingSeconds} restantes`; // Expresa claramente el tiempo que queda.
    } // Finaliza el formato de la cuenta regresiva.

    formatCurrency(amount) { // Formatea precios en la moneda de la universidad.
        return new Intl.NumberFormat("es-CO", { style: "currency", currency: "COP", maximumFractionDigits: 0 }).format(amount); // Evita decimales en los precios definidos en pesos.
    } // Finaliza el formato monetario.
} // Finaliza el componente del carrito.
