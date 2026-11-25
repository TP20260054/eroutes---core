package tp20260054.eroutes.configuracion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tp20260054.eroutes.routing.graph.GrafoProvider;

/**
 * Perfil {@code prod} (swagger apagado por defecto): actuator mínimo, swagger/OpenAPI no
 * servidos y banderas de desarrollo apagadas. Sin base de datos.
 */
@SpringBootTest(classes = PerfilProdTestApp.class)
@AutoConfigureMockMvc
@ActiveProfiles("prod")
class PerfilProdTest {

    @Autowired MockMvc mockMvc;
    @Autowired Environment env;
    @MockitoBean GrafoProvider grafoProvider;

    // ------------------------------------------------------------- actuator

    @Test
    void health_devuelveSoloElEstado() throws Exception {
        when(grafoProvider.estaPrecargado()).thenReturn(true);

        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"status\":\"UP\"}", true));
    }

    @Test
    void readiness_estaUpConElGrafoCargado_yDownSinElGrafo() throws Exception {
        when(grafoProvider.estaPrecargado()).thenReturn(true);
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"status\":\"UP\"}", true));

        when(grafoProvider.estaPrecargado()).thenReturn(false);
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().json("{\"status\":\"DOWN\"}", true));
    }

    @Test
    void liveness_noDependeDelGrafo() throws Exception {
        when(grafoProvider.estaPrecargado()).thenReturn(false);

        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"status\":\"UP\"}", true));
    }

    @Test
    void envEInfo_noExisten() throws Exception {
        // Sin sesión: la seguridad responde 401; con sesión: el endpoint no existe (404)
        for (String ruta : new String[]{"/actuator/env", "/actuator/info", "/actuator/beans", "/actuator"}) {
            mockMvc.perform(get(ruta)).andExpect(status().isUnauthorized());
            mockMvc.perform(get(ruta).with(jwt())).andExpect(status().isNotFound());
        }
    }

    // ------------------------------------------------------------- swagger

    @Test
    void swaggerYOpenApi_noSeSirven() throws Exception {
        for (String ruta : new String[]{"/swagger-ui/index.html", "/swagger-ui.html", "/v3/api-docs"}) {
            mockMvc.perform(get(ruta)).andExpect(status().isUnauthorized());
            mockMvc.perform(get(ruta).with(jwt())).andExpect(status().isNotFound());
        }
    }

    // ------------------------------------------------------------- banderas

    @Test
    void banderasDeDesarrollo_estanApagadas() {
        assertThat(env.getProperty("eroutes.routing.permitir-pesos-en-solicitud", Boolean.class)).isFalse();
        assertThat(env.getProperty("eroutes.recalculo.permitir-configuracion-en-solicitud", Boolean.class)).isFalse();
        assertThat(env.getProperty("springdoc.swagger-ui.enabled", Boolean.class)).isFalse();
        assertThat(env.getProperty("springdoc.api-docs.enabled", Boolean.class)).isFalse();
    }

    @Test
    void ajustesDeDespliegue() {
        assertThat(env.getProperty("management.endpoints.web.exposure.include")).isEqualTo("health");
        assertThat(env.getProperty("management.endpoint.health.show-details")).isEqualTo("never");
        assertThat(env.getProperty("management.endpoint.health.probes.enabled", Boolean.class)).isTrue();
        assertThat(env.getProperty("management.endpoint.health.group.readiness.include"))
                .isEqualTo("readinessState,grafo");
        assertThat(env.getProperty("logging.level.root")).isEqualTo("INFO");
        assertThat(env.getProperty("logging.level.org.springframework.security")).isEqualTo("WARN");
        assertThat(env.getProperty("server.forward-headers-strategy")).isEqualTo("framework");
        assertThat(env.getProperty("server.port")).isEqualTo("8080"); // sin PORT
        assertThat(env.getProperty("spring.devtools.restart.enabled", Boolean.class)).isFalse();
    }

    @Test
    void rutaInexistente_autenticado_devuelve404ConProblemDetail() throws Exception {
        mockMvc.perform(post("/api/v1/no-existe").with(jwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Recurso no encontrado"));
    }
}
