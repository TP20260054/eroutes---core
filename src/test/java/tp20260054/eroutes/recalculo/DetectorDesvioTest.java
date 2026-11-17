package tp20260054.eroutes.recalculo;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import tp20260054.eroutes.routing.graph.Arista;

/**
 * Tests unitarios de {@link DetectorDesvio}. Sin Spring ni base de datos.
 *
 * <p>Escenarios:
 * <ul>
 *   <li>Umbral de 30 m, 2 muestras consecutivas (configuración por defecto del prompt)
 *   <li>Reinicio al volver a la ruta
 *   <li>Progreso monótono (el índice no retrocede)
 *   <li>Una sola muestra no dispara el desvío aunque esté lejos
 * </ul>
 */
class DetectorDesvioTest {

    // Ruta sintética: segmento horizontal de ~100 m en latitud ~0°
    // A = (0, 0), B = (0, 0.001) → ~111 m
    private static final double LAT_A = 0.0;
    private static final double LNG_A = 0.0;
    private static final double LAT_B = 0.0;
    private static final double LNG_B = 0.001; // ~111 m al este

    private static Arista aristaHorizontal() {
        double[][] geom = {{LAT_A, LNG_A}, {LAT_B, LNG_B}};
        return new Arista(0, 1L, null, "residential", 111.0, null, true, false, geom);
    }

    private static DetectorDesvio detector(double umbralM, int muestras) {
        return new DetectorDesvio(umbralM, muestras);
    }

    // -------------------------------------------------------------------------

    @Test
    void unaMuestraLejos_noDisparaDesvio() {
        DetectorDesvio det = detector(30.0, 2);
        DetectorDesvio.EstadoDesvio estado = new DetectorDesvio.EstadoDesvio(
                List.of(aristaHorizontal()));

        // Punto a ~111 m al norte del segmento
        boolean disparado = det.procesar(estado, 0.001, 0.0005);
        assertThat(disparado).isFalse();
        assertThat(estado.muestrasDesviadas()).isEqualTo(1);
    }

    @Test
    void dosMuestrasLejos_disparaDesvio() {
        DetectorDesvio det = detector(30.0, 2);
        DetectorDesvio.EstadoDesvio estado = new DetectorDesvio.EstadoDesvio(
                List.of(aristaHorizontal()));

        det.procesar(estado, 0.001, 0.0005);  // 1ª muestra lejana
        boolean disparado = det.procesar(estado, 0.001, 0.0006);  // 2ª
        assertThat(disparado).isTrue();
        assertThat(estado.muestrasDesviadas()).isEqualTo(2);
    }

    @Test
    void muestraCercana_reiniciaContador() {
        DetectorDesvio det = detector(30.0, 2);
        DetectorDesvio.EstadoDesvio estado = new DetectorDesvio.EstadoDesvio(
                List.of(aristaHorizontal()));

        det.procesar(estado, 0.001, 0.0005);  // lejana
        det.procesar(estado, 0.0, 0.0005);    // cercana al segmento (≈ 0 m)
        assertThat(estado.muestrasDesviadas()).isEqualTo(0);

        // Ahora hace falta 2 muestras lejanas consecutivas de nuevo
        det.procesar(estado, 0.001, 0.0005);
        boolean disparado = det.procesar(estado, 0.001, 0.0006);
        assertThat(disparado).isTrue();
    }

    @Test
    void reiniciarExplicito_borraCont() {
        DetectorDesvio det = detector(30.0, 2);
        DetectorDesvio.EstadoDesvio estado = new DetectorDesvio.EstadoDesvio(
                List.of(aristaHorizontal()));

        det.procesar(estado, 0.001, 0.0005);
        assertThat(estado.muestrasDesviadas()).isEqualTo(1);

        det.reiniciar(estado);
        assertThat(estado.muestrasDesviadas()).isEqualTo(0);
    }

    @Test
    void progresoMonotono_indiceNoRetrocede() {
        // Dos aristas consecutivas
        double[][] g1 = {{LAT_A, LNG_A}, {LAT_B, LNG_B}};
        double[][] g2 = {{LAT_B, LNG_B}, {LAT_B, LNG_B + 0.001}};
        Arista a1 = new Arista(0, 1L, null, "residential", 111.0, null, true, false, g1);
        Arista a2 = new Arista(0, 2L, null, "residential", 111.0, null, true, false, g2);

        DetectorDesvio det = detector(30.0, 2);
        DetectorDesvio.EstadoDesvio estado = new DetectorDesvio.EstadoDesvio(List.of(a1, a2));

        // Primera muestra cerca del inicio del segundo segmento
        det.procesar(estado, LAT_B, LNG_B + 0.0005);
        int indiceTras1 = estado.indicePunto();
        assertThat(indiceTras1).isGreaterThanOrEqualTo(1);

        // Segunda muestra cerca del inicio (retrocedeería sin el guard)
        det.procesar(estado, LAT_A, LNG_A + 0.0001);
        // El índice no debe retroceder
        assertThat(estado.indicePunto()).isGreaterThanOrEqualTo(indiceTras1);
    }

    @Test
    void progresoMonotono_avanzaHaciaAdelante() {
        double[][] g1 = {{LAT_A, LNG_A}, {LAT_B, LNG_B}};
        double[][] g2 = {{LAT_B, LNG_B}, {LAT_B, LNG_B + 0.001}};
        Arista a1 = new Arista(0, 1L, null, "residential", 111.0, null, true, false, g1);
        Arista a2 = new Arista(0, 2L, null, "residential", 111.0, null, true, false, g2);

        DetectorDesvio det = detector(30.0, 2);
        DetectorDesvio.EstadoDesvio estado = new DetectorDesvio.EstadoDesvio(List.of(a1, a2));

        det.procesar(estado, LAT_A, LNG_A);               // cerca del inicio
        int idx0 = estado.indicePunto();
        det.procesar(estado, LAT_B, LNG_B + 0.0005);      // cerca del 2º segmento
        assertThat(estado.indicePunto()).isGreaterThan(idx0);
        assertThat(estado.indiceArista()).isEqualTo(1);    // en la 2ª arista
    }

    @Test
    void umbralAjustado_muestraDentroNoDispara() {
        DetectorDesvio det = detector(200.0, 1); // umbral grande
        DetectorDesvio.EstadoDesvio estado = new DetectorDesvio.EstadoDesvio(
                List.of(aristaHorizontal()));
        // 111 m al norte: dentro del umbral de 200 m
        boolean disparado = det.procesar(estado, 0.001, 0.0005);
        assertThat(disparado).isFalse();
    }

    @Test
    void sesionRutaVacia_noDispara() {
        DetectorDesvio det = detector(30.0, 2);
        DetectorDesvio.EstadoDesvio estado = new DetectorDesvio.EstadoDesvio(List.of());
        boolean disparado = det.procesar(estado, 0.001, 0.0005);
        assertThat(disparado).isFalse();
    }
}
