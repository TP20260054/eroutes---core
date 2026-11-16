package tp20260054.eroutes.routing.costo;

/**
 * Pesos normalizados de un preset. Cada componente pondera el costo normalizado
 * y deben sumar 1 (la ruta de referencia queda con costo 1 para los términos
 * tiempo, distancia y cruce). Se valida en la construcción.
 */
public record PesosPreset(
        double tiempo,
        double distancia,
        double exposicion,
        double noValidado,
        double cruce) {

    public PesosPreset {
        double suma = tiempo + distancia + exposicion + noValidado + cruce;
        if (Double.isFinite(suma) && Math.abs(suma - 1.0) > 1e-9) {
            throw new IllegalArgumentException(
                    "Los pesos del preset deben sumar 1, pero suman " + suma);
        }
    }
}
