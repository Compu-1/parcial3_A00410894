package co.icesi.booking.server; // Ubica el punto de entrada en el paquete del servidor.

import com.zeroc.Ice.Communicator; // Administra el ciclo de vida del runtime Ice.
import com.zeroc.Ice.InitializationData; // Agrupa la configuración inicial del runtime.
import com.zeroc.Ice.ObjectAdapter; // Publica objetos remotos en los endpoints configurados.
import com.zeroc.Ice.Util; // Provee las funciones estándar para inicializar Ice.

import java.net.InetAddress; // Lee direcciones IP asignadas al equipo.
import java.net.NetworkInterface; // Enumera las interfaces de red activas.
import java.util.Enumeration; // Recorre las interfaces y sus direcciones.

public class BookingServerApp { // Configura y ejecuta el servicio remoto de reservas.

    public static final int DEFAULT_PORT = 10000; // Define el puerto TCP predeterminado del backend.
    public static final String SERVICE_IDENTITY = "BookingService"; // Define la identidad usada por los clientes RPC.

    public static void main(String[] args) { // Inicia los adaptadores TCP/WebSocket y espera el apagado.
        int port = DEFAULT_PORT; // Empieza con el puerto documentado si no se recibe otro.
        for (int i = 0; i < args.length; i++) { // Recorre parámetros para aceptar una configuración de puerto.
            if (args[i].equals("--port") && i + 1 < args.length) { // Comprueba que exista el valor después de --port.
                try { // Convierte el texto del argumento en un número de puerto.
                    port = Integer.parseInt(args[i + 1]); // Sustituye el puerto predeterminado por el indicado.
                } catch (NumberFormatException ignored) { // Conserva el valor predeterminado ante texto no numérico.
                } // Finaliza la conversión opcional del puerto.
            } // Finaliza la validación del argumento --port.
        } // Finaliza el recorrido de argumentos.

        System.out.println("=========================================================================="); // Dibuja el encabezado de inicio del servidor.
        System.out.println("  SERVIDOR ZEROC ICE - SISTEMA DE RESERVA DE BOLETAS (COMPU 1)           "); // Identifica el proceso en la consola.
        System.out.println("=========================================================================="); // Cierra visualmente el encabezado.

        InitializationData initData = new InitializationData(); // Prepara las propiedades de inicialización de Ice.
        initData.properties = Util.createProperties(); // Crea la colección configurable de propiedades.
        // Parámetros de rendimiento y soporte para clientes WebSocket
        initData.properties.setProperty("Ice.MessageSizeMax", "4096"); // Limita el tamaño máximo admitido por mensaje Ice.
        initData.properties.setProperty("Ice.ThreadPool.Server.Size", "10"); // Define el tamaño inicial del grupo de hilos RPC.
        initData.properties.setProperty("Ice.ThreadPool.Server.SizeMax", "100"); // Define el máximo de hilos RPC que puede crear Ice.

        BookingServiceImpl servant = null; // Conserva una referencia para detener el planificador al apagar el servidor.
        try (Communicator communicator = Util.initialize(args, initData)) { // Inicializa Ice y garantiza su cierre al salir.
            int wsPort = port + 2; // Puerto 10002 para WebSocket de navegadores
            // Endpoint dual: TCP para clientes nativos (puerto 10000) y WebSocket para clientes Web (puerto 10002)
            String endpoint = String.format("default -p %d:ws -h 0.0.0.0 -p %d", port, wsPort); // Abre TCP y WebSocket en sus puertos respectivos.
            ObjectAdapter adapter = communicator.createObjectAdapterWithEndpoints("BookingServerAdapter", endpoint); // Crea el adaptador para publicar objetos Ice.

            servant = new BookingServiceImpl(); // Construye el servicio que administra inventario y vencimientos.
            adapter.add(servant, Util.stringToIdentity(SERVICE_IDENTITY)); // Vincula la implementación con la identidad conocida por los clientes.
            adapter.activate(); // Empieza a aceptar solicitudes remotas.

            System.out.printf("[OK] Servidor ZeroC Ice activo en endpoints: %s%n", endpoint); // Informa las direcciones abiertas.
            System.out.printf("[OK] Canal TCP (Clientes Java/Escritorio)   : localhost:%d%n", port); // Muestra el puerto del cliente nativo.
            System.out.printf("[OK] Canal WebSocket (Navegadores Web / JS) : ws://localhost:%d%n", wsPort); // Muestra el endpoint del navegador.
            System.out.printf("[OK] Proxy Identity                         : %s%n", SERVICE_IDENTITY); // Muestra la identidad RPC registrada.
            printNetworkInterfaces(port, wsPort); // Informa direcciones adicionales para equipos remotos.
            System.out.println("--------------------------------------------------------------------------"); // Separa configuración y estado del servidor.
            System.out.println("[INFO] Catálogo inicializado: Concierto Icesi Live Arena (90 asientos)."); // Resume el inventario inicial.
            System.out.println("[OK] Operaciones de inventario protegidas contra condiciones de carrera."); // Confirma que el servidor inicia con exclusión mutua habilitada.
            System.out.println("[INFO] Presione Ctrl+C para detener el servidor."); // Explica cómo finalizar el proceso local.
            System.out.println("=========================================================================="); // Cierra el mensaje de inicio.

            communicator.waitForShutdown(); // Mantiene el proceso activo hasta recibir una orden de apagado.
        } catch (Exception e) { // Maneja errores de configuración, inicio o comunicación del adaptador.
            System.err.println("[ERROR] Error crítico al iniciar servidor ZeroC Ice: " + e.getMessage()); // Expone un resumen del fallo.
            e.printStackTrace(); // Conserva la traza para diagnóstico técnico.
        } finally { // Garantiza que el hilo programado se cierre incluso ante errores de arranque.
            if (servant != null) { // Evita cerrar una instancia que no alcanzó a construirse.
                servant.close(); // Detiene el limpiador periódico de bloqueos.
            } // Finaliza la liberación condicionada del planificador.
        } // Finaliza el manejo de errores y limpieza del servidor.
    } // Finaliza el punto de entrada del servidor.

    private static void printNetworkInterfaces(int tcpPort, int wsPort) { // Muestra cómo se conectan clientes de otros equipos.
        try { // Protege el inicio si el sistema no permite enumerar interfaces.
            System.out.println("\n[INFO] Direcciones de red para conexión de clientes remotos:"); // Anuncia la lista de direcciones.
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces(); // Obtiene las interfaces disponibles en el host.
            while (interfaces.hasMoreElements()) { // Recorre cada interfaz de red detectada.
                NetworkInterface iface = interfaces.nextElement(); // Lee la interfaz actual.
                if (iface.isLoopback() || !iface.isUp()) continue; // Omite loopback y dispositivos inactivos.
                Enumeration<InetAddress> addresses = iface.getInetAddresses(); // Obtiene las direcciones de la interfaz actual.
                while (addresses.hasMoreElements()) { // Recorre las direcciones asignadas a esa interfaz.
                    InetAddress addr = addresses.nextElement(); // Lee la dirección de red actual.
                    if (addr instanceof java.net.Inet4Address) { // Publica ejemplos IPv4 que son fáciles de usar en el laboratorio.
                        System.out.printf("   • %s (%s):%n", iface.getDisplayName(), addr.getHostAddress()); // Identifica interfaz y dirección.
                        System.out.printf("       -> TCP: %s -h %s -p %d%n", SERVICE_IDENTITY, addr.getHostAddress(), tcpPort); // Imprime el proxy TCP nativo.
                        System.out.printf("       -> WS : ws://%s:%d%n", addr.getHostAddress(), wsPort); // Imprime el endpoint WebSocket para navegador.
                    } // Finaliza la selección de direcciones IPv4.
                } // Finaliza el recorrido de direcciones.
            } // Finaliza el recorrido de interfaces.
        } catch (Exception ignored) { // Omite la lista opcional si la consulta de red no está disponible.
        } // Finaliza la enumeración opcional de interfaces.
    } // Finaliza la consulta opcional de interfaces de red.
} // Finaliza la aplicación del servidor.
