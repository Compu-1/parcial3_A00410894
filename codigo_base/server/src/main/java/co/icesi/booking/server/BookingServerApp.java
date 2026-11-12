package co.icesi.booking.server;

import com.zeroc.Ice.Communicator;
import com.zeroc.Ice.InitializationData;
import com.zeroc.Ice.ObjectAdapter;
import com.zeroc.Ice.Util;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

public class BookingServerApp {

    public static final int DEFAULT_PORT = 10000;
    public static final String SERVICE_IDENTITY = "BookingService";

    public static void main(String[] args) {
        int port = DEFAULT_PORT;
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--port") && i + 1 < args.length) {
                try {
                    port = Integer.parseInt(args[i + 1]);
                } catch (NumberFormatException ignored) {
                }
            }
        }

        System.out.println("==========================================================================");
        System.out.println("  SERVIDOR ZEROC ICE - SISTEMA DE RESERVA DE BOLETAS (COMPU 1)           ");
        System.out.println("==========================================================================");

        InitializationData initData = new InitializationData();
        initData.properties = Util.createProperties();
        // Parámetros de rendimiento y soporte para clientes WebSocket
        initData.properties.setProperty("Ice.MessageSizeMax", "4096");
        initData.properties.setProperty("Ice.ThreadPool.Server.Size", "10");
        initData.properties.setProperty("Ice.ThreadPool.Server.SizeMax", "100");

        try (Communicator communicator = Util.initialize(args, initData)) {
            int wsPort = port + 2; // Puerto 10002 para WebSocket de navegadores
            // Endpoint dual: TCP para clientes nativos (puerto 10000) y WebSocket para clientes Web (puerto 10002)
            String endpoint = String.format("default -p %d:ws -h 0.0.0.0 -p %d", port, wsPort);
            ObjectAdapter adapter = communicator.createObjectAdapterWithEndpoints("BookingServerAdapter", endpoint);

            BookingServiceImpl servant = new BookingServiceImpl();
            adapter.add(servant, Util.stringToIdentity(SERVICE_IDENTITY));
            adapter.activate();

            System.out.printf("[OK] Servidor ZeroC Ice activo en endpoints: %s%n", endpoint);
            System.out.printf("[OK] Canal TCP (Clientes Java/Escritorio)   : localhost:%d%n", port);
            System.out.printf("[OK] Canal WebSocket (Navegadores Web / JS) : ws://localhost:%d%n", wsPort);
            System.out.printf("[OK] Proxy Identity                         : %s%n", SERVICE_IDENTITY);
            printNetworkInterfaces(port, wsPort);
            System.out.println("--------------------------------------------------------------------------");
            System.out.println("[INFO] Catálogo inicializado: Concierto Icesi Live Arena (90 asientos).");
            System.out.println("[ALERTA] Concurrencia intencionalmente incompleta para fines didácticos.");
            System.out.println("[INFO] Presione Ctrl+C para detener el servidor.");
            System.out.println("==========================================================================");

            communicator.waitForShutdown();
        } catch (Exception e) {
            System.err.println("[ERROR] Error crítico al iniciar servidor ZeroC Ice: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void printNetworkInterfaces(int tcpPort, int wsPort) {
        try {
            System.out.println("\n[INFO] Direcciones de red para conexión de clientes remotos:");
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (iface.isLoopback() || !iface.isUp()) continue;
                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr instanceof java.net.Inet4Address) {
                        System.out.printf("   • %s (%s):%n", iface.getDisplayName(), addr.getHostAddress());
                        System.out.printf("       -> TCP: %s -h %s -p %d%n", SERVICE_IDENTITY, addr.getHostAddress(), tcpPort);
                        System.out.printf("       -> WS : ws://%s:%d%n", addr.getHostAddress(), wsPort);
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }
}
