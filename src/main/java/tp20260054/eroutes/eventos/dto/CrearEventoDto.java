package tp20260054.eroutes.eventos.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import tp20260054.eroutes.eventos.TipoEventoVial;
import tp20260054.eroutes.routing.dto.PuntoDto;

public record CrearEventoDto(
        @NotNull @Valid PuntoDto punto,
        @NotNull TipoEventoVial tipo,
        String motivo) {
}
