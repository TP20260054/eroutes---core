package tp20260054.eroutes.recalculo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Tests unitarios de {@link MlpModel}. Sin Spring ni base de datos.
 * Usa el modelo de prueba en {@code src/test/resources/dql/modelo-test.json}.
 */
class MlpModelTest {

    private static byte[] modeloBytes;
    private static RecalculoProperties propsOk;

    @BeforeAll
    static void cargarRecursos() throws IOException {
        Path p = Path.of("src/test/resources/dql/modelo-test.json");
        modeloBytes = Files.readAllBytes(p);
        propsOk = props(1000, 5000, 5, 300);
    }

    private static RecalculoProperties props(double distMax, double longMax,
                                             int maxNorm, int ventana) {
        return new RecalculoProperties(
                30, 2, 120, 500, 15, 1000, ventana, maxNorm, distMax, longMax,
                0.05, "AI", true, new RecalculoProperties.Modelo(""));
    }

    // -------------------------------------------------------------------------
    // Carga y disponibilidad
    // -------------------------------------------------------------------------

    @Test
    void carga_rutaVacia_noDisponible() {
        MlpModel m = MlpModel.cargar("", propsOk);
        assertThat(m.estaDisponible()).isFalse();
        assertThat(m.motivoNoDisponible()).contains("MODELO_NO_DISPONIBLE");
    }

    @Test
    void carga_archivoInexistente_noDisponible() {
        MlpModel m = MlpModel.cargar("/tmp/no-existe-eroutes.json", propsOk);
        assertThat(m.estaDisponible()).isFalse();
    }

    @Test
    void carga_modeloValido_disponible() {
        MlpModel m = MlpModel.cargarDesdeBytes(modeloBytes, propsOk);
        assertThat(m.estaDisponible()).isTrue();
        assertThat(m.version()).isEqualTo("dql-test-manual");
    }

    // -------------------------------------------------------------------------
    // Validaciones de contenido
    // -------------------------------------------------------------------------

    @Test
    void rechazo_featuresDistintas() throws Exception {
        String json = new String(modeloBytes)
                .replace("\"desvio\"", "\"otro_nombre\"");
        MlpModel m = MlpModel.cargarDesdeBytes(json.getBytes(), propsOk);
        assertThat(m.estaDisponible()).isFalse();
        assertThat(m.motivoNoDisponible()).contains("features");
    }

    @Test
    void rechazo_normalizacionDistinta() {
        RecalculoProperties propsDistintos = props(999, 5000, 5, 300); // distMax diferente
        MlpModel m = MlpModel.cargarDesdeBytes(modeloBytes, propsDistintos);
        assertThat(m.estaDisponible()).isFalse();
        assertThat(m.motivoNoDisponible()).contains("dist_evento_max_m");
    }

    @Test
    void rechazo_normalizacionVentanaDistinta() {
        RecalculoProperties propsDistintos = props(1000, 5000, 5, 999);
        MlpModel m = MlpModel.cargarDesdeBytes(modeloBytes, propsDistintos);
        assertThat(m.estaDisponible()).isFalse();
        assertThat(m.motivoNoDisponible()).contains("ventana_recalculos_s");
    }

    @Test
    void rechazo_jsonMalformado() {
        MlpModel m = MlpModel.cargarDesdeBytes("{esto no es json".getBytes(), propsOk);
        assertThat(m.estaDisponible()).isFalse();
    }

    @Test
    void rechazo_salidasDistintasDe3() throws Exception {
        // Construimos un JSON con una capa final de 2 salidas
        String json = new String(modeloBytes);
        // Remplaza la última capa por una de 2×4
        String modelo2salidas = json.replaceAll(
            "\"activacion\": \"lineal\"",
            "\"activacion\": \"linealX\""  // activación inválida → se trata como lineal, OK
        );
        // Para forzar el error de salidas lo hacemos cortando la capa
        // Es más directo construir un JSON inválido desde cero
        String json2sal = """
                {
                  "formato":1,"version":"v","entrada_dim":14,
                  "features":[%s],
                  "normalizacion":{"dist_evento_max_m":1000,"long_restante_max_m":5000,
                                   "max_recalculos_norm":5,"ventana_recalculos_s":300},
                  "acciones":["A","B"],
                  "capas":[{"w":[[1,0,0,0,0,0,0,0,0,0,0,0,0,0],
                                 [0,1,0,0,0,0,0,0,0,0,0,0,0,0]],
                             "b":[0,0],"activacion":"lineal"}]
                }
                """.formatted(EstadoRecalculo.NOMBRES_FEATURES.stream()
                        .map(s -> "\"" + s + "\"").reduce((a, b) -> a + "," + b).orElse(""));
        MlpModel m = MlpModel.cargarDesdeBytes(json2sal.getBytes(), propsOk);
        assertThat(m.estaDisponible()).isFalse();
        assertThat(m.motivoNoDisponible()).contains("3 salidas");
    }

    // -------------------------------------------------------------------------
    // Pasada hacia adelante (valores calculados a mano en el script Python)
    // -------------------------------------------------------------------------

    @Test
    void inferencia_todoCeros_mantener() {
        MlpModel m = MlpModel.cargarDesdeBytes(modeloBytes, propsOk);
        double[] entrada = new double[14]; // todo ceros
        double[] q = m.inferir(entrada);
        assertThat(q).hasSize(3);
        int argmax = argmax(q);
        assertThat(argmax).isEqualTo(0); // MANTENER
    }

    @Test
    void inferencia_desvio_recalcularVigente() {
        MlpModel m = MlpModel.cargarDesdeBytes(modeloBytes, propsOk);
        double[] entrada = new double[14];
        entrada[0] = 1.0; // desvio
        double[] q = m.inferir(entrada);
        assertThat(argmax(q)).isEqualTo(1); // RECALCULAR_VIGENTE
    }

    @Test
    void inferencia_bloqueoCercano_recalcularSegura() {
        MlpModel m = MlpModel.cargarDesdeBytes(modeloBytes, propsOk);
        double[] entrada = new double[14];
        entrada[1] = 1.0; // ev_bloqueo_total
        entrada[5] = 1.0; // gravedad máxima
        entrada[6] = 0.0; // dist_evento_ruta = 0 (bloqueo en la ruta)
        double[] q = m.inferir(entrada);
        assertThat(argmax(q)).isEqualTo(2); // RECALCULAR_SEGURA
    }

    @Test
    void inferencia_q_valores_consistentesConPython() {
        // Q=[0.25,0.25,0.25] para todo ceros según el script
        MlpModel m = MlpModel.cargarDesdeBytes(modeloBytes, propsOk);
        double[] q = m.inferir(new double[14]);
        assertThat(q[0]).isCloseTo(0.25, within(1e-6));
        assertThat(q[1]).isCloseTo(0.25, within(1e-6));
        assertThat(q[2]).isCloseTo(0.25, within(1e-6));
    }

    @Test
    void inferencia_desvio_q1Dominante() {
        MlpModel m = MlpModel.cargarDesdeBytes(modeloBytes, propsOk);
        double[] entrada = new double[14];
        entrada[0] = 1.0;
        double[] q = m.inferir(entrada);
        // Q=[0.25, 2.25, 0.25] según el script
        assertThat(q[1]).isCloseTo(2.25, within(1e-6));
        assertThat(q[1]).isGreaterThan(q[0]);
        assertThat(q[1]).isGreaterThan(q[2]);
    }

    // -------------------------------------------------------------------------

    private static int argmax(double[] q) {
        int best = 0;
        for (int i = 1; i < q.length; i++) if (q[i] > q[best]) best = i;
        return best;
    }
}
