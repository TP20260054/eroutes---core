package tp20260054.eroutes.eventos;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
import tp20260054.eroutes.eventos.dto.CrearEventoDto;
import tp20260054.eroutes.eventos.dto.EventoVialDto;
import tp20260054.eroutes.mapeado.dto.Coordenada;

@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
@Tag(name = "Eventos viales", description = "Gestión de eventos viales activos")
@SecurityRequirement(name = "bearerAuth")
public class EventoVialController {

    private final EventoVialService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Reportar un evento vial")
    public EventoVialDto crear(@AuthenticationPrincipal Jwt jwt,
                               @Valid @RequestBody CrearEventoDto dto) {
        Long usuarioId = Long.parseLong(jwt.getSubject());
        EventoVial ev = service.crear(
                usuarioId,
                dto.punto().lat(),
                dto.punto().lng(),
                dto.tipo(),
                dto.motivo());
        return toDto(ev);
    }

    @PostMapping("/{id}/resolve")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Resolver un evento vial")
    public void resolver(@AuthenticationPrincipal Jwt jwt,
                         @PathVariable Long id) {
        // Solo el autor del evento puede resolverlo (404 para los demás)
        service.resolver(Long.parseLong(jwt.getSubject()), id);
    }

    private EventoVialDto toDto(EventoVial ev) {
        return new EventoVialDto(
                ev.getId(), ev.getTipo(), ev.getGravedad(), ev.getTramoId(),
                new Coordenada(ev.getGeom().getY(), ev.getGeom().getX()),
                ev.getEstado(), ev.getMotivo(), ev.getCreadoEn(), ev.getExpiraEn());
    }
}
