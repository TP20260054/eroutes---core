package tp20260054.eroutes.routing.dto;

import tp20260054.eroutes.mapeado.dto.Coordenada;

public record ExtremoRutaDto(
        Coordenada solicitado,
        Coordenada ajustado,
        int distanciaAjusteM) {
}
