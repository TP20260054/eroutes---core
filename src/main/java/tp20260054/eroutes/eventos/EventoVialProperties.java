package tp20260054.eroutes.eventos;

import java.util.List;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuración de eventos viales.
 *
 * <p>{@code tiposQueBloquean}: tipos cuyo costo en {@link tp20260054.eroutes.routing.costo.CostoPreset}
 * es {@link Double#POSITIVE_INFINITY}. Los demás solo alimentan el vector de estado del DQL.
 */
@ConfigurationProperties(prefix = "eroutes.eventos")
public record EventoVialProperties(
        int ttlBloqueoMin,
        double maxDistanciaTramoM,
        List<String> tiposQueBloquean,
        int intervaloExpiracionS,
        Gravedad gravedad) {

    public Set<String> tiposQueBloqueanSet() {
        return tiposQueBloquean == null ? Set.of() : Set.copyOf(tiposQueBloquean);
    }

    public double gravedadDe(TipoEventoVial tipo) {
        return switch (tipo) {
            case BLOQUEO_TOTAL         -> gravedad.bloqueoTotal();
            case OBRA                  -> gravedad.obra();
            case BACHES_SEVEROS        -> gravedad.bachesSeveros();
            case VEHICULO_EN_CICLOVIA  -> gravedad.vehiculoEnCiclovia();
        };
    }

    public record Gravedad(
            double bloqueoTotal,
            double obra,
            double bachesSeveros,
            double vehiculoEnCiclovia) {}
}
