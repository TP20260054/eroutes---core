package tp20260054.eroutes.routing.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record PuntoDto(
        @NotNull
        @DecimalMin(value = "-90.0", message = "lat debe ser >= -90")
        @DecimalMax(value = "90.0",  message = "lat debe ser <= 90")
        Double lat,

        @NotNull
        @DecimalMin(value = "-180.0", message = "lng debe ser >= -180")
        @DecimalMax(value = "180.0",  message = "lng debe ser <= 180")
        Double lng) {
}
