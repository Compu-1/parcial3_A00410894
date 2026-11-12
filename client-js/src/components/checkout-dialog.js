export class CheckoutDialog { // Gestiona el formulario modal de confirmación de compra.
    constructor(dialog, onSubmit) { // Recibe el diálogo y la operación RPC que lo confirma.
        this.dialog = dialog; // Conserva el diálogo nativo y accesible.
        this.onSubmit = onSubmit; // Conserva la acción de confirmación del controlador.
        this.form = dialog.querySelector("#checkout-form"); // Obtiene el formulario de contacto.
        this.summary = dialog.querySelector("#checkout-summary"); // Obtiene el resumen visible de asientos.
        this.error = dialog.querySelector("#checkout-error"); // Obtiene el área de errores RPC.
        this.submitButton = dialog.querySelector("#confirm-button"); // Obtiene el botón que inicia la compra.
        dialog.querySelector("#close-checkout").addEventListener("click", () => dialog.close()); // Permite cancelar sin liberar sillas.
        dialog.addEventListener("click", event => { // Admite cerrar el diálogo al pulsar solo el fondo modal.
            if (event.target === dialog) dialog.close(); // Evita cerrar accidentalmente al pulsar campos del formulario.
        }); // Finaliza la gestión del fondo modal.
        this.form.addEventListener("submit", event => this.handleSubmit(event)); // Centraliza validación HTML y llamada RPC.
    } // Finaliza la configuración del formulario.

    open(seats) { // Muestra el número de sillas retenidas que se enviarán a confirmación.
        this.error.textContent = ""; // Limpia errores de un intento anterior.
        this.summary.textContent = `${seats.length} silla(s) en tu carrito. La compra validará nuevamente su disponibilidad.`; // Advierte que el backend valida el estado final.
        this.dialog.showModal(); // Abre el diálogo como modal accesible.
        this.dialog.querySelector("#customer-name").focus(); // Sitúa el foco en el primer dato requerido.
    } // Finaliza la apertura del diálogo.

    async handleSubmit(event) { // Envía datos de contacto y muestra errores sin perder el contexto.
        event.preventDefault(); // Evita una recarga completa de la SPA.
        this.error.textContent = ""; // Limpia validaciones anteriores.
        this.submitButton.disabled = true; // Evita enviar compras duplicadas desde el navegador.
        this.submitButton.textContent = "Confirmando..."; // Comunica que el servidor está procesando el RPC.
        const formData = new FormData(this.form); // Lee los valores ya validados por el formulario HTML.

        try { // Mantiene abierto el modal si el servidor rechaza la compra.
            await this.onSubmit({ name: String(formData.get("customerName")).trim(), email: String(formData.get("customerEmail")).trim() }); // Envía datos limpios al controlador.
            this.form.reset(); // Borra datos personales después de completar la reserva.
            this.dialog.close(); // Cierra el formulario solo ante una respuesta exitosa.
        } catch (error) { // Comunica errores remotos sin degradar la página.
            this.error.textContent = error.reason || error.message || "No fue posible confirmar la compra."; // Muestra el motivo entregado por RPC.
        } finally { // Restaura la acción aunque la solicitud falle.
            this.submitButton.disabled = false; // Permite reintentar después de corregir el problema.
            this.submitButton.textContent = "Confirmar reserva"; // Restablece el rótulo normal del botón.
        } // Finaliza el envío del formulario.
    } // Finaliza la gestión de envío.
} // Finaliza el componente de checkout.
