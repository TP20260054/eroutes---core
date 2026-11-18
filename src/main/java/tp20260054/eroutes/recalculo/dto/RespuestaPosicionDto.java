package tp20260054.eroutes.recalculo.dto;

import java.util.List;
import tp20260054.eroutes.eventos.dto.EventoVialDto;

/** Respuesta 200 de {@code POST /api/v1/navigations/{id}/position}. */
public record RespuestaPosicionDto(
        List<EventoVialDto> eventosAledanos,
        RecalculoDetalleDto recalculo         // null si no hubo disparador
) {}
