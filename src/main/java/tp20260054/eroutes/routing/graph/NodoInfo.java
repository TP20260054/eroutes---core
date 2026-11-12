package tp20260054.eroutes.routing.graph;

/**
 * Información de un nodo en el grafo en memoria.
 *
 * @param osmId         id OSM del nodo
 * @param lat           latitud
 * @param lng           longitud
 * @param control       valor del tag highway del nodo (traffic_signals, stop, crossing…), o null
 * @param riesgoCruce   riesgo de cruce precalculado [0, 1]
 */
public record NodoInfo(
        long osmId,
        double lat,
        double lng,
        String control,
        double riesgoCruce) {
}
