package tp20260054.eroutes.recalculo.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/** Body de {@code POST /api/v1/navigations/{id}/position}. */
public record MuestraPosicionDto(
        @NotNull
        @DecimalMin(value = "-90.0",  message = "lat debe ser >= -90")
        @DecimalMax(value = "90.0",   message = "lat debe ser <= 90")
        Double lat,

        @NotNull
        @DecimalMin(value = "-180.0", message = "lng debe ser >= -180")
        @DecimalMax(value = "180.0",  message = "lng debe ser <= 180")
        Double lng,

        @NotNull
        @DecimalMin(value = "0.0",    message = "velocidadKmh debe ser >= 0")
        @DecimalMax(value = "200.0",  message = "velocidadKmh debe ser <= 200")
        Double velocidadKmh,

        Long timestamp  // epoch millis; null = usar tiempo del servidor
) {}
