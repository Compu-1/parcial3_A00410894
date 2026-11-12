const path = require("path"); // Resuelve el contrato Slice desde la raíz de este proyecto web.
const fs = require("fs"); // Permite crear el directorio requerido por el compilador.
const sliceCompiler = require("slice2js"); // Accede al compilador oficial distribuido con el paquete npm.

const sliceFile = path.resolve(__dirname, "../../codigo_base/common/src/main/slice/BookingService.ice"); // Reutiliza el contrato compartido con Java.
const outputDirectory = path.resolve(__dirname, "../src/generated"); // Mantiene los stubs fuera del código escrito a mano.
fs.mkdirSync(outputDirectory, { recursive: true }); // Crea la carpeta que slice2js no genera automáticamente.
const compiler = sliceCompiler.compile(["--output-dir", outputDirectory, sliceFile], { stdio: "inherit" }); // Ejecuta el compilador nativo de la versión Ice instalada.

compiler.on("error", error => { // Expone errores de inicio del compilador.
    console.error("No fue posible iniciar slice2js:", error); // Describe el fallo de generación en la consola.
    process.exitCode = 1; // Hace fallar npm start o npm run build ante un problema real.
}); // Finaliza el control de errores de inicio.

compiler.on("close", exitCode => { // Comprueba el resultado del compilador una vez finalizado.
    if (exitCode !== 0) process.exitCode = exitCode || 1; // Propaga errores de Slice al comando npm.
}); // Finaliza el control del resultado.
