package tp20260054.eroutes.routing.dto;

import java.util.List;
import tp20260054.eroutes.mapeado.dto.Coordenada;

public record TramoRutaDto(
        long id,
        String nombre,
        String tipoVia,
        double longitudM,
        List<Coordenada> coordenadas) {
}
