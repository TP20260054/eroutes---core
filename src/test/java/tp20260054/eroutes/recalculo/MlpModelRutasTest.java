package tp20260054.eroutes.recalculo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import tp20260054.eroutes.navegacion.DisparadorRecalculo;

/**
 * Carga del modelo DQL desde una ruta de archivo o desde el classpath
 * ({@code classpath:...}, para empaquetarlo dentro del jar). Sin Spring ni base de datos.
 */
class MlpModelRutasTest {

    private static final Path ARCHIVO = Path.of("src/test/resources/dql/modelo-test.json").toAbsolutePath();

    private static RecalculoProperties props(String ruta) {
        return new RecalculoProperties(30, 2, 120, 500, 15, 1000, 300, 5, 1000.0, 5000.0,
                0.05, "AI", true, new RecalculoProperties.Modelo(ruta));
    }

    private static EstadoRecalculo estado() {
        return new EstadoRecalculo(DisparadorRecalculo.REROUTE, false, false, null,
                true, null, 0.0, 1.0, 0.5, "BICIMOTO", 0.3, 0.0);
    }

    // ------------------------------------------------------------- carga

    @Test
    void classpath_recursoExistente_cargaElModelo() {
        MlpModel m = MlpModel.cargar("classpath:dql/modelo-test.json", props(null));

        assertThat(m.estaDisponible()).isTrue();
        assertThat(m.version()).isEqualTo("dql-test-manual");
    }

    @Test
    void classpath_conBarraInicial_tambienCarga() {
        assertThat(MlpModel.cargar("classpath:/dql/modelo-test.json", props(null)).estaDisponible()).isTrue();
    }

    @Test
    void classpath_recursoInexistente_noDisponible_conMotivoClaro() {
        MlpModel m = MlpModel.cargar("classpath:dql/no-existe.json", props(null));

        assertThat(m.estaDisponible()).isFalse();
        assertThat(m.motivoNoDisponible()).contains("MODELO_NO_DISPONIBLE")
                .contains("classpath").contains("dql/no-existe.json");
    }

    @Test
    void file_rutaAbsolutaConPrefijo_carga() {
        assertThat(MlpModel.cargar("file:" + ARCHIVO, props(null)).estaDisponible()).isTrue();
        assertThat(MlpModel.cargar(ARCHIVO.toUri().toString(), props(null)).estaDisponible()).isTrue();
    }

    @Test
    void file_rutaRelativaConPrefijo_carga() {
        assertThat(MlpModel.cargar("file:src/test/resources/dql/modelo-test.json", props(null))
                .estaDisponible()).isTrue();
    }

    @Test
    void file_inexistente_noDisponible() {
        MlpModel m = MlpModel.cargar("file:/ruta/que/no/existe/modelo.json", props(null));

        assertThat(m.estaDisponible()).isFalse();
        assertThat(m.motivoNoDisponible()).contains("archivo no encontrado");
    }

    @Test
    void rutaSimpleDeArchivo_sigueFuncionando() {
        assertThat(MlpModel.cargar(ARCHIVO.toString(), props(null)).estaDisponible()).isTrue();
    }

    @Test
    void rutaVaciaONula_noDisponible() {
        assertThat(MlpModel.cargar(null, props(null)).estaDisponible()).isFalse();
        assertThat(MlpModel.cargar("  ", props(null)).estaDisponible()).isFalse();
    }

    // ------------------------------------------------------------- respaldo y log

    @Test
    void modeloInexistente_usaRespaldo_yAvisaConUnSoloWarn() {
        Logger logger = (Logger) LoggerFactory.getLogger(DQLPolicyConRespaldo.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            FixedRulePolicy respaldo = mock(FixedRulePolicy.class);
            when(respaldo.decide(any())).thenReturn(Accion.RECALCULAR_VIGENTE);
            when(respaldo.version()).thenReturn("RULE-1.0");

            DQLPolicyConRespaldo politica = new DQLPolicyConRespaldo(
                    props("classpath:dql/no-existe.json"), respaldo);
            DecisionConMetadatos d1 = politica.decidirConMetadatos(estado());
            DecisionConMetadatos d2 = politica.decidirConMetadatos(estado());
            DecisionConMetadatos d3 = politica.decidirConMetadatos(estado());

            for (DecisionConMetadatos d : List.of(d1, d2, d3)) {
                assertThat(d.usoRespaldo()).isTrue();
                assertThat(d.motivoRespaldo()).isEqualTo(MotivoRespaldo.MODELO_NO_DISPONIBLE);
                assertThat(d.accion()).isEqualTo(Accion.RECALCULAR_VIGENTE);
                assertThat(d.version()).isEqualTo("RULE-1.0");
            }
            List<ILoggingEvent> warns = appender.list.stream()
                    .filter(e -> e.getLevel() == Level.WARN).toList();
            assertThat(warns).hasSize(1);
            assertThat(warns.get(0).getFormattedMessage())
                    .contains("classpath:dql/no-existe.json").contains("respaldo");
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    void modeloEnClasspath_noUsaRespaldo_yNoAvisa() {
        Logger logger = (Logger) LoggerFactory.getLogger(DQLPolicyConRespaldo.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            DQLPolicyConRespaldo politica = new DQLPolicyConRespaldo(
                    props("classpath:dql/modelo-test.json"), mock(FixedRulePolicy.class));

            DecisionConMetadatos d = politica.decidirConMetadatos(estado());

            assertThat(d.usoRespaldo()).isFalse();
            assertThat(d.accion()).isEqualTo(Accion.RECALCULAR_VIGENTE); // modelo de prueba: desvío → Q1
            assertThat(politica.version()).isEqualTo("dql-dql-test-manual");
            assertThat(appender.list.stream().filter(e -> e.getLevel() == Level.WARN)).isEmpty();
        } finally {
            logger.detachAppender(appender);
        }
    }
}
