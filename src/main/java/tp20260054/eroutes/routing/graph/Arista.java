package tp20260054.eroutes.routing.graph;

/**
 * Arista del grafo en memoria. Representa un tramo dirigido.
 *
 * @param destino    índice del nodo destino en el grafo
 * @param tramoId    id de la tabla tramos
 * @param nombre     nombre de la calle (puede ser null)
 * @param tipoVia    tipo de vía OSM (puede ser null)
 * @param longitudM  longitud en metros
 * @param velocidadMax velocidad máxima (km/h), puede ser null
 * @param validado   si el tramo fue validado manualmente
 * @param esCiclovia si es una infraestructura dedicada de ciclista
 * @param geom       polilínea [[lat,lng], ...] en sentido origen→destino
 */
public record Arista(
        int destino,
        long tramoId,
        String nombre,
        String tipoVia,
        double longitudM,
        Integer velocidadMax,
        boolean validado,
        boolean esCiclovia,
        double[][] geom) {
}
