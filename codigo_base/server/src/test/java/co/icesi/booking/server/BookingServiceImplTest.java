// La carpeta server/src/test contiene pruebas automatizadas de concurrencia e integridad del servicio.
package co.icesi.booking.server; // Sitúa las pruebas junto al paquete del servicio probado.

import BookingApp.BookingException; // Permite verificar errores del contrato RPC de reservas.
import BookingApp.BookingResult; // Permite revisar el resultado de una compra correcta.
import BookingApp.InvalidBookingException; // Permite comprobar validaciones de compra.
import BookingApp.SeatNotAvailableException; // Permite comprobar conflictos de disponibilidad.
import BookingApp.SeatState; // Permite comprobar el estado final de una silla.
import org.junit.After; // Cierra los recursos creados por cada prueba.
import org.junit.Before; // Prepara un inventario limpio antes de cada prueba.
import org.junit.Test; // Marca los casos ejecutables por JUnit.

import java.util.ArrayList; // Almacena las respuestas de las tareas concurrentes.
import java.util.List; // Expone la colección de respuestas mediante su interfaz.
import java.util.concurrent.CountDownLatch; // Inicia las solicitudes concurrentes al mismo tiempo.
import java.util.concurrent.ExecutorService; // Ejecuta las solicitudes desde hilos independientes.
import java.util.concurrent.Executors; // Crea el grupo fijo de hilos de prueba.
import java.util.concurrent.Future; // Recoge los resultados de cada solicitud.
import java.util.concurrent.TimeUnit; // Limita la espera de las respuestas concurrentes.

import static org.junit.Assert.assertEquals; // Comprueba la cantidad de bloqueos concedidos.
import static org.junit.Assert.assertFalse; // Comprueba acciones que deben rechazarse.
import static org.junit.Assert.assertNotNull; // Comprueba que la compra tenga resultado.
import static org.junit.Assert.assertThrows; // Comprueba que las entradas inválidas fallen.
import static org.junit.Assert.assertTrue; // Comprueba el éxito de la compra válida.

public class BookingServiceImplTest { // Agrupa pruebas de integridad del inventario y compra.
    private BookingServiceImpl service; // Comparte un servicio aislado dentro de cada caso.

    @Before // Ejecuta la preparación antes de cada prueba.
    public void setUp() { // Crea el inventario inicial de 90 sillas.
        service = new BookingServiceImpl(); // Inicializa el servicio bajo prueba.
    } // Finaliza la preparación del caso.

    @After // Ejecuta la limpieza aunque una aserción falle.
    public void tearDown() { // Libera el hilo de expiración del servicio.
        service.close(); // Detiene el planificador para evitar dejar recursos activos.
    } // Finaliza la limpieza del caso.

    @Test // Verifica que una silla no pueda concederse a varios clientes simultáneamente.
    public void onlyOneConcurrentClientCanLockTheSameSeat() throws Exception { // Permite propagar errores de espera de las tareas.
        int threadCount = 10; // Define la cantidad de clientes que compiten por una silla.
        ExecutorService executor = Executors.newFixedThreadPool(threadCount); // Prepara un hilo para cada cliente.
        CountDownLatch startSignal = new CountDownLatch(1); // Mantiene las solicitudes retenidas hasta el disparo simultáneo.
        List<Future<Boolean>> results = new ArrayList<>(); // Conserva si cada cliente obtuvo el bloqueo.

        try { // Garantiza que el grupo de hilos cierre aun si una tarea falla.
            for (int index = 0; index < threadCount; index++) { // Programa una solicitud independiente por cliente.
                String clientId = "client-" + index; // Asigna una identidad distinta a cada solicitante.
                results.add(executor.submit(() -> { // Ejecuta un intento concurrente de reserva.
                    startSignal.await(); // Alinea todos los intentos antes de acceder al inventario.
                    try { // Interpreta el rechazo por contención como resultado esperado.
                        service.lockSeat("A-1", clientId, null); // Intenta bloquear la misma silla compartida.
                        return true; // Registra el único caso que obtiene el bloqueo.
                    } catch (SeatNotAvailableException expected) { // Recibe los intentos que perdieron la carrera.
                        return false; // Marca que este cliente no obtuvo la silla.
                    } // Finaliza el manejo del conflicto de disponibilidad.
                })); // Guarda el resultado futuro para contabilizarlo.
            } // Finaliza el envío de solicitudes concurrentes.

            startSignal.countDown(); // Libera todos los clientes en el mismo instante.
            int successfulLocks = 0; // Cuenta las solicitudes aceptadas por el servicio.
            for (Future<Boolean> result : results) { // Reúne las respuestas de todos los hilos.
                if (result.get(5, TimeUnit.SECONDS)) { // Falla la prueba si una solicitud queda bloqueada.
                    successfulLocks++; // Suma cada bloqueo efectivamente concedido.
                } // Finaliza la contabilización del resultado actual.
            } // Finaliza la recolección de resultados.

            assertEquals("Una silla solo debe tener un ganador.", 1, successfulLocks); // Demuestra la exclusión mutua del inventario.
        } finally { // Ejecuta el cierre en los casos de éxito y de error.
            executor.shutdownNow(); // Detiene los trabajadores después de reunir los resultados.
        } // Finaliza la limpieza del ejecutor.
    } // Finaliza el caso de concurrencia.

    @Test // Verifica que la compra dependa de bloqueos válidos y de IDs únicos.
    public void bookingRequiresOwnedUniqueLocks() throws Exception { // Permite comprobar las excepciones declaradas por Slice.
        String[] availableSeat = {"A-1"}; // Representa una silla que aún no ha sido retenida.
        assertThrows(SeatNotAvailableException.class, () -> service.confirmBooking(availableSeat, "buyer", "Ana Pérez", "ana@example.com", null)); // Rechaza vender una silla disponible.

        service.lockSeat("A-1", "owner", null); // Retiene la silla para una identidad conocida.
        assertThrows(BookingException.class, () -> service.confirmBooking(availableSeat, "other", "Ana Pérez", "ana@example.com", null)); // Rechaza la compra de un bloqueo ajeno.
        assertThrows(InvalidBookingException.class, () -> service.confirmBooking(availableSeat, "owner", "Ana Pérez", "correo-invalido", null)); // Rechaza correos que no tienen formato de dirección válido.

        String[] duplicateSeats = {"A-1", "A-1"}; // Construye una solicitud que no debe duplicar el cobro.
        assertThrows(InvalidBookingException.class, () -> service.confirmBooking(duplicateSeats, "owner", "Ana Pérez", "ana@example.com", null)); // Rechaza IDs de asiento repetidos.

        BookingResult booking = service.confirmBooking(availableSeat, "owner", "Ana Pérez", "ana@example.com", null); // Confirma la silla retenida por su propietaria.
        assertNotNull(booking); // Confirma que se produjo un comprobante.
        assertTrue(booking.success); // Confirma que el resultado RPC indica éxito.
        assertEquals(SeatState.BOOKED, service.getSeat("A-1", null).state); // Verifica que la silla termine vendida.
        assertEquals(1, service.getMyBookings("owner", null).length); // Verifica que el historial registre una compra.
    } // Finaliza el caso de validación transaccional.

    @Test // Verifica que solo el propietario pueda renovar y liberar una silla bloqueada.
    public void onlyLockOwnerCanRenewOrReleaseSeat() throws Exception { // Permite invocar operaciones RPC que declaran excepciones.
        long firstExpiration = service.lockSeat("A-1", "owner", null).expiresAt; // Retiene la silla para el cliente propietario.
        assertThrows(SeatNotAvailableException.class, () -> service.lockSeat("A-1", "other", null)); // Impide que otro cliente renueve el bloqueo.
        assertFalse(service.releaseSeat("A-1", "other", null)); // Impide que otro cliente libere el bloqueo.

        long renewedExpiration = service.lockSeat("A-1", "owner", null).expiresAt; // Permite al propietario renovar su retención.
        assertTrue(renewedExpiration >= firstExpiration); // Comprueba que la renovación conserva o prolonga el vencimiento.
        assertTrue(service.releaseSeat("A-1", "owner", null)); // Permite que el propietario libere voluntariamente la silla.
        assertEquals(SeatState.AVAILABLE, service.getSeat("A-1", null).state); // Comprueba que la silla vuelve a estar disponible.
        assertEquals(90, service.getEventInfo(null).availableSeats); // Comprueba que el aforo refleje la liberación.
    } // Finaliza el caso de propiedad de bloqueo.

    @Test // Verifica que un bloqueo vencido deje de impedir nuevas reservas.
    public void expiredLockBecomesAvailableAgain() throws Exception { // Permite comprobar el resultado de las operaciones RPC.
        service.close(); // Detiene el planificador del servicio con el plazo normal.
        service = new BookingServiceImpl(1); // Usa un plazo reducido para evitar esperar 90 segundos en la prueba.
        service.lockSeat("A-1", "owner", null); // Retiene la silla durante el plazo corto.

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(4); // Limita la espera para que un fallo no bloquee la suite.
        while (service.getSeat("A-1", null).state == SeatState.LOCKED && System.nanoTime() < deadline) { // Espera hasta que la expiración sea visible.
            Thread.sleep(50); // Deja tiempo al reloj y al planificador de expiraciones.
        } // Finaliza la espera acotada.

        assertEquals(SeatState.AVAILABLE, service.getSeat("A-1", null).state); // Comprueba que la retención vencida se libere.
        assertThrows(BookingException.class, () -> service.confirmBooking(new String[]{"A-1"}, "owner", "Ana Pérez", "ana@example.com", null)); // Rechaza comprar con el bloqueo ya vencido.
        service.lockSeat("A-1", "next-owner", null); // Comprueba que otro cliente pueda adquirir la silla vencida.
        assertEquals("next-owner", service.getSeat("A-1", null).lockedByClientId); // Comprueba que el nuevo propietario figure en el inventario.
    } // Finaliza el caso de expiración.

    @Test // Verifica que una compra con una silla inválida no confirme parcialmente las demás.
    public void invalidMultiSeatBookingDoesNotPartiallyBookSeats() throws Exception { // Permite comprobar excepciones del servicio.
        service.lockSeat("A-1", "buyer", null); // Retiene una silla para la compra que se intentará.
        service.lockSeat("A-2", "other", null); // Retiene otra silla para impedir que el comprador la incluya.
        String[] requestedSeats = {"A-1", "A-2"}; // Combina una silla propia y otra de un cliente diferente.

        assertThrows(BookingException.class, () -> service.confirmBooking(requestedSeats, "buyer", "Ana Pérez", "ana@example.com", null)); // Rechaza la compra completa ante una silla ajena.
        assertEquals(SeatState.LOCKED, service.getSeat("A-1", null).state); // Comprueba que la silla válida no se haya vendido parcialmente.
        assertEquals("buyer", service.getSeat("A-1", null).lockedByClientId); // Comprueba que la retención original sigue perteneciendo al comprador.
        assertEquals(SeatState.LOCKED, service.getSeat("A-2", null).state); // Comprueba que la silla ajena conserva su bloqueo.
        assertEquals(0, service.getMyBookings("buyer", null).length); // Comprueba que un fallo no genere un comprobante parcial.
    } // Finaliza el caso de atomicidad.

    @Test // Verifica que reiniciar el inventario elimine ventas, bloqueos e historial.
    public void resetRestoresAllSeatsAndClearsBookingHistory() throws Exception { // Permite preparar y confirmar una compra antes de reiniciar.
        service.lockSeat("A-1", "buyer", null); // Retiene una silla para completar una compra válida.
        service.confirmBooking(new String[]{"A-1"}, "buyer", "Ana Pérez", "ana@example.com", null); // Genera una reserva que debe eliminar el reinicio.
        service.lockSeat("B-1", "another-buyer", null); // Añade otro bloqueo para verificar que también se limpie.

        service.resetSeats(null); // Ejecuta el reinicio remoto del inventario de laboratorio.

        assertEquals(90, service.getEventInfo(null).availableSeats); // Comprueba que las 90 sillas vuelvan a estar disponibles.
        assertEquals(0, service.getEventInfo(null).bookedSeats); // Comprueba que no queden ventas confirmadas.
        assertEquals(SeatState.AVAILABLE, service.getSeat("B-1", null).state); // Comprueba que el bloqueo restante también se borre.
        assertEquals(0, service.getMyBookings("buyer", null).length); // Comprueba que el historial del comprador quede vacío.
    } // Finaliza el caso de reinicio.
} // Finaliza la clase de pruebas.
