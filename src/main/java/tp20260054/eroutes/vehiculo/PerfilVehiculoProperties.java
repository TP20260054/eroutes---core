package tp20260054.eroutes.vehiculo;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "eroutes.vehiculo")
public record PerfilVehiculoProperties(
        String modelo,
        String tipo,
        int velocidadMaximaKmh) {
}
