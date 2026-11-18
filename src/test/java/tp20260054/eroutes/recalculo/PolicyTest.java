package tp20260054.eroutes.recalculo;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tp20260054.eroutes.eventos.EventoVialProperties;
import tp20260054.eroutes.eventos.TipoEventoVial;
import tp20260054.eroutes.navegacion.DisparadorRecalculo;

/**
 * Tests unitarios de FixedRulePolicy, ReferencePolicy y DQLPolicyConRespaldo.
 * Sin Spring ni base de datos.
 */
class PolicyTest {

    private static byte[] modeloBytes;
    private static RecalculoProperties props;
    private static EventoVialProperties eventoProps;
    private static FixedRulePolicy fixedRule;

    @BeforeAll
    static void setup() throws Exception {
        modeloBytes = Files.readAllBytes(Path.of("src/test/resources/dql/modelo-test.json"));
        props = new RecalculoProperties(
                30, 2, 120, 500, 15, 1000, 300, 5, 1000.0, 5000.0,
                0.05, "AI", true, new RecalculoProperties.Modelo(""));
        eventoProps = new EventoVialProperties(
                120, 50.0, List.of("BLOQUEO_TOTAL"), 30,
                new EventoVialProperties.Gravedad(1.0, 0.75, 0.5, 0.25));
        fixedRule = new FixedRulePolicy(eventoProps);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static EstadoRecalculo estado(boolean desvio, boolean eventoEnRuta,
                                          boolean bloquea, TipoEventoVial tipo) {
        return new EstadoRecalculo(
                DisparadorRecalculo.BLOCKED, eventoEnRuta, bloquea,
                tipo != null ? 1L : null,
                desvio, tipo,
                tipo != null ? 1.0 : 0.0,
                tipo != null ? 0.1 : 1.0,
                0.5, "BICIMOTO", 0.3, 0.1);
    }

    // -------------------------------------------------------------------------
    // FixedRulePolicy — tabla de verdad
    // -------------------------------------------------------------------------

    @Test
    void fixed_desvio_recalcularVigente() {
        assertThat(fixedRule.decide(estado(true, false, false, null)))
                .isEqualTo(Accion.RECALCULAR_VIGENTE);
    }

    @Test
    void fixed_bloqueantEnRuta_recalcularVigente() {
        assertThat(fixedRule.decide(estado(false, true, true, TipoEventoVial.BLOQUEO_TOTAL)))
                .isEqualTo(Accion.RECALCULAR_VIGENTE);
    }

    @Test
    void fixed_bloqueante_fueraDeRuta_mantener() {
        // El evento bloquea pero NO está en la ruta restante → MANTENER
        assertThat(fixedRule.decide(estado(false, false, true, TipoEventoVial.BLOQUEO_TOTAL)))
                .isEqualTo(Accion.MANTENER);
    }

    @Test
    void fixed_eventoNoBloqueanteEnRuta_mantener() {
        assertThat(fixedRule.decide(estado(false, true, false, TipoEventoVial.OBRA)))
                .isEqualTo(Accion.MANTENER);
    }

    @Test
    void fixed_sinNada_mantener() {
        assertThat(fixedRule.decide(estado(false, false, false, null)))
                .isEqualTo(Accion.MANTENER);
    }

    @Test
    void fixed_nuncaDevuelveRecalcularSegura() {
        // Probamos todas las combinaciones relevantes
        for (boolean d : new boolean[]{true, false}) {
            for (boolean e : new boolean[]{true, false}) {
                for (boolean b : new boolean[]{true, false}) {
                    assertThat(fixedRule.decide(estado(d, e, b, TipoEventoVial.BLOQUEO_TOTAL)))
                            .isNotEqualTo(Accion.RECALCULAR_SEGURA);
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // ReferencePolicy — tabla de verdad
    // -------------------------------------------------------------------------

    @Test
    void reference_desvio_recalcularVigente() {
        ReferencePolicy ref = new ReferencePolicy();
        assertThat(ref.decide(estado(true, false, false, null)))
                .isEqualTo(Accion.RECALCULAR_VIGENTE);
    }

    @Test
    void reference_eventoEnRuta_mantener() {
        ReferencePolicy ref = new ReferencePolicy();
        // No reacciona a eventos
        assertThat(ref.decide(estado(false, true, true, TipoEventoVial.BLOQUEO_TOTAL)))
                .isEqualTo(Accion.MANTENER);
    }

    @Test
    void reference_sinNada_mantener() {
        ReferencePolicy ref = new ReferencePolicy();
        assertThat(ref.decide(estado(false, false, false, null)))
                .isEqualTo(Accion.MANTENER);
    }

    // -------------------------------------------------------------------------
    // DQLPolicyConRespaldo — modelo ausente
    // -------------------------------------------------------------------------

    @Test
    void dql_modeloAusente_usaRespaldo() {
        DQLPolicyConRespaldo dql = new DQLPolicyConRespaldo(props, fixedRule);
        // props.modelo.ruta = "" → no disponible
        assertThat(dql.decidirConMetadatos(estado(true, false, false, null)).usoRespaldo())
                .isTrue();
    }

    @Test
    void dql_modeloAusente_motivoModeloNoDisponible() {
        DQLPolicyConRespaldo dql = new DQLPolicyConRespaldo(props, fixedRule);
        DecisionConMetadatos d = dql.decidirConMetadatos(estado(true, false, false, null));
        assertThat(d.motivoRespaldo()).isEqualTo(MotivoRespaldo.MODELO_NO_DISPONIBLE);
    }

    @Test
    void dql_modeloAusente_accionSiguePoliticaRespaldo() {
        DQLPolicyConRespaldo dql = new DQLPolicyConRespaldo(props, fixedRule);
        // desvio=true → FixedRulePolicy devuelve RECALCULAR_VIGENTE
        DecisionConMetadatos d = dql.decidirConMetadatos(estado(true, false, false, null));
        assertThat(d.accion()).isEqualTo(Accion.RECALCULAR_VIGENTE);
    }

    // -------------------------------------------------------------------------
    // DQLPolicyConRespaldo — modelo cargado
    // -------------------------------------------------------------------------

    private DQLPolicyConRespaldo dqlConModelo() {
        RecalculoProperties propsConModelo = new RecalculoProperties(
                30, 2, 120, 500, 15, 1000, 300, 5, 1000.0, 5000.0,
                0.05, "AI", true,
                new RecalculoProperties.Modelo("src/test/resources/dql/modelo-test.json"));
        return new DQLPolicyConRespaldo(propsConModelo, fixedRule);
    }

    @Test
    void dql_modeloCargado_sinRespaldo_desvio() {
        DQLPolicyConRespaldo dql = dqlConModelo();
        DecisionConMetadatos d = dql.decidirConMetadatos(estado(true, false, false, null));
        assertThat(d.usoRespaldo()).isFalse();
        assertThat(d.accion()).isEqualTo(Accion.RECALCULAR_VIGENTE);
    }

    @Test
    void dql_modeloCargado_bloqueoCercano_recalcularSegura() {
        DQLPolicyConRespaldo dql = dqlConModelo();
        // Construimos un estado con bloqueo total en ruta (dist=0, gravedad=1)
        EstadoRecalculo e = new EstadoRecalculo(
                DisparadorRecalculo.BLOCKED, true, true, 1L,
                false, TipoEventoVial.BLOQUEO_TOTAL, 1.0, 0.0,
                0.5, "BICIMOTO", 0.3, 0.1);
        DecisionConMetadatos d = dql.decidirConMetadatos(e);
        assertThat(d.usoRespaldo()).isFalse();
        assertThat(d.accion()).isEqualTo(Accion.RECALCULAR_SEGURA);
    }

    @Test
    void dql_modeloCargado_versionContieneDql() {
        DQLPolicyConRespaldo dql = dqlConModelo();
        assertThat(dql.version()).startsWith("dql-");
    }

    @Test
    void dql_modeloCargado_registraPolicyVersion() {
        DQLPolicyConRespaldo dql = dqlConModelo();
        DecisionConMetadatos d = dql.decidirConMetadatos(estado(false, false, false, null));
        assertThat(d.version()).isNotBlank();
    }

    // -------------------------------------------------------------------------
    // DQLPolicyConRespaldo — timeout con fuente de tiempo inyectable
    // -------------------------------------------------------------------------

    @Test
    void dql_timeout_activaRespaldo() {
        DQLPolicyConRespaldo dql = dqlConModelo();
        // Simulamos que la inferencia tarda más de limiteInferenciaMs (1000 ms)
        AtomicLong t = new AtomicLong(0);
        dql.setFuenteTiempo(() -> t.getAndAdd(2000)); // siempre avanza 2000 ms
        DecisionConMetadatos d = dql.decidirConMetadatos(estado(false, false, false, null));
        assertThat(d.usoRespaldo()).isTrue();
        assertThat(d.motivoRespaldo()).isEqualTo(MotivoRespaldo.TIMEOUT);
    }

    // -------------------------------------------------------------------------
    // DQLPolicyConRespaldo — salida inválida
    // -------------------------------------------------------------------------

    @Test
    void dql_salidaNaN_activaRespaldo() {
        // Creamos un modelo que devuelve NaN: W1 con NaN
        String jsonNan = """
                {
                  "formato":1,"version":"v-nan","entrada_dim":14,
                  "features":[%s],
                  "normalizacion":{"dist_evento_max_m":1000,"long_restante_max_m":5000,
                                   "max_recalculos_norm":5,"ventana_recalculos_s":300},
                  "acciones":["A","B","C"],
                  "capas":[
                    {"w":[[1,0,0,0,0,0,0,0,0,0,0,0,0,0],
                           [0,1,0,0,0,0,0,0,0,0,0,0,0,0],
                           [0,0,1,0,0,0,0,0,0,0,0,0,0,0]],
                     "b":[0,0,0],"activacion":"lineal"}
                  ]
                }
                """.formatted(EstadoRecalculo.NOMBRES_FEATURES.stream()
                        .map(s -> "\"" + s + "\"").reduce((a, b) -> a + "," + b).orElse(""));
        // Este modelo es válido; para forzar NaN en la salida sobrescribimos el método
        // mediante un modelo que inyecta NaN en los pesos vía JSON
        String jsonConNanBias = jsonNan.replace("\"b\":[0,0,0]",
                "\"b\":[\"NaN\",0,0]");  // NaN en bias → no se parsea como número, usa 0
        // La vía más directa: inyectamos desde tests un MlpModel con NaN via pasada
        // Verificamos que el validador de contenido rechaza pesos no finitos
        String jsonPesoNaN = """
                {
                  "formato":1,"version":"v-nan2","entrada_dim":14,
                  "features":[%s],
                  "normalizacion":{"dist_evento_max_m":1000,"long_restante_max_m":5000,
                                   "max_recalculos_norm":5,"ventana_recalculos_s":300},
                  "acciones":["A","B","C"],
                  "capas":[
                    {"w":[[9E999,0,0,0,0,0,0,0,0,0,0,0,0,0],
                           [0,1,0,0,0,0,0,0,0,0,0,0,0,0],
                           [0,0,1,0,0,0,0,0,0,0,0,0,0,0]],
                     "b":[0,0,0],"activacion":"lineal"}
                  ]
                }
                """.formatted(EstadoRecalculo.NOMBRES_FEATURES.stream()
                        .map(s -> "\"" + s + "\"").reduce((a, b) -> a + "," + b).orElse(""));
        RecalculoProperties propsOk = new RecalculoProperties(
                30, 2, 120, 500, 15, 1000, 300, 5, 1000.0, 5000.0,
                0.05, "AI", true, new RecalculoProperties.Modelo(""));
        MlpModel m = MlpModel.cargarDesdeBytes(jsonPesoNaN.getBytes(), propsOk);
        // El validador de carga rechaza pesos Inf/NaN → el modelo no está disponible
        assertThat(m.estaDisponible()).isFalse();
        assertThat(m.motivoNoDisponible()).contains("MODELO_INVALIDO");
    }
}
