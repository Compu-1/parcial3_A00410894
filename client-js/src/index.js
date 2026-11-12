import "./styles.css"; // Carga los estilos junto al bundle de JavaScript.
import { mountBookingApp } from "./app.js"; // Importa el punto de entrada de la aplicación.

const root = document.querySelector("#app"); // Encuentra el contenedor declarado en la plantilla HTML.
if (!root) throw new Error("No se encontró el elemento de montaje #app."); // Hace visible un HTML mal configurado en lugar de fallar en silencio.
mountBookingApp(root); // Inicializa componentes y conexión RPC.
