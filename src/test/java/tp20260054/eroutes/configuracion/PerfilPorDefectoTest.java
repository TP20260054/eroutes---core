package tp20260054.eroutes.configuracion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.env.Environment;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tp20260054.eroutes.routing.graph.GrafoProvider;

/** Sin el perfil {@code prod} el comportamiento de desarrollo no cambia. Sin base de datos. */
@SpringBootTest(classes = PerfilProdTestApp.class)
@AutoConfigureMockMvc
class PerfilPorDefectoTest {

    @Autowired MockMvc mockMvc;
    @Autowired Environment env;
    @MockitoBean GrafoProvider grafoProvider;

    @Test
    void swaggerYOpenApi_siguenPublicos() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
        mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }

    @Test
    void banderasDeDesarrollo_siguenActivas() {
        assertThat(env.getProperty("eroutes.routing.permitir-pesos-en-solicitud", Boolean.class)).isTrue();
        assertThat(env.getProperty("eroutes.recalculo.permitir-configuracion-en-solicitud", Boolean.class)).isTrue();
    }

    @Test
    void health_siguiendoComoAntes_muestraComponentes_yElGrafoNoRompeLaAplicacion() throws Exception {
        when(grafoProvider.estaPrecargado()).thenReturn(true);

        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components.grafo.status").value("UP"));
    }
}
