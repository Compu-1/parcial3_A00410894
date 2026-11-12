export function iceLongToNumber(value) { // Converts Slice 64-bit values to JavaScript numbers used by dates and timers.
    if (typeof value === "number") return value; // Preserves numeric values when a mapping already made the conversion.
    if (value && typeof value.toNumber === "function") return value.toNumber(); // Uses the supported Ice.Long conversion method.
    throw new TypeError("El valor RPC no es un número ni un Ice.Long válido."); // Exposes an invalid contract value instead of rendering NaN.
} // Finaliza la conversión segura de valores long.
