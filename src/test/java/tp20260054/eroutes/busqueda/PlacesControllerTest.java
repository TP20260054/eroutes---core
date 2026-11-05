package tp20260054.eroutes.busqueda;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tp20260054.eroutes.busqueda.dto.ResultadoBusquedaDto;
import tp20260054.eroutes.busqueda.dto.UbicacionInfoDto;
import tp20260054.eroutes.mapeado.dto.Coordenada;

@WebMvcTest(PlacesController.class)
@EnableConfigurationProperties(BusquedaProperties.class)
@TestPropertySource(properties = {
        "eroutes.busqueda.limite-por-defecto=8",
        "eroutes.busqueda.limite-maximo=20",
        "eroutes.busqueda.radio-calle-m=60",
        "eroutes.busqueda.radio-lugar-m=40"
})
class PlacesControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BusquedaService service;

    // ---- search ----

    @Test
    void searchValidoDevuelve200() throws Exception {
        ResultadoBusquedaDto resultado = new ResultadoBusquedaDto(
                "CALLE", null, "Av Javier Prado", null, null,
                new Coordenada(-12.09, -77.05), null);

        Mockito.when(service.buscar(anyString(), anyString(), isNull(), isNull(), anyInt()))
                .thenReturn(List.of(resultado));

        mockMvc.perform(get("/api/v1/places/search").param("q", "javier prado"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tipo").value("CALLE"))
                .andExpect(jsonPath("$[0].nombre").value("Av Javier Prado"));
    }

    @Test
    void searchQCortoDevuelve400() throws Exception {
        // "a" normaliza a "a", longitud 1 < 2
        mockMvc.perform(get("/api/v1/places/search").param("q", "a"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void searchSoloLatDevuelve400() throws Exception {
        // lat sin lng
        mockMvc.perform(get("/api/v1/places/search")
                        .param("q", "javier prado")
                        .param("lat", "-12.09"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void searchLatFueraDeRangoDevuelve400() throws Exception {
        mockMvc.perform(get("/api/v1/places/search")
                        .param("q", "javier prado")
                        .param("lat", "200.0")
                        .param("lng", "-77.0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void searchLimitePorEncimaNormaliza() throws Exception {
        // limit=50 > máximo=20: no es error, se clampea al máximo
        Mockito.when(service.buscar(anyString(), anyString(), isNull(), isNull(), anyInt()))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/places/search")
                        .param("q", "parque")
                        .param("limit", "50"))
                .andExpect(status().isOk());
    }

    // ---- reverse ----

    @Test
    void reverseValidoDevuelve200() throws Exception {
        UbicacionInfoDto dto = new UbicacionInfoDto(true, null, null);
        Mockito.when(service.geocodificacionInversa(anyDouble(), anyDouble())).thenReturn(dto);

        mockMvc.perform(get("/api/v1/places/reverse")
                        .param("lat", "-12.09")
                        .param("lng", "-77.05"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dentroDeCobertura").value(true));
    }

    @Test
    void reverseLatFueraDeRangoDevuelve400() throws Exception {
        mockMvc.perform(get("/api/v1/places/reverse")
                        .param("lat", "-95.0")
                        .param("lng", "-77.0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reverseLngFueraDeRangoDevuelve400() throws Exception {
        mockMvc.perform(get("/api/v1/places/reverse")
                        .param("lat", "-12.09")
                        .param("lng", "200.0"))
                .andExpect(status().isBadRequest());
    }
}
