package tp20260054.eroutes.eventos;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tp20260054.eroutes.mapeado.exception.RecursoNoEncontradoException;
import tp20260054.eroutes.seguridad.JwtConfig;
import tp20260054.eroutes.seguridad.JwtService;
import tp20260054.eroutes.seguridad.SecurityConfig;
import tp20260054.eroutes.seguridad.TestSecurityConfig;

@WebMvcTest(EventoVialController.class)
@Import({SecurityConfig.class, JwtConfig.class, TestSecurityConfig.class})
@TestPropertySource(properties = {
        "eroutes.seguridad.jwt-secreto=test-secret-32bytes-para-pruebas-1234",
        "eroutes.seguridad.jwt-expiracion-horas=1"
})
class EventoVialControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean EventoVialService service;
    @MockitoBean JwtService jwtService;

    private static final GeometryFactory GF = new GeometryFactory(new PrecisionModel(), 4326);

    private static EventoVial eventoFake() {
        EventoVial ev = new EventoVial();
        ev.setId(1L);
        ev.setTipo(TipoEventoVial.BLOQUEO_TOTAL);
        ev.setGravedad(1.0);
        ev.setTramoId(42L);
        ev.setGeom(GF.createPoint(new Coordinate(-77.046, -12.093)));
        ev.setEstado(EstadoEventoVial.ACTIVO);
        ev.setAutorUsuarioId(1L);
        ev.setCreadoEn(OffsetDateTime.now());
        ev.setExpiraEn(OffsetDateTime.now().plusHours(2));
        return ev;
    }

    @Test
    void sinToken_devuelve401() throws Exception {
        mockMvc.perform(post("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"punto":{"lat":-12.09,"lng":-77.04},"tipo":"BLOQUEO_TOTAL"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void crearEvento_valido_devuelve201() throws Exception {
        when(service.crear(anyLong(), any(Double.class), any(Double.class),
                eq(TipoEventoVial.BLOQUEO_TOTAL), any()))
                .thenReturn(eventoFake());

        mockMvc.perform(post("/api/v1/events")
                        .with(jwt().jwt(j -> j.subject("1")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"punto":{"lat":-12.09,"lng":-77.04},"tipo":"BLOQUEO_TOTAL","motivo":"Accidente"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.tipo").value("BLOQUEO_TOTAL"))
                .andExpect(jsonPath("$.gravedad").value(1.0));
    }

    @Test
    void crearEvento_latInvalida_devuelve400() throws Exception {
        mockMvc.perform(post("/api/v1/events")
                        .with(jwt().jwt(j -> j.subject("1")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"punto":{"lat":200.0,"lng":-77.04},"tipo":"BLOQUEO_TOTAL"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void crearEvento_tipofaltante_devuelve400() throws Exception {
        mockMvc.perform(post("/api/v1/events")
                        .with(jwt().jwt(j -> j.subject("1")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"punto":{"lat":-12.09,"lng":-77.04}}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void crearEvento_fueraDeCobertura_devuelve422() throws Exception {
        when(service.crear(anyLong(), any(Double.class), any(Double.class),
                any(), isNull()))
                .thenThrow(new EventoFueraDeCoberturaException("sin tramo cercano"));

        mockMvc.perform(post("/api/v1/events")
                        .with(jwt().jwt(j -> j.subject("1")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"punto":{"lat":-12.09,"lng":-77.04},"tipo":"BLOQUEO_TOTAL"}
                                """))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void resolver_eventoNoEncontrado_devuelve404() throws Exception {
        doThrow(new RecursoNoEncontradoException("Evento no encontrado: 99"))
                .when(service).resolver(99L);

        mockMvc.perform(post("/api/v1/events/99/resolve")
                        .with(jwt().jwt(j -> j.subject("1"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void resolver_valido_devuelve204() throws Exception {
        mockMvc.perform(post("/api/v1/events/1/resolve")
                        .with(jwt().jwt(j -> j.subject("1"))))
                .andExpect(status().isNoContent());
    }
}
