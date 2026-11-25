package tp20260054.eroutes.configuracion;

import java.util.Set;
import org.springframework.boot.actuate.endpoint.web.WebServerNamespace;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.health.actuate.endpoint.AdditionalHealthEndpointPath;
import org.springframework.boot.health.actuate.endpoint.HealthEndpointGroup;
import org.springframework.boot.health.actuate.endpoint.HealthEndpointGroups;
import org.springframework.boot.health.actuate.endpoint.HealthEndpointGroupsPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

/**
 * Perfil {@code prod}: {@code GET /actuator/health} responde únicamente {@code {"status":"UP"}}.
 *
 * <p>Con los probes habilitados, Spring Boot añade al cuerpo la lista de grupos
 * ({@code "groups":["liveness","readiness"]}). Este post-procesador la oculta; los grupos
 * siguen existiendo y se consultan por su ruta ({@code /actuator/health/readiness}).
 *
 * <p>Es una auto-configuración (ver {@code META-INF/spring/...AutoConfiguration.imports}) declarada
 * {@code after} la de los probes: ambos post-procesadores tienen el mismo {@code @Order}
 * y el que se registra último envuelve al otro, que es lo que se necesita aquí.
 */
@Profile("prod")
@AutoConfiguration(afterName = {
        "org.springframework.boot.health.autoconfigure.actuate.endpoint.AvailabilityProbesAutoConfiguration",
        "org.springframework.boot.health.autoconfigure.actuate.endpoint.HealthEndpointAutoConfiguration"})
public class ActuatorProdConfig {

    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE)
    HealthEndpointGroupsPostProcessor ocultarNombresDeGrupos() {
        return OcultaNombresDeGrupos::new;
    }

    /** Igual que el original, salvo que no lista los nombres de los grupos. */
    private record OcultaNombresDeGrupos(HealthEndpointGroups delegado) implements HealthEndpointGroups {

        @Override
        public HealthEndpointGroup getPrimary() {
            return delegado.getPrimary();
        }

        @Override
        public Set<String> getNames() {
            return Set.of();
        }

        @Override
        public HealthEndpointGroup get(String name) {
            return delegado.get(name);
        }

        @Override
        public HealthEndpointGroup get(AdditionalHealthEndpointPath path) {
            return delegado.get(path);
        }

        @Override
        public Set<HealthEndpointGroup> getAllWithAdditionalPath(WebServerNamespace namespace) {
            return delegado.getAllWithAdditionalPath(namespace);
        }
    }
}
