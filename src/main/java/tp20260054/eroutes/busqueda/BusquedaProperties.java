package tp20260054.eroutes.busqueda;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propiedades de configuración del módulo de búsqueda.
 */
@ConfigurationProperties(prefix = "eroutes.busqueda")
public record BusquedaProperties(
        int limitePorDefecto,
        int limiteMaximo,
        int radioCalleM,
        int radioLugarM) {
}
