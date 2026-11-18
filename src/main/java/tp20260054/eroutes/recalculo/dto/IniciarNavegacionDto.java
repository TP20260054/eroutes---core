package tp20260054.eroutes.recalculo.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import tp20260054.eroutes.navegacion.ConfiguracionNavegacion;
import tp20260054.eroutes.routing.costo.Preset;
import tp20260054.eroutes.routing.dto.PuntoDto;

/**
 * Body de {@code POST /api/v1/navigations}.
 *
 * <p>{@code configuracion} se acepta solo si
 * {@code eroutes.recalculo.permitir-configuracion-en-solicitud=true}
 * (flag de desarrollo/evaluación).
 */
public record IniciarNavegacionDto(
        @NotNull @Valid PuntoDto origen,
        @NotNull @Valid PuntoDto destino,
        Preset preset,                       // null → RAPIDA por defecto
        Long vehiculoId,
        ConfiguracionNavegacion configuracion // null → configuracion-defecto de propiedades
) {}
