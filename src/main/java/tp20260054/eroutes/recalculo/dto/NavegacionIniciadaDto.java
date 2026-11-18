package tp20260054.eroutes.recalculo.dto;

import com.fasterxml.jackson.databind.JsonNode;
import tp20260054.eroutes.navegacion.ConfiguracionNavegacion;
import tp20260054.eroutes.routing.costo.Preset;
import tp20260054.eroutes.routing.dto.RutaDto;

/** Respuesta 201 de {@code POST /api/v1/navigations}. */
public record NavegacionIniciadaDto(
        Long navegacionId,
        RutaDto ruta,
        Preset preset,
        JsonNode pesosUsados,
        ConfiguracionNavegacion configuracion
) {}
