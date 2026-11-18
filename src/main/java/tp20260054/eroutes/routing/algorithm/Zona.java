package tp20260054.eroutes.routing.algorithm;

/**
 * Rectángulo de búsqueda (bounding box) origen-destino ampliado por un margen en
 * metros. Usado por {@link AStar} para acotar la expansión de nodos.
 */
public record Zona(double minLat, double minLng, double maxLat, double maxLng) {

    /** ~111 320 m por grado (aprox.; misma convención que BusquedaService). */
    private static final double M_POR_GRADO = 111_320.0;

    public static Zona alrededor(double latOrigen, double lngOrigen,
                                 double latDestino, double lngDestino, double margenM) {
        double d = margenM / M_POR_GRADO;
        return new Zona(
                Math.min(latOrigen, latDestino) - d,
                Math.min(lngOrigen, lngDestino) - d,
                Math.max(latOrigen, latDestino) + d,
                Math.max(lngOrigen, lngDestino) + d);
    }

    public boolean contiene(double lat, double lng) {
        return lat >= minLat && lat <= maxLat && lng >= minLng && lng <= maxLng;
    }
}
