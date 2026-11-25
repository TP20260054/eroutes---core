package tp20260054.eroutes.configuracion;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tp20260054.eroutes.routing.graph.GrafoProvider;

/** Perfil {@code prod} con SWAGGER_ENABLED=true: Swagger y OpenAPI sí se sirven (sin sesión). */
@SpringBootTest(classes = PerfilProdTestApp.class, properties = "SWAGGER_ENABLED=true")
@AutoConfigureMockMvc
@ActiveProfiles("prod")
class PerfilProdSwaggerHabilitadoTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean GrafoProvider grafoProvider;

    @Test
    void openApi_seSirveSinSesion() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").exists());
    }

    @Test
    void swaggerUi_seSirveSinSesion() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html"));
    }
}
