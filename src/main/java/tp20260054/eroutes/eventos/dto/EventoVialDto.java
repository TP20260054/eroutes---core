package tp20260054.eroutes.eventos.dto;

import java.time.OffsetDateTime;
import tp20260054.eroutes.eventos.EstadoEventoVial;
import tp20260054.eroutes.eventos.TipoEventoVial;
import tp20260054.eroutes.mapeado.dto.Coordenada;

public record EventoVialDto(
        Long id,
        TipoEventoVial tipo,
        double gravedad,
        Long tramoId,
        Coordenada punto,
        EstadoEventoVial estado,
        String motivo,
        OffsetDateTime creadoEn,
        OffsetDateTime expiraEn) {
}
