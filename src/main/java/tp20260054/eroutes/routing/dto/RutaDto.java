package tp20260054.eroutes.routing.dto;

import java.util.List;
import tp20260054.eroutes.mapeado.dto.Coordenada;

public record RutaDto(
        String criterio,
        PerfilResumenDto perfil,
        ExtremoRutaDto origen,
        ExtremoRutaDto destino,
        double distanciaM,
        double tiempoEstimadoS,
        MetricasRutaDto metricas,
        List<Coordenada> coordenadas,
        List<TramoRutaDto> tramos) {
}
