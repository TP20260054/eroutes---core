package tp20260054.eroutes.recalculo;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tp20260054.eroutes.eventos.EventoVial;
import tp20260054.eroutes.eventos.dto.EventoVialDto;
import tp20260054.eroutes.mapeado.dto.Coordenada;
import tp20260054.eroutes.navegacion.ConfiguracionNavegacion;
import tp20260054.eroutes.recalculo.dto.IniciarNavegacionDto;
import tp20260054.eroutes.recalculo.dto.MuestraPosicionDto;
import tp20260054.eroutes.recalculo.dto.NavegacionIniciadaDto;
import tp20260054.eroutes.recalculo.dto.RecalculoDetalleDto;
import tp20260054.eroutes.recalculo.dto.RecalculoManualDto;
import tp20260054.eroutes.recalculo.dto.RespuestaPosicionDto;

@RestController
@RequestMapping("/api/v1/navigations")
@RequiredArgsConstructor
@Tag(name = "Navegación", description = "Sesiones de navegación y recálculo adaptativo")
@SecurityRequirement(name = "bearerAuth")
public class NavegacionController {

    private final NavegacionService navService;
    private final RecalculoService recalculoService;
    private final RecalculoProperties props;

    // =========================================================================
    // POST /api/v1/navigations
    // =========================================================================

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Iniciar una sesión de navegación")
    public NavegacionIniciadaDto iniciar(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody IniciarNavegacionDto dto) {

        Long usuarioId = Long.parseLong(jwt.getSubject());

        // La configuración solo se acepta si el flag de desarrollo está activo
        ConfiguracionNavegacion config = dto.configuracion();
        if (config != null && !props.permitirConfiguracionEnSolicitud()) {
            throw new MuestraInvalidaException(
                    "El parámetro 'configuracion' no está habilitado en este entorno");
        }

        return navService.iniciar(
                usuarioId,
                dto.origen().lat(), dto.origen().lng(),
                dto.destino().lat(), dto.destino().lng(),
                dto.preset(),
                dto.vehiculoId(),
                config);
    }

    // =========================================================================
    // POST /api/v1/navigations/{id}/position
    // =========================================================================

    @PostMapping("/{id}/position")
    @Operation(summary = "Enviar una muestra de posición")
    public RespuestaPosicionDto posicion(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id,
            @Valid @RequestBody MuestraPosicionDto dto) {

        Long usuarioId = Long.parseLong(jwt.getSubject());
        validarMuestra(dto);

        RecalculoService.ResultadoPosicion resultado = recalculoService.procesarPosicion(
                id, usuarioId, dto.lat(), dto.lng(), dto.velocidadKmh());

        List<EventoVialDto> aledanos = resultado.eventosAledanos().stream()
                .map(this::toEventoDto).toList();

        RecalculoDetalleDto detalle = resultado.huboDisparador()
                ? new RecalculoDetalleDto(
                        resultado.disparador(),
                        resultado.accion(),
                        resultado.usoRespaldo(),
                        resultado.mensaje(),
                        resultado.nuevaRuta(),
                        resultado.exito(),
                        resultado.tiempoCalculoMs())
                : null;

        return new RespuestaPosicionDto(aledanos, detalle);
    }

    // =========================================================================
    // POST /api/v1/navigations/{id}/recalculations  — recálculo manual
    // =========================================================================

    @PostMapping("/{id}/recalculations")
    @Operation(summary = "Forzar un recálculo manual")
    public RecalculoManualDto recalculoManual(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id,
            @Valid @RequestBody MuestraPosicionDto dto) {

        Long usuarioId = Long.parseLong(jwt.getSubject());
        validarMuestra(dto);
        return navService.recalcularManual(id, usuarioId, dto.lat(), dto.lng());
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private void validarMuestra(MuestraPosicionDto dto) {
        if (dto.lat() == null || dto.lng() == null || dto.velocidadKmh() == null) {
            throw new MuestraInvalidaException(
                    "lat, lng y velocidadKmh son obligatorios");
        }
    }

    private EventoVialDto toEventoDto(EventoVial ev) {
        return new EventoVialDto(
                ev.getId(), ev.getTipo(), ev.getGravedad(), ev.getTramoId(),
                new Coordenada(ev.getGeom().getY(), ev.getGeom().getX()),
                ev.getEstado(), ev.getMotivo(),
                ev.getCreadoEn(), ev.getExpiraEn());
    }
}
