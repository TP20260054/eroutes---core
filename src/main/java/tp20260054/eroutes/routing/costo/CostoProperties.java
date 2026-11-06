package tp20260054.eroutes.routing.costo;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "eroutes.routing")
public record CostoProperties(
        Pesos pesos,
        VelDefecto velDefecto,
        Exposicion exposicion) {

    public record Pesos(
            double tiempo,
            double distancia,
            double exposicion,
            double noValidado,
            double cruce) {}

    public record VelDefecto(
            int primary,
            int secondary,
            int tertiary,
            int unclassified,
            int residential,
            int livingStreet,
            int service,
            int otros,
            int minima) {}

    public record Exposicion(
            double primary,
            double secondary,
            double tertiary,
            double unclassified,
            double residential,
            double livingStreet,
            double service,
            double otros) {}
}
