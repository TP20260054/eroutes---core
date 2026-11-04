package tp20260054.eroutes.mapeado.dto;

import java.util.List;

public record TramoDetalleDto(Long id, Long osmWayId, Long nodoOrigen, Long nodoDestino,
		String tipoVia, double longitudM, Integer velocidadMax, boolean esCiclovia,
		boolean validado, boolean sentidoUnico, List<Coordenada> coordenadas) {
}
