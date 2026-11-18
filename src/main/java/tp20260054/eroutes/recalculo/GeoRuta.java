package tp20260054.eroutes.recalculo;

import java.util.List;
import tp20260054.eroutes.routing.graph.Arista;
import tp20260054.eroutes.routing.graph.GrafoEnMemoria;

/**
 * Utilidades geométricas sobre la polilínea de una ruta activa.
 *
 * <p>Todo puro y sin dependencias de Spring; testeable directamente.
 * Las coordenadas de cada arista están en {@code double[2]  = {lat, lng}}.
 *
 * <h3>Plano local equirectangular</h3>
 * Para proyecciones punto-segmento se usa la aproximación equirectangular
 * ({@code x = lng · cos(lat_ref)}, {@code y = lat}), suficiente para las
 * distancias cortas (< 5 km) del caso de uso.
 */
public final class GeoRuta {

    private GeoRuta() {}

    // -------------------------------------------------------------------------
    // Polilínea de la ruta completa
    // -------------------------------------------------------------------------

    /**
     * Polilínea completa a partir de la lista de aristas, sin puntos duplicados
     * entre aristas consecutivas. Cada punto es {@code {lat, lng}}.
     */
    public static double[][] polilínea(List<Arista> aristas) {
        if (aristas == null || aristas.isEmpty()) return new double[0][];
        int total = 0;
        for (Arista a : aristas) total += a.geom().length;
        total -= (aristas.size() - 1); // solapa entre aristas consecutivas

        double[][] pts = new double[total][2];
        int idx = 0;
        for (int i = 0; i < aristas.size(); i++) {
            double[][] geom = aristas.get(i).geom();
            int inicio = (i == 0) ? 0 : 1;
            for (int j = inicio; j < geom.length; j++) {
                pts[idx][0] = geom[j][0];
                pts[idx][1] = geom[j][1];
                idx++;
            }
        }
        return pts;
    }

    // -------------------------------------------------------------------------
    // Longitud restante desde un índice de arista
    // -------------------------------------------------------------------------

    /**
     * Longitud restante de la ruta (en metros) sumando las aristas desde
     * {@code desdeArista} (inclusive) hasta el final.
     */
    public static double longitudRestante(List<Arista> aristas, int desdeArista) {
        if (aristas == null || desdeArista >= aristas.size()) return 0.0;
        double suma = 0.0;
        for (int i = desdeArista; i < aristas.size(); i++) {
            suma += aristas.get(i).longitudM();
        }
        return suma;
    }

    // -------------------------------------------------------------------------
    // Distancia a lo largo de la ruta hasta un tramoId
    // -------------------------------------------------------------------------

    /**
     * Distancia a lo largo de la ruta (en metros) desde la arista {@code desdeArista}
     * hasta el inicio del primer tramo con {@code tramoId}, buscando solo hacia
     * adelante. Devuelve {@link Double#MAX_VALUE} si el tramo no está en la ruta
     * restante.
     */
    public static double distanciaHastaTramo(List<Arista> aristas, int desdeArista, long tramoId) {
        if (aristas == null) return Double.MAX_VALUE;
        double acum = 0.0;
        for (int i = desdeArista; i < aristas.size(); i++) {
            if (aristas.get(i).tramoId() == tramoId) return acum;
            acum += aristas.get(i).longitudM();
        }
        return Double.MAX_VALUE;
    }

    /**
     * Indica si {@code tramoId} está en la ruta restante (desde {@code desdeArista}).
     */
    public static boolean tramoEnRutaRestante(List<Arista> aristas, int desdeArista, long tramoId) {
        return distanciaHastaTramo(aristas, desdeArista, tramoId) != Double.MAX_VALUE;
    }

    // -------------------------------------------------------------------------
    // Proyección punto-segmento y distancia mínima a la polilínea
    // -------------------------------------------------------------------------

    /**
     * Distancia mínima (en metros) del punto {@code (lat, lng)} a la polilínea
     * definida por {@code pts}, buscando solo desde el punto {@code desdeIdx}
     * hacia adelante (progreso monótono). También actualiza el mejor índice de
     * segmento encontrado, útil para avanzar {@code indiceProgreso}.
     *
     * <p>Devuelve {@link ProyeccionRuta} con la distancia mínima y el índice del
     * segmento más cercano en la polilínea.
     */
    public static ProyeccionRuta proyectar(double lat, double lng,
                                           double[][] pts, int desdeIdx) {
        if (pts == null || pts.length < 2) {
            return new ProyeccionRuta(Double.MAX_VALUE, desdeIdx);
        }
        // Latitud de referencia para la escala longitudinal
        double latRef = Math.toRadians(lat);
        double cosLat = Math.cos(latRef);

        double mejorDist = Double.MAX_VALUE;
        int mejorSeg = Math.max(0, desdeIdx);

        int inicio = Math.max(0, desdeIdx);
        for (int i = inicio; i < pts.length - 1; i++) {
            double d = distPuntoSegmento(lat, lng, pts[i], pts[i + 1], cosLat);
            if (d < mejorDist) {
                mejorDist = d;
                mejorSeg = i;
            }
        }
        return new ProyeccionRuta(mejorDist, mejorSeg);
    }

    // -------------------------------------------------------------------------
    // Helpers geométricos internos
    // -------------------------------------------------------------------------

    /**
     * Distancia (en metros) del punto P al segmento AB usando plano local
     * equirectangular. {@code cosLat} es {@code cos(lat_P)} precomputado.
     */
    static double distPuntoSegmento(double latP, double lngP,
                                    double[] A, double[] B, double cosLat) {
        final double R = 6_371_000.0;
        // Convertir a metros en plano local (origen = P)
        double ay = (A[0] - latP) * (Math.PI / 180) * R;
        double ax = (A[1] - lngP) * (Math.PI / 180) * R * cosLat;
        double by = (B[0] - latP) * (Math.PI / 180) * R;
        double bx = (B[1] - lngP) * (Math.PI / 180) * R * cosLat;
        // P es el origen (0, 0); proyección de P sobre AB
        double dx = bx - ax;
        double dy = by - ay;
        double len2 = dx * dx + dy * dy;
        if (len2 == 0.0) {
            // Segmento degenerado: distancia a A
            return Math.sqrt(ax * ax + ay * ay);
        }
        double t = ((-ax) * dx + (-ay) * dy) / len2;
        t = Math.max(0.0, Math.min(1.0, t));
        double px = ax + t * dx;
        double py = ay + t * dy;
        return Math.sqrt(px * px + py * py);
    }

    /** Resultado de la proyección más cercana sobre la polilínea. */
    public record ProyeccionRuta(double distanciaM, int indiceSeg) {}
}
