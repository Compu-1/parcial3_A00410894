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

        String[] duplicateSeats = {"A-1", "A-1"}; // Construye una solicitud que no debe duplicar el cobro.
        assertThrows(InvalidBookingException.class, () -> service.confirmBooking(duplicateSeats, "owner", "Ana Pérez", "ana@example.com", null)); // Rechaza IDs de asiento repetidos.

        BookingResult booking = service.confirmBooking(availableSeat, "owner", "Ana Pérez", "ana@example.com", null); // Confirma la silla retenida por su propietaria.
        assertNotNull(booking); // Confirma que se produjo un comprobante.
        assertTrue(booking.success); // Confirma que el resultado RPC indica éxito.
        assertEquals(SeatState.BOOKED, service.getSeat("A-1", null).state); // Verifica que la silla termine vendida.
        assertEquals(1, service.getMyBookings("owner", null).length); // Verifica que el historial registre una compra.
    } // Finaliza el caso de validación transaccional.
} // Finaliza la clase de pruebas.
