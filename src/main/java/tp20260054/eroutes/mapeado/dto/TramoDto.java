package tp20260054.eroutes.mapeado.dto;

import java.util.List;

public record TramoDto(Long id, String tipoVia, double longitudM, boolean esCiclovia,
		boolean validado, List<Coordenada> coordenadas) {
}
