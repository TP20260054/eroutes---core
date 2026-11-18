package tp20260054.eroutes.recalculo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Política DQL con respaldo automático a {@link FixedRulePolicy} (OB2 3.5).
 *
 * <p>Activa el respaldo cuando:
 * <ul>
 *   <li>El modelo no está cargado/es inválido ({@link MotivoRespaldo#MODELO_NO_DISPONIBLE})
 *   <li>La inferencia lanza excepción ({@link MotivoRespaldo#EXCEPCION})
 *   <li>La salida contiene NaN o ∞ ({@link MotivoRespaldo#SALIDA_INVALIDA})
 *   <li>La acción elegida está fuera de {0,1,2} ({@link MotivoRespaldo#SALIDA_INVALIDA})
 *   <li>La inferencia supera {@code limite-inferencia-ms} ({@link MotivoRespaldo#TIMEOUT})
 * </ul>
 *
 * <p>El arranque no falla si el modelo no está: solo se registra WARN y todas
 * las decisiones usan respaldo (RNF-07).
 *
 * <p>Implementa {@link RecalculationPolicy} (delegando a {@link #decidirConMetadatos})
 * para poder ser inyectada donde se necesite solo la acción.
 * Usa {@link FuenteTiempo} inyectable para que los tests puedan simular timeout.
 */
@Component
public class DQLPolicyConRespaldo implements RecalculationPolicy {

    private static final Logger log = LoggerFactory.getLogger(DQLPolicyConRespaldo.class);

    private final MlpModel modelo;
    private final DQLPolicy dqlPolicy;      // null si el modelo no está disponible
    private final FixedRulePolicy respaldo;
    private final long limiteInferenciaMs;
    private FuenteTiempo fuenteTiempo;

    public DQLPolicyConRespaldo(RecalculoProperties props,
                                FixedRulePolicy respaldo) {
        this.respaldo = respaldo;
        this.limiteInferenciaMs = props.limiteInferenciaMs();
        this.fuenteTiempo = FuenteTiempo.sistematico();

        this.modelo = MlpModel.cargar(
                props.modelo() != null ? props.modelo().ruta() : null, props);

        if (modelo.estaDisponible()) {
            this.dqlPolicy = new DQLPolicy(modelo);
            log.info("[DQL] Modelo cargado: versión '{}'", modelo.version());
        } else {
            this.dqlPolicy = null;
            log.warn("[DQL] Modelo no disponible: {}. Todas las decisiones usarán respaldo.",
                    modelo.motivoNoDisponible());
        }
    }

    /** Permite sustituir la fuente de tiempo en tests. */
    public void setFuenteTiempo(FuenteTiempo ft) { this.fuenteTiempo = ft; }

    // -------------------------------------------------------------------------
    // RecalculationPolicy (delega a decidirConMetadatos)
    // -------------------------------------------------------------------------

    @Override
    public Accion decide(EstadoRecalculo estado) {
        return decidirConMetadatos(estado).accion();
    }

    @Override
    public String version() {
        return dqlPolicy != null ? dqlPolicy.version() : respaldo.version();
    }

    // -------------------------------------------------------------------------
    // API extendida con metadatos (usada por RecalculoService)
    // -------------------------------------------------------------------------

    public DecisionConMetadatos decidirConMetadatos(EstadoRecalculo estado) {
        if (dqlPolicy == null) {
            Accion accion = respaldo.decide(estado);
            return new DecisionConMetadatos(accion, true,
                    MotivoRespaldo.MODELO_NO_DISPONIBLE, respaldo.version(), 0L);
        }

        long t0 = fuenteTiempo.ahoraMs();
        try {
            double[] q = modelo.inferir(estado.vector());
            long elapsed = fuenteTiempo.ahoraMs() - t0;

            if (elapsed > limiteInferenciaMs) {
                return usarRespaldo(estado, MotivoRespaldo.TIMEOUT, elapsed,
                        "inferencia tardó " + elapsed + " ms");
            }

            // Validar salida
            int mejor = -1;
            for (int i = 0; i < q.length; i++) {
                if (!Double.isFinite(q[i])) {
                    return usarRespaldo(estado, MotivoRespaldo.SALIDA_INVALIDA, elapsed,
                            "salida no finita en índice " + i);
                }
                if (mejor < 0 || q[i] > q[mejor]) mejor = i;
            }
            if (mejor < 0 || mejor > 2) {
                return usarRespaldo(estado, MotivoRespaldo.SALIDA_INVALIDA, elapsed,
                        "acción fuera de rango: " + mejor);
            }

            Accion accion = Accion.deCodigo(mejor);
            return new DecisionConMetadatos(accion, false, null, dqlPolicy.version(), elapsed);

        } catch (Exception e) {
            long elapsed = fuenteTiempo.ahoraMs() - t0;
            log.warn("[DQL] Excepción durante la inferencia: {}", e.getMessage());
            return usarRespaldo(estado, MotivoRespaldo.EXCEPCION, elapsed, e.getMessage());
        }
    }

    // -------------------------------------------------------------------------

    private DecisionConMetadatos usarRespaldo(EstadoRecalculo estado,
                                              MotivoRespaldo motivo, long elapsed,
                                              String detalle) {
        log.warn("[DQL] Respaldo activado por {}: {}", motivo, detalle);
        Accion accion = respaldo.decide(estado);
        return new DecisionConMetadatos(accion, true, motivo,
                respaldo.version(), elapsed);
    }
}
