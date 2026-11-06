package tp20260054.eroutes.routing.dto;

public record MetricasRutaDto(
        double exposicionPromedio,
        double tramosNoValidadosM,
        double porcentajeNoValidado,
        int crucesRiesgo,
        double costoTotal) {
}
