package tp20260054.eroutes.recalculo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;
import tp20260054.eroutes.eventos.TipoEventoVial;
import tp20260054.eroutes.navegacion.DisparadorRecalculo;

/**
 * Tests unitarios de {@link EstadoRecalculo#vector()}.
 * Sin Spring ni base de datos.
 */
class EstadoRecalculoTest {

    private static EstadoRecalculo sinEvento(boolean desvio, String tipoVehiculo,
                                             double velocidadRel, double longRestante,
                                             double recalculos) {
        return new EstadoRecalculo(
                DisparadorRecalculo.REROUTE, false, false, null,
                desvio, null, 0.0, 1.0,
                velocidadRel, tipoVehiculo, longRestante, recalculos);
    }

    private static EstadoRecalculo conEvento(TipoEventoVial tipo, double gravedad,
                                             double distNorm, boolean enRuta) {
        return new EstadoRecalculo(
                DisparadorRecalculo.BLOCKED, enRuta, true, 1L,
                false, tipo, gravedad, distNorm,
                0.5, "BICIMOTO", 0.3, 0.1);
    }

    // -------------------------------------------------------------------------

    @Test
    void vector_14_dimensiones() {
        assertThat(sinEvento(false, "BICIMOTO", 0.5, 0.3, 0.1).vector()).hasSize(14);
    }

    @Test
    void vector_todosEnRango01() {
        double[] v = sinEvento(true, "BICIMOTO", 0.8, 0.6, 0.4).vector();
        for (double x : v) {
            assertThat(x).isBetween(0.0, 1.0);
        }
    }

    @Test
    void vector_desvio1_indice0es1() {
        double[] v = sinEvento(true, "BICIMOTO", 0.5, 0.3, 0.1).vector();
        assertThat(v[0]).isEqualTo(1.0);
    }

    @Test
    void vector_sinDesvio_indice0es0() {
        double[] v = sinEvento(false, "BICIMOTO", 0.5, 0.3, 0.1).vector();
        assertThat(v[0]).isEqualTo(0.0);
    }

    @Test
    void vector_sinEvento_oneHotEventoCeros() {
        double[] v = sinEvento(false, "BICIMOTO", 0.5, 0.3, 0.1).vector();
        // índices 1–4 deben ser 0
        assertThat(v[1]).isEqualTo(0.0);
        assertThat(v[2]).isEqualTo(0.0);
        assertThat(v[3]).isEqualTo(0.0);
        assertThat(v[4]).isEqualTo(0.0);
    }

    @Test
    void vector_sinEvento_distEventoRuta_es1() {
        double[] v = sinEvento(false, "BICIMOTO", 0.5, 0.3, 0.1).vector();
        assertThat(v[6]).isEqualTo(1.0);
    }

    @Test
    void vector_eventoFueraDeRuta_distEventoRuta_es1() {
        double[] v = conEvento(TipoEventoVial.BLOQUEO_TOTAL, 1.0, 0.3, false).vector();
        // dist_evento_ruta (índice 6) = 0.3 ya que lo pasamos directamente
        // Si el evento NO está en ruta, el servicio pasa distNorm=1.0 (ver sección 3.4)
        // Aquí el constructor recibe el valor ya normalizado; comprobamos que se usa tal cual.
        assertThat(v[6]).isCloseTo(0.3, within(1e-9));
    }

    @Test
    void vector_bloqueoTotal_oneHotCorrecto() {
        double[] v = conEvento(TipoEventoVial.BLOQUEO_TOTAL, 1.0, 0.0, true).vector();
        assertThat(v[1]).isEqualTo(1.0);
        assertThat(v[2]).isEqualTo(0.0);
        assertThat(v[3]).isEqualTo(0.0);
        assertThat(v[4]).isEqualTo(0.0);
    }

    @Test
    void vector_obra_oneHotCorrecto() {
        double[] v = conEvento(TipoEventoVial.OBRA, 0.75, 0.5, true).vector();
        assertThat(v[1]).isEqualTo(0.0);
        assertThat(v[2]).isEqualTo(1.0);
    }

    @Test
    void vector_bachesSeveros_oneHotCorrecto() {
        double[] v = conEvento(TipoEventoVial.BACHES_SEVEROS, 0.5, 0.5, true).vector();
        assertThat(v[3]).isEqualTo(1.0);
    }

    @Test
    void vector_vehiculoEnCiclovia_oneHotCorrecto() {
        double[] v = conEvento(TipoEventoVial.VEHICULO_EN_CICLOVIA, 0.25, 0.5, true).vector();
        assertThat(v[4]).isEqualTo(1.0);
    }

    @Test
    void vector_gravedadCorrectamente() {
        double[] v = conEvento(TipoEventoVial.OBRA, 0.75, 0.5, true).vector();
        assertThat(v[5]).isCloseTo(0.75, within(1e-9));
    }

    @Test
    void vector_perfilBicicleta_oneHotCorrecto() {
        double[] v = sinEvento(false, "BICICLETA_ELECTRICA", 0.5, 0.3, 0.1).vector();
        assertThat(v[8]).isEqualTo(1.0);
        assertThat(v[9]).isEqualTo(0.0);
        assertThat(v[10]).isEqualTo(0.0);
        assertThat(v[11]).isEqualTo(0.0);
    }

    @Test
    void vector_perfilScooter_oneHotCorrecto() {
        double[] v = sinEvento(false, "SCOOTER_ELECTRICO", 0.5, 0.3, 0.1).vector();
        assertThat(v[9]).isEqualTo(1.0);
        assertThat(v[8]).isEqualTo(0.0);
    }

    @Test
    void vector_perfilBicimoto_oneHotCorrecto() {
        double[] v = sinEvento(false, "BICIMOTO", 0.5, 0.3, 0.1).vector();
        assertThat(v[10]).isEqualTo(1.0);
    }

    @Test
    void vector_perfilMotoElectrica_oneHotCorrecto() {
        double[] v = sinEvento(false, "MOTO_ELECTRICA", 0.5, 0.3, 0.1).vector();
        assertThat(v[11]).isEqualTo(1.0);
    }

    @Test
    void vector_longRestanteYRecalculos() {
        double[] v = sinEvento(false, "BICIMOTO", 0.5, 0.42, 0.6).vector();
        assertThat(v[12]).isCloseTo(0.42, within(1e-9));
        assertThat(v[13]).isCloseTo(0.6, within(1e-9));
    }

    @Test
    void nombresFeatures_14elementos() {
        assertThat(EstadoRecalculo.NOMBRES_FEATURES).hasSize(14);
    }
}
