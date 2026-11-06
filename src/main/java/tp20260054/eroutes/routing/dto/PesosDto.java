package tp20260054.eroutes.routing.dto;

import jakarta.validation.constraints.DecimalMin;

/**
 * Pesos opcionales para la función de costo MULTICRITERIO.
 * Los campos ausentes (null) usan el valor configurado por defecto.
 * Todos deben ser ≥ 0 si se proporcionan.
 */
public record PesosDto(
        @DecimalMin(value = "0.0", message = "pesos.tiempo debe ser >= 0")
        Double tiempo,

        @DecimalMin(value = "0.0", message = "pesos.distancia debe ser >= 0")
        Double distancia,

        @DecimalMin(value = "0.0", message = "pesos.exposicion debe ser >= 0")
        Double exposicion,

        @DecimalMin(value = "0.0", message = "pesos.noValidado debe ser >= 0")
        Double noValidado,

        @DecimalMin(value = "0.0", message = "pesos.cruce debe ser >= 0")
        Double cruce) {
}
