package tp20260054.eroutes.usuarios.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import tp20260054.eroutes.usuarios.TipoVehiculo;

public record VehiculoRequestDto(

        @Size(max = 60)
        String alias,

        @Size(max = 60)
        String marca,

        @NotBlank @Size(max = 80)
        String modelo,

        @NotNull
        TipoVehiculo tipo,

        @NotNull @Min(5) @Max(80)
        Integer velocidadMaximaKmh,

        @Pattern(regexp = "^[A-Za-z0-9]{1,10}$", message = "Placa inválida: solo alfanumérico, máximo 10 caracteres")
        String placa) {
}
