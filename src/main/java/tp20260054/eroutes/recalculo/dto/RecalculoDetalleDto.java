package tp20260054.eroutes.recalculo.dto;

import tp20260054.eroutes.navegacion.DisparadorRecalculo;
import tp20260054.eroutes.recalculo.Accion;
import tp20260054.eroutes.routing.dto.RutaDto;

/**
 * Detalle del recálculo dentro de la respuesta de posición.
 * {@code null} en la respuesta cuando no hubo disparador.
 */
public record RecalculoDetalleDto(
        DisparadorRecalculo disparador,
        Accion accion,
        boolean usoRespaldo,
        String mensaje,
        RutaDto nuevaRuta,       // null si MANTENER o sin alternativa
        boolean exito,
        long tiempoCalculoMs
) {}
