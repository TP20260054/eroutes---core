package tp20260054.eroutes.routing.dto;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record SolicitudRutaDto(
        @NotNull @Valid PuntoDto origen,
        @NotNull @Valid PuntoDto destino,
        String criterio,
        @Valid PesosDto pesos,
        Long vehiculoId) {
}
