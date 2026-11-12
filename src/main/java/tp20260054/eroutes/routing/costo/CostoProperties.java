package tp20260054.eroutes.routing.costo;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "eroutes.routing")
public record CostoProperties(
        Pesos pesos,
        VelDefecto velDefecto,
        Exposicion exposicion,
        double factorVelocidadEfectiva,
        DemoraNode demoraNodo) {

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

    /** Demoras en segundos al llegar a un nodo según su control. # PROVISIONAL */
    public record DemoraNode(
            double trafficSignals,
            double stop,
            double crossing,
            double sinControl) {}
}
