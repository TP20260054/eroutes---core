package tp20260054.eroutes.routing.costo;

/**
 * Ruta de referencia usada para normalizar el costo de un preset.
 *
 * @param tRefS tiempo total de la ruta de referencia (segundos), calculada con costo TIEMPO
 * @param lRefM longitud total de la ruta de referencia (metros)
 * @param cRef  cantidad de cruces de riesgo (riesgoCruce &gt;= 0.5) de la referencia
 */
public record ReferenciaRuta(double tRefS, double lRefM, int cRef) {
}
