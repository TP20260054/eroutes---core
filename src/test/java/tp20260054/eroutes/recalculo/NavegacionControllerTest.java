package tp20260054.eroutes.recalculo;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tp20260054.eroutes.mapeado.dto.Coordenada;
import tp20260054.eroutes.navegacion.ConfiguracionNavegacion;
import tp20260054.eroutes.navegacion.DisparadorRecalculo;
import tp20260054.eroutes.recalculo.dto.NavegacionIniciadaDto;
import tp20260054.eroutes.recalculo.dto.RecalculoDetalleDto;
import tp20260054.eroutes.recalculo.dto.RecalculoManualDto;
import tp20260054.eroutes.recalculo.dto.RespuestaPosicionDto;
import tp20260054.eroutes.routing.costo.Preset;
import tp20260054.eroutes.routing.dto.ExtremoRutaDto;
import tp20260054.eroutes.routing.dto.MetricasRutaDto;
import tp20260054.eroutes.routing.dto.PerfilResumenDto;
import tp20260054.eroutes.routing.dto.RutaDto;
import tp20260054.eroutes.seguridad.JwtConfig;
import tp20260054.eroutes.seguridad.JwtService;
import tp20260054.eroutes.seguridad.SecurityConfig;
import tp20260054.eroutes.seguridad.TestSecurityConfig;

@WebMvcTest(NavegacionController.class)
@Import({SecurityConfig.class, JwtConfig.class, TestSecurityConfig.class})
@TestPropertySource(properties = {
        "eroutes.seguridad.jwt-secreto=test-secret-32bytes-para-pruebas-1234",
        "eroutes.seguridad.jwt-expiracion-horas=1",
        "eroutes.recalculo.umbral-desvio-m=30",
        "eroutes.recalculo.muestras-desvio=2",
        "eroutes.recalculo.sesion-ttl-min=120",
        "eroutes.recalculo.umbral-bloqueo-m=500",
        "eroutes.recalculo.cooldown-s=15",
        "eroutes.recalculo.limite-inferencia-ms=1000",
        "eroutes.recalculo.ventana-recalculos-s=300",
        "eroutes.recalculo.max-recalculos-norm=5",
        "eroutes.recalculo.dist-evento-max-m=1000",
        "eroutes.recalculo.long-restante-max-m=5000",
        "eroutes.recalculo.epsilon-mejora=0.05",
        "eroutes.recalculo.configuracion-defecto=AI",
        "eroutes.recalculo.permitir-configuracion-en-solicitud=true",
        "eroutes.recalculo.modelo.ruta="
})
class NavegacionControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean NavegacionService navService;
    @MockitoBean RecalculoService recalculoService;
    @MockitoBean RecalculoProperties props;
    @MockitoBean JwtService jwtService;

    // =========================================================================
    // Helpers
    // =========================================================================

    private static RutaDto rutaFake() {
        Coordenada c = new Coordenada(-12.09, -77.02);
        return new RutaDto("RAPIDA",
                new PerfilResumenDto("M", "BICIMOTO", 45),
                new ExtremoRutaDto(c, c, 0), new ExtremoRutaDto(c, c, 0),
                1200.0, 180.0,
                new MetricasRutaDto(0.3, 0, 0, 0, 5.0),
                List.of(c), List.of());
    }

    private static final String BODY_INICIAR = """
            {
              "origen":  {"lat": -12.0907, "lng": -77.0207},
              "destino": {"lat": -12.1012, "lng": -77.0347}
            }
            """;

    private static final String BODY_POSICION = """
            {"lat": -12.0910, "lng": -77.0210, "velocidadKmh": 20.0}
            """;

    // =========================================================================
    // POST /api/v1/navigations
    // =========================================================================

    @Test
    void iniciar_sinToken_401() throws Exception {
        mockMvc.perform(post("/api/v1/navigations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY_INICIAR))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void iniciar_valido_201() throws Exception {
        when(props.permitirConfiguracionEnSolicitud()).thenReturn(true);
        when(navService.iniciar(anyLong(), anyDouble(), anyDouble(),
                anyDouble(), anyDouble(), isNull(), isNull(), isNull()))
                .thenReturn(new NavegacionIniciadaDto(
                        42L, rutaFake(), Preset.RAPIDA,
                        JsonNodeFactory.instance.objectNode(),
                        ConfiguracionNavegacion.AI));

        mockMvc.perform(post("/api/v1/navigations")
                        .with(jwt().jwt(j -> j.subject("1")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY_INICIAR))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.navegacionId").value(42))
                .andExpect(jsonPath("$.preset").value("RAPIDA"))
                .andExpect(jsonPath("$.configuracion").value("AI"))
                .andExpect(jsonPath("$.ruta.distanciaM").value(1200.0));
    }

    @Test
    void iniciar_latInvalida_400() throws Exception {
        String body = """
                {"origen": {"lat": 999, "lng": -77.02},
                 "destino": {"lat": -12.10, "lng": -77.03}}
                """;
        mockMvc.perform(post("/api/v1/navigations")
                        .with(jwt().jwt(j -> j.subject("1")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void iniciar_origenFaltante_400() throws Exception {
        mockMvc.perform(post("/api/v1/navigations")
                        .with(jwt().jwt(j -> j.subject("1")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"destino\": {\"lat\": -12.10, \"lng\": -77.03}}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void iniciar_sinRutaDisponible_lanzaSinCamino_404() throws Exception {
        when(props.permitirConfiguracionEnSolicitud()).thenReturn(false);
        when(navService.iniciar(anyLong(), anyDouble(), anyDouble(),
                anyDouble(), anyDouble(), isNull(), isNull(), isNull()))
                .thenThrow(new tp20260054.eroutes.routing.service.SinCaminoException(
                        "Sin camino disponible"));

        mockMvc.perform(post("/api/v1/navigations")
                        .with(jwt().jwt(j -> j.subject("1")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY_INICIAR))
                .andExpect(status().isNotFound());
    }

    @Test
    void iniciar_configFlag_deshabilitado_400() throws Exception {
        // La prop devuelve false → la config en body → 400
        when(props.permitirConfiguracionEnSolicitud()).thenReturn(false);

        String body = """
                {
                  "origen":  {"lat": -12.0907, "lng": -77.0207},
                  "destino": {"lat": -12.1012, "lng": -77.0347},
                  "configuracion": "RULE"
                }
                """;
        mockMvc.perform(post("/api/v1/navigations")
                        .with(jwt().jwt(j -> j.subject("1")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    // =========================================================================
    // POST /api/v1/navigations/{id}/position
    // =========================================================================

    @Test
    void posicion_sinToken_401() throws Exception {
        mockMvc.perform(post("/api/v1/navigations/1/position")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY_POSICION))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void posicion_sinDisparador_200_recalculoNull() throws Exception {
        when(recalculoService.procesarPosicion(anyLong(), anyLong(),
                anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(RecalculoService.ResultadoPosicion.soloAledanos(List.of()));

        mockMvc.perform(post("/api/v1/navigations/1/position")
                        .with(jwt().jwt(j -> j.subject("1")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY_POSICION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recalculo").doesNotExist());
    }

    @Test
    void posicion_conRecalculo_200_devuelveDetalle() throws Exception {
        var detalle = new RecalculoDetalleDto(
                DisparadorRecalculo.REROUTE, Accion.RECALCULAR_VIGENTE,
                false, "Recalculando...", rutaFake(), true, 45L);
        when(recalculoService.procesarPosicion(anyLong(), anyLong(),
                anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(new RecalculoService.ResultadoPosicion(
                        List.of(), true, DisparadorRecalculo.REROUTE,
                        Accion.RECALCULAR_VIGENTE, false,
                        "Recalculando...", rutaFake(), true, 45L));

        mockMvc.perform(post("/api/v1/navigations/1/position")
                        .with(jwt().jwt(j -> j.subject("1")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY_POSICION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recalculo.disparador").value("REROUTE"))
                .andExpect(jsonPath("$.recalculo.accion").value("RECALCULAR_VIGENTE"))
                .andExpect(jsonPath("$.recalculo.exito").value(true));
    }

    @Test
    void posicion_sesionAjena_404() throws Exception {
        when(recalculoService.procesarPosicion(anyLong(), anyLong(),
                anyDouble(), anyDouble(), anyDouble()))
                .thenThrow(new NavegacionNoEncontradaException("No encontrada"));

        mockMvc.perform(post("/api/v1/navigations/99/position")
                        .with(jwt().jwt(j -> j.subject("1")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY_POSICION))
                .andExpect(status().isNotFound());
    }

    @Test
    void posicion_velNegativa_400() throws Exception {
        String body = """
                {"lat": -12.09, "lng": -77.02, "velocidadKmh": -5.0}
                """;
        mockMvc.perform(post("/api/v1/navigations/1/position")
                        .with(jwt().jwt(j -> j.subject("1")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    // =========================================================================
    // POST /api/v1/navigations/{id}/recalculations
    // =========================================================================

    @Test
    void recalculoManual_sinToken_401() throws Exception {
        mockMvc.perform(post("/api/v1/navigations/1/recalculations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY_POSICION))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void recalculoManual_exitoso_200() throws Exception {
        when(navService.recalcularManual(anyLong(), anyLong(),
                anyDouble(), anyDouble()))
                .thenReturn(new RecalculoManualDto(true, null, rutaFake()));

        mockMvc.perform(post("/api/v1/navigations/1/recalculations")
                        .with(jwt().jwt(j -> j.subject("1")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY_POSICION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exito").value(true))
                .andExpect(jsonPath("$.nuevaRuta.distanciaM").value(1200.0));
    }

    @Test
    void recalculoManual_sesionAjena_404() throws Exception {
        when(navService.recalcularManual(anyLong(), anyLong(),
                anyDouble(), anyDouble()))
                .thenThrow(new NavegacionNoEncontradaException("No encontrada"));

        mockMvc.perform(post("/api/v1/navigations/1/recalculations")
                        .with(jwt().jwt(j -> j.subject("1")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY_POSICION))
                .andExpect(status().isNotFound());
    }

    @Test
    void recalculoManual_sinRuta_200_exitoFalse() throws Exception {
        when(navService.recalcularManual(anyLong(), anyLong(),
                anyDouble(), anyDouble()))
                .thenReturn(new RecalculoManualDto(false, "Sin alternativa", null));

        mockMvc.perform(post("/api/v1/navigations/1/recalculations")
                        .with(jwt().jwt(j -> j.subject("1")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY_POSICION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exito").value(false))
                .andExpect(jsonPath("$.nuevaRuta").doesNotExist());
    }
}
