package tp20260054.eroutes.recalculo;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propiedades de configuración del módulo de recálculo.
 *
 * <p>Todos los valores numéricos son PROVISIONALES / A CALIBRAR salvo que se
 * indique lo contrario.
 */
@ConfigurationProperties(prefix = "eroutes.recalculo")
public record RecalculoProperties(
        double umbralDesvioM,
        int muestrasDesvio,
        long sesionTtlMin,
        double umbralBloqueoM,
        int cooldownS,
        long limiteInferenciaMs,
        int ventanaRecalculosS,
        int maxRecalculosNorm,
        double distEventoMaxM,
        double longRestanteMaxM,
        double epsilonMejora,
        String configuracionDefecto,
        boolean permitirConfiguracionEnSolicitud,
        Modelo modelo) {

    public record Modelo(String ruta) {}
}
