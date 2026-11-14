package tp20260054.eroutes.seguridad;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "eroutes.seguridad")
public record SeguridadProperties(
        String jwtSecreto,
        int jwtExpiracionHoras) {
}
