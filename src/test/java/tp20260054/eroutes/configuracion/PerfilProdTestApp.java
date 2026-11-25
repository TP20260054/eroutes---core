package tp20260054.eroutes.configuracion;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import tp20260054.eroutes.mapeado.exception.ApiExceptionHandler;
import tp20260054.eroutes.routing.graph.GrafoHealthIndicator;
import tp20260054.eroutes.seguridad.SecurityConfig;
import tp20260054.eroutes.seguridad.TestSecurityConfig;

/**
 * Aplicación mínima para probar el perfil {@code prod} sin base de datos: autoconfiguración
 * completa (web, seguridad, actuator, springdoc) salvo JDBC/JPA/Flyway, con la seguridad real
 * ({@link SecurityConfig}), el manejador de errores y el indicador de salud del grafo.
 * {@code ActuatorProdConfig} entra por auto-configuración, igual que en la aplicación real.
 * Quien la use debe aportar un {@code GrafoProvider} (normalmente un @MockitoBean).
 */
// @TestComponent: los otros tests no la recogen al escanear; @Configuration (y no @TestConfiguration):
// con @SpringBootTest(classes = ...) evita que se cargue también EroutesApplication.
@TestComponent
@Configuration
@EnableAutoConfiguration(excludeName = {
        "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
        "org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration",
        "org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration",
        "org.springframework.boot.jdbc.autoconfigure.JdbcClientAutoConfiguration",
        "org.springframework.boot.jdbc.autoconfigure.health.DataSourceHealthContributorAutoConfiguration",
        "org.springframework.boot.jdbc.autoconfigure.metrics.DataSourcePoolMetricsAutoConfiguration",
        "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration",
        "org.springframework.boot.hibernate.autoconfigure.metrics.HibernateMetricsAutoConfiguration",
        "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration",
        "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration",
        "org.springframework.boot.flyway.autoconfigure.FlywayEndpointAutoConfiguration"
})
@Import({SecurityConfig.class, TestSecurityConfig.class, ApiExceptionHandler.class,
        GrafoHealthIndicator.class})
public class PerfilProdTestApp {
}
