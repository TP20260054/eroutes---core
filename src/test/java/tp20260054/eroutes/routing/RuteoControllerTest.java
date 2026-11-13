package tp20260054.eroutes.routing;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.notNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tp20260054.eroutes.mapeado.dto.Coordenada;
import tp20260054.eroutes.routing.controller.RuteoController;
import tp20260054.eroutes.routing.dto.ExtremoRutaDto;
import tp20260054.eroutes.routing.dto.MetricasRutaDto;
import tp20260054.eroutes.routing.dto.PerfilResumenDto;
import tp20260054.eroutes.routing.dto.RutaDto;
import tp20260054.eroutes.routing.service.MismoNodoException;
import tp20260054.eroutes.routing.service.PuntoFueraDeCoberturaException;
import tp20260054.eroutes.routing.service.RuteoService;
import tp20260054.eroutes.routing.service.SinCaminoException;
import tp20260054.eroutes.seguridad.JwtConfig;
import tp20260054.eroutes.seguridad.JwtService;
import tp20260054.eroutes.seguridad.SecurityConfig;
import tp20260054.eroutes.seguridad.TestSecurityConfig;

@WebMvcTest(RuteoController.class)
@Import({SecurityConfig.class, JwtConfig.class, TestSecurityConfig.class})
@TestPropertySource(properties = {
        "eroutes.seguridad.jwt-secreto=test-secret-32bytes-para-pruebas-1234",
        "eroutes.seguridad.jwt-expiracion-horas=1"
})
class RuteoControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean RuteoService service;
    @MockitoBean JwtService jwtService;

    private static final String BODY = """
            {"origen":{"lat":-12.0907,"lng":-77.0207},"destino":{"lat":-12.1012,"lng":-77.0347}}
            """;

    @Test
    void sinTokenDevuelve401() throws Exception {
        mockMvc.perform(post("/api/v1/routes")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void solicitudValidaDevuelve200() throws Exception {
        Mockito.when(service.calcular(anyLong(), anyDouble(), anyDouble(),
                        anyDouble(), anyDouble(), isNull(), isNull(), isNull()))
                .thenReturn(rutaFake("MULTICRITERIO"));

        mockMvc.perform(post("/api/v1/routes").with(jwt().jwt(j -> j.subject("1")))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.criterio").value("MULTICRITERIO"));
    }

    @Test
    void latInvalidaDevuelve400() throws Exception {
        String body = """
                {"origen":{"lat":200.0,"lng":-77.0},"destino":{"lat":-12.1,"lng":-77.03}}
                """;
        mockMvc.perform(post("/api/v1/routes").with(jwt().jwt(j -> j.subject("1")))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void puntoFueraDeCoberturaDevuelve422() throws Exception {
        Mockito.when(service.calcular(anyLong(), anyDouble(), anyDouble(),
                        anyDouble(), anyDouble(), isNull(), isNull(), isNull()))
                .thenThrow(new PuntoFueraDeCoberturaException("fuera"));

        mockMvc.perform(post("/api/v1/routes").with(jwt().jwt(j -> j.subject("1")))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void mismoNodoDevuelve422() throws Exception {
        Mockito.when(service.calcular(anyLong(), anyDouble(), anyDouble(),
                        anyDouble(), anyDouble(), isNull(), isNull(), isNull()))
                .thenThrow(new MismoNodoException("mismo"));

        mockMvc.perform(post("/api/v1/routes").with(jwt().jwt(j -> j.subject("1")))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void sinCaminoDevuelve404() throws Exception {
        Mockito.when(service.calcular(anyLong(), anyDouble(), anyDouble(),
                        anyDouble(), anyDouble(), isNull(), isNull(), isNull()))
                .thenThrow(new SinCaminoException("sin camino"));

        mockMvc.perform(post("/api/v1/routes").with(jwt().jwt(j -> j.subject("1")))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isNotFound());
    }

    @Test
    void pesosNegativosDevuelve400() throws Exception {
        String body = """
                {"origen":{"lat":-12.09,"lng":-77.02},"destino":{"lat":-12.10,"lng":-77.03},
                 "pesos":{"tiempo":-1.0}}
                """;
        mockMvc.perform(post("/api/v1/routes").with(jwt().jwt(j -> j.subject("1")))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void pesosValidosAceptados() throws Exception {
        Mockito.when(service.calcular(anyLong(), anyDouble(), anyDouble(),
                        anyDouble(), anyDouble(), isNull(), notNull(), isNull()))
                .thenReturn(rutaFake("MULTICRITERIO"));

        String body = """
                {"origen":{"lat":-12.09,"lng":-77.02},"destino":{"lat":-12.10,"lng":-77.03},
                 "pesos":{"tiempo":2.0,"distancia":0.5}}
                """;
        mockMvc.perform(post("/api/v1/routes").with(jwt().jwt(j -> j.subject("1")))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
    }

    private RutaDto rutaFake(String criterio) {
        Coordenada c = new Coordenada(-12.09, -77.02);
        return new RutaDto(criterio,
                new PerfilResumenDto("Aima Kumai", "BICIMOTO", 45),
                new ExtremoRutaDto(c, c, 5), new ExtremoRutaDto(c, c, 5),
                1200.0, 180.0,
                new MetricasRutaDto(0.3, 0, 0, 0, 10.0),
                List.of(c), List.of());
    }
}
