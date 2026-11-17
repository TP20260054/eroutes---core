package tp20260054.eroutes.recalculo;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import org.junit.jupiter.api.Test;

import tp20260054.eroutes.routing.graph.Arista;

/**
 * Tests unitarios de {@link GeoRuta}. Sin Spring ni base de datos.
 *
 * <p>Las aristas sintéticas usan longitudes declaradas que son mayores o iguales
 * a la distancia haversine real entre sus puntos de geometría, garantizando que
 * la heurística del ShortPaper es admisible.
 */
class GeoRutaTest {

    // Coordenadas aproximadas de San Isidro, Lima (suficientes para los tests)
    private static final double LAT0 = -12.0931;
    private static final double LNG0 = -77.0465;
    // ~100 m al norte (~0.0009°)
    private static final double LAT1 = -12.0922;
    // ~100 m al este (~0.001° en esta latitud)
    private static final double LNG1 = -77.0455;
    // ~100 m más al norte
    private static final double LAT2 = -12.0913;

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Arista con dos puntos de geometría. */
    private static Arista arista(long id, double lat0, double lng0,
                                 double lat1, double lng1, double longM) {
        double[][] geom = {{lat0, lng0}, {lat1, lng1}};
        return new Arista(0, id, null, "residential", longM, null, true, false, geom);
    }

    /** Arista con tres puntos de geometría (tramo con quiebre). */
    private static Arista aristaTriple(long id, double lat0, double lng0,
                                       double latM, double lngM,
                                       double lat1, double lng1, double longM) {
        double[][] geom = {{lat0, lng0}, {latM, lngM}, {lat1, lng1}};
        return new Arista(0, id, null, "residential", longM, null, true, false, geom);
    }

    // -------------------------------------------------------------------------
    // polilínea
    // -------------------------------------------------------------------------

    @Test
    void polilinea_dosBAristas_sinDuplicados() {
        Arista a1 = arista(1, LAT0, LNG0, LAT1, LNG1, 150.0);
        Arista a2 = arista(2, LAT1, LNG1, LAT2, LNG1, 100.0);
        double[][] pts = GeoRuta.polilínea(List.of(a1, a2));
        // 2 pts + 2 pts - 1 solapamiento = 3
        assertThat(pts.length).isEqualTo(3);
        assertThat(pts[0]).containsExactly(LAT0, LNG0);
        assertThat(pts[1]).containsExactly(LAT1, LNG1);
        assertThat(pts[2]).containsExactly(LAT2, LNG1);
    }

    @Test
    void polilinea_aristaTriple_incluyePuntoIntermedio() {
        Arista a = aristaTriple(1, LAT0, LNG0, LAT1, LNG0, LAT1, LNG1, 200.0);
        double[][] pts = GeoRuta.polilínea(List.of(a));
        assertThat(pts.length).isEqualTo(3);
        assertThat(pts[1]).containsExactly(LAT1, LNG0);
    }

    @Test
    void polilinea_listaVacia_arrayVacio() {
        assertThat(GeoRuta.polilínea(List.of())).isEmpty();
    }

    // -------------------------------------------------------------------------
    // longitudRestante
    // -------------------------------------------------------------------------

    @Test
    void longitudRestante_desdeInicio_sumaTodo() {
        List<Arista> aristas = List.of(
                arista(1, LAT0, LNG0, LAT1, LNG1, 100.0),
                arista(2, LAT1, LNG1, LAT2, LNG1, 80.0));
        assertThat(GeoRuta.longitudRestante(aristas, 0)).isEqualTo(180.0);
    }

    @Test
    void longitudRestante_desdeSegunda_soloSegunda() {
        List<Arista> aristas = List.of(
                arista(1, LAT0, LNG0, LAT1, LNG1, 100.0),
                arista(2, LAT1, LNG1, LAT2, LNG1, 80.0));
        assertThat(GeoRuta.longitudRestante(aristas, 1)).isEqualTo(80.0);
    }

    @Test
    void longitudRestante_masDel_final_cero() {
        List<Arista> aristas = List.of(arista(1, LAT0, LNG0, LAT1, LNG1, 100.0));
        assertThat(GeoRuta.longitudRestante(aristas, 5)).isEqualTo(0.0);
    }

    // -------------------------------------------------------------------------
    // distanciaHastaTramo / tramoEnRutaRestante
    // -------------------------------------------------------------------------

    @Test
    void distanciaHastaTramo_primerTramo_esZero() {
        List<Arista> aristas = List.of(
                arista(10, LAT0, LNG0, LAT1, LNG1, 100.0),
                arista(20, LAT1, LNG1, LAT2, LNG1, 80.0));
        assertThat(GeoRuta.distanciaHastaTramo(aristas, 0, 10L)).isEqualTo(0.0);
    }

    @Test
    void distanciaHastaTramo_segundoTramo_esPrimeraLongitud() {
        List<Arista> aristas = List.of(
                arista(10, LAT0, LNG0, LAT1, LNG1, 100.0),
                arista(20, LAT1, LNG1, LAT2, LNG1, 80.0));
        assertThat(GeoRuta.distanciaHastaTramo(aristas, 0, 20L)).isEqualTo(100.0);
    }

    @Test
    void distanciaHastaTramo_tramoAusente_maxValue() {
        List<Arista> aristas = List.of(arista(10, LAT0, LNG0, LAT1, LNG1, 100.0));
        assertThat(GeoRuta.distanciaHastaTramo(aristas, 0, 99L)).isEqualTo(Double.MAX_VALUE);
    }

    @Test
    void tramoEnRutaRestante_tramoDelante_true() {
        List<Arista> aristas = List.of(
                arista(10, LAT0, LNG0, LAT1, LNG1, 100.0),
                arista(20, LAT1, LNG1, LAT2, LNG1, 80.0));
        assertThat(GeoRuta.tramoEnRutaRestante(aristas, 0, 20L)).isTrue();
    }

    @Test
    void tramoEnRutaRestante_tramoDetras_false() {
        List<Arista> aristas = List.of(
                arista(10, LAT0, LNG0, LAT1, LNG1, 100.0),
                arista(20, LAT1, LNG1, LAT2, LNG1, 80.0));
        // Desde arista 1, el tramo 10 ya quedó atrás
        assertThat(GeoRuta.tramoEnRutaRestante(aristas, 1, 10L)).isFalse();
    }

    // -------------------------------------------------------------------------
    // distPuntoSegmento (casos exactos en plano local)
    // -------------------------------------------------------------------------

    @Test
    void distPuntoSegmento_puntoEnMedio_ceroCasi() {
        // Segmento horizontal en lat=0, lng de 0 a 1°
        double[] A = {0.0, 0.0};
        double[] B = {0.0, 0.001};  // ~111 m
        double cosLat = Math.cos(0.0);
        // Punto en el centro del segmento (lat=0, lng=0.0005): distancia ≈ 0
        double d = GeoRuta.distPuntoSegmento(0.0, 0.0005, A, B, cosLat);
        assertThat(d).isCloseTo(0.0, within(1.0));
    }

    @Test
    void distPuntoSegmento_puntoPerpendicularConDesplazamiento() {
        // Segmento de A=(0,0) a B=(0,0.001): ~111 m horizontal
        // Punto en (0.0001, 0.0005): ~11 m al norte del centro
        double[] A = {0.0, 0.0};
        double[] B = {0.0, 0.001};
        double cosLat = Math.cos(0.0);
        double d = GeoRuta.distPuntoSegmento(0.0001, 0.0005, A, B, cosLat);
        // La proyección cae en el centro; la distancia es la desviación latitudinal ≈ 11.1 m
        assertThat(d).isCloseTo(11.1, within(1.0));
    }

    @Test
    void distPuntoSegmento_puntoAntesDelInicio_distanciaAlExtremo() {
        double[] A = {0.0, 0.001};
        double[] B = {0.0, 0.002};
        double cosLat = Math.cos(0.0);
        // Punto muy a la izquierda de A: proyección clampea a A
        double d = GeoRuta.distPuntoSegmento(0.0, -0.001, A, B, cosLat);
        // Distancia de (0,−0.001) a A=(0,0.001) ≈ 222 m
        assertThat(d).isCloseTo(222.4, within(5.0));
    }

    // -------------------------------------------------------------------------
    // proyectar
    // -------------------------------------------------------------------------

    @Test
    void proyectar_puntoCercaAlInicio_indiceSeg0() {
        Arista a1 = arista(1, LAT0, LNG0, LAT1, LNG1, 150.0);
        Arista a2 = arista(2, LAT1, LNG1, LAT2, LNG1, 100.0);
        double[][] pts = GeoRuta.polilínea(List.of(a1, a2));

        // Punto muy cercano al inicio
        GeoRuta.ProyeccionRuta p = GeoRuta.proyectar(LAT0 + 0.00001, LNG0, pts, 0);
        assertThat(p.indiceSeg()).isEqualTo(0);
        assertThat(p.distanciaM()).isLessThan(5.0);
    }

    @Test
    void proyectar_progreso_monotono() {
        Arista a1 = arista(1, LAT0, LNG0, LAT1, LNG1, 150.0);
        Arista a2 = arista(2, LAT1, LNG1, LAT2, LNG1, 100.0);
        double[][] pts = GeoRuta.polilínea(List.of(a1, a2));

        // Desde índice 1, no puede retroceder al segmento 0 aunque el punto esté allí
        GeoRuta.ProyeccionRuta p = GeoRuta.proyectar(LAT0, LNG0, pts, 1);
        assertThat(p.indiceSeg()).isGreaterThanOrEqualTo(1);
    }

    // -------------------------------------------------------------------------
    // puntoAArista
    // -------------------------------------------------------------------------

    @Test
    void puntoAArista_puntoEnPrimeraArista() {
        Arista a1 = arista(1, LAT0, LNG0, LAT1, LNG1, 150.0);        // 2 pts
        Arista a2 = aristaTriple(2, LAT1, LNG1, LAT2, LNG0, LAT2, LNG1, 200.0); // 3 pts
        List<Arista> aristas = List.of(a1, a2);
        // Puntos de a1: 0,1; puntos de a2: 1,2,3 (global)
        // Segmento 0 → arista 0
        assertThat(DetectorDesvio.puntoAArista(aristas, 0)).isEqualTo(0);
    }

    @Test
    void puntoAArista_segmentoInteraristico() {
        // a1: 2 pts (segmento 0); a2: 2 pts (segmento 1)
        Arista a1 = arista(1, LAT0, LNG0, LAT1, LNG1, 150.0);
        Arista a2 = arista(2, LAT1, LNG1, LAT2, LNG1, 100.0);
        List<Arista> aristas = List.of(a1, a2);
        assertThat(DetectorDesvio.puntoAArista(aristas, 1)).isEqualTo(1);
    }

    @Test
    void puntoAArista_masAllaDelFinal_ultimaArista() {
        Arista a1 = arista(1, LAT0, LNG0, LAT1, LNG1, 150.0);
        assertThat(DetectorDesvio.puntoAArista(List.of(a1), 99)).isEqualTo(0);
    }
}
