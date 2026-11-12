const path = require("path"); // Resuelve rutas de forma independiente del sistema operativo.
const CopyWebpackPlugin = require("copy-webpack-plugin"); // Copia el runtime browser de Ice y los stubs Slice.
const HtmlWebpackPlugin = require("html-webpack-plugin"); // Genera el HTML con el bundle inyectado.

module.exports = (environment, arguments_) => ({ // Exporta la configuración utilizada por Webpack.
    entry: "./src/index.js", // Define el módulo que inicia la SPA.
    output: { // Configura los archivos optimizados que recibirá el navegador.
        path: path.resolve(__dirname, "dist"), // Ubica la distribución de producción dentro de client-js.
        filename: "assets/[name].[contenthash].js", // Versiona el bundle para evitar cachés obsoletas.
        clean: true // Elimina solamente la distribución anterior durante la compilación.
    }, // Finaliza las opciones de salida.
    module: { // Declara los cargadores requeridos por los módulos de la aplicación.
        rules: [ // Agrupa las reglas de transformación.
            { test: /\.css$/i, use: ["style-loader", "css-loader"] }, // Incluye CSS importado desde los componentes.
            { test: /\.html$/i, loader: "html-loader", options: { sources: false } } // Procesa el HTML y deja que CopyWebpackPlugin publique los scripts.
        ] // Finaliza las reglas de transformación.
    }, // Finaliza la configuración de módulos.
    plugins: [ // Registra los complementos que preparan la página.
        new HtmlWebpackPlugin({ template: "./src/index.html", title: "Icesi Live Arena" }), // Inserta el bundle en el HTML de entrada.
        new CopyWebpackPlugin({ patterns: [ // Publica los scripts oficiales antes del código de la SPA.
            { from: path.resolve(__dirname, "node_modules/ice/lib/Ice.min.js"), to: "assets/Ice.min.js" }, // Provee Ice como runtime global para navegadores.
            { from: path.resolve(__dirname, "src/generated/BookingService.js"), to: "assets/BookingService.js" } // Provee los proxies generados desde Slice.
        ] }) // Finaliza la copia de las dependencias de navegador.
    ], // Finaliza la lista de complementos.
    devServer: { // Configura el servidor de desarrollo requerido por el enunciado.
        host: "0.0.0.0", // Permite acceder desde otras máquinas de la red del laboratorio.
        port: 3000, // Usa el puerto solicitado para npm start.
        open: false, // Evita abrir ventanas automáticamente durante la sustentación.
        hot: true, // Actualiza módulos compatibles sin recargar toda la página.
        historyApiFallback: true // Sirve la SPA ante rutas que no son archivos estáticos.
    }, // Finaliza la configuración del servidor de desarrollo.
    devtool: arguments_.mode === "development" ? "eval-source-map" : "source-map" // Conserva trazas útiles sin alterar el bundle de producción.
}); // Finaliza y exporta la configuración Webpack.
