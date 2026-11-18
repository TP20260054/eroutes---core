package tp20260054.eroutes.recalculo.dto;

import tp20260054.eroutes.routing.dto.RutaDto;

/** Respuesta de {@code POST /api/v1/navigations/{id}/recalculations}. */
public record RecalculoManualDto(
        boolean exito,
        String mensaje,
        RutaDto nuevaRuta
) {}
