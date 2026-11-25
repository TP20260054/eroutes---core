package tp20260054.eroutes.routing.graph;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Indicador de salud {@code grafo}: UP cuando el grafo precargado al arrancar ya está en
 * memoria. Forma parte del grupo {@code readiness}, de modo que la plataforma no enrute
 * tráfico a la instancia hasta que pueda calcular rutas.
 */
@Component("grafo")
public class GrafoHealthIndicator implements HealthIndicator {

    private final GrafoProvider grafoProvider;

    public GrafoHealthIndicator(GrafoProvider grafoProvider) {
        this.grafoProvider = grafoProvider;
    }

    @Override
    public Health health() {
        if (grafoProvider.estaPrecargado()) {
            return Health.up().build();
        }
        return Health.down().withDetail("motivo", "grafo no cargado en memoria").build();
    }
}
