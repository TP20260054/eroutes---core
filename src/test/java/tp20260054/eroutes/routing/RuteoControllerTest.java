package tp20260054.eroutes.routing;

import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.notNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tp20260054.eroutes.mapeado.dto.Coordenada;
import tp20260054.eroutes.routing.controller.RuteoController;
import tp20260054.eroutes.routing.dto.ExtremoRutaDto;
import tp20260054.eroutes.routing.dto.MetricasRutaDto;
import tp20260054.eroutes.routing.dto.PerfilResumenDto;
import tp20260054.eroutes.routing.dto.PesosDto;
import tp20260054.eroutes.routing.dto.RutaDto;
import tp20260054.eroutes.routing.service.MismoNodoException;
import tp20260054.eroutes.routing.service.PuntoFueraDeCoberturaException;
import tp20260054.eroutes.routing.service.RuteoService;
import tp20260054.eroutes.routing.service.SinCaminoException;

@WebMvcTest(RuteoController.class)
class RuteoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RuteoService service;

    private static final String BODY_VALIDO = """
            {
              "origen":  { "lat": -12.0907, "lng": -77.0207 },
              "destino": { "lat": -12.1012, "lng": -77.0347 }
            }
            """;

    @Test
    void solicitudValidaDevuelve200() throws Exception {
        RutaDto ruta = rutaFake("MULTICRITERIO");
        Mockito.when(service.calcular(anyDouble(), anyDouble(), anyDouble(), anyDouble(),
                        isNull(), isNull()))
                .thenReturn(ruta);

        mockMvc.perform(post("/api/v1/routes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY_VALIDO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.criterio").value("MULTICRITERIO"))
                .andExpect(jsonPath("$.distanciaM").exists());
    }

    @Test
    void latInvalidaDevuelve400() throws Exception {
        String body = """
                {
                  "origen":  { "lat": 200.0, "lng": -77.0 },
                  "destino": { "lat": -12.1,  "lng": -77.03 }
                }
                """;
        mockMvc.perform(post("/api/v1/routes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void lngInvalidaDevuelve400() throws Exception {
        String body = """
                {
                  "origen":  { "lat": -12.09, "lng": -200.0 },
                  "destino": { "lat": -12.10,  "lng": -77.03 }
                }
                """;
        mockMvc.perform(post("/api/v1/routes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void puntoFueraDeCoberturaDevuelve422() throws Exception {
        Mockito.when(service.calcular(anyDouble(), anyDouble(), anyDouble(), anyDouble(),
                        isNull(), isNull()))
                .thenThrow(new PuntoFueraDeCoberturaException("fuera de cobertura"));

        mockMvc.perform(post("/api/v1/routes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY_VALIDO))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void mismoNodoDevuelve422() throws Exception {
        Mockito.when(service.calcular(anyDouble(), anyDouble(), anyDouble(), anyDouble(),
                        isNull(), isNull()))
                .thenThrow(new MismoNodoException("mismo nodo"));

        mockMvc.perform(post("/api/v1/routes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY_VALIDO))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void sinCaminoDevuelve404() throws Exception {
        Mockito.when(service.calcular(anyDouble(), anyDouble(), anyDouble(), anyDouble(),
                        isNull(), isNull()))
                .thenThrow(new SinCaminoException("sin camino"));

        mockMvc.perform(post("/api/v1/routes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY_VALIDO))
                .andExpect(status().isNotFound());
    }

    @Test
    void origenNuloDevuelve400() throws Exception {
        String body = """
                {
                  "destino": { "lat": -12.10, "lng": -77.03 }
                }
                """;
        mockMvc.perform(post("/api/v1/routes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    // ---- Tests de pesos ----

    @Test
    void pesosValidosAceptados() throws Exception {
        RutaDto ruta = rutaFake("MULTICRITERIO");
        // El controlador pasa el pesos no-nulo al servicio
        Mockito.when(service.calcular(anyDouble(), anyDouble(), anyDouble(), anyDouble(),
                        isNull(), notNull()))
                .thenReturn(ruta);

        String body = """
                {
                  "origen":  { "lat": -12.0907, "lng": -77.0207 },
                  "destino": { "lat": -12.1012, "lng": -77.0347 },
                  "pesos": { "tiempo": 2.0, "distancia": 0.5, "cruce": 10.0 }
                }
                """;
        mockMvc.perform(post("/api/v1/routes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    @Test
    void pesoNegativoDevuelve400() throws Exception {
        String body = """
                {
                  "origen":  { "lat": -12.0907, "lng": -77.0207 },
                  "destino": { "lat": -12.1012, "lng": -77.0347 },
                  "pesos": { "tiempo": -1.0 }
                }
                """;
        mockMvc.perform(post("/api/v1/routes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void pesosIgnoradosCuandoPropiedadFalse() throws Exception {
        // Con la propiedad en false el servicio recibe los pesos del DTO pero los ignora.
        // Aquí verificamos que el controlador siempre pasa el campo pesos al servicio
        // (la decisión de ignorarlos es del servicio, no del controlador).
        RutaDto ruta = rutaFake("MULTICRITERIO");
        Mockito.when(service.calcular(anyDouble(), anyDouble(), anyDouble(), anyDouble(),
                        isNull(), notNull()))
                .thenReturn(ruta);

        String body = """
                {
                  "origen":  { "lat": -12.0907, "lng": -77.0207 },
                  "destino": { "lat": -12.1012, "lng": -77.0347 },
                  "pesos": { "tiempo": 3.0 }
                }
                """;
        mockMvc.perform(post("/api/v1/routes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        // Verificar que el servicio recibió el campo pesos (no null)
        Mockito.verify(service).calcular(anyDouble(), anyDouble(), anyDouble(), anyDouble(),
                isNull(), notNull());
    }

    // ---- helper ----

    private RutaDto rutaFake(String criterio) {
        Coordenada c = new Coordenada(-12.09, -77.02);
        ExtremoRutaDto extremo = new ExtremoRutaDto(c, c, 5);
        MetricasRutaDto metricas = new MetricasRutaDto(0.3, 0, 0, 0, 10.0);
        PerfilResumenDto perfil = new PerfilResumenDto("Aima Kumai", "BICIMOTO", 45);
        return new RutaDto(criterio, perfil, extremo, extremo,
                1200.0, 180.0, metricas, List.of(c), List.of());
    }
}
