package tp20260054.eroutes.usuarios.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CambiarContrasenaDto(
        @NotBlank String contrasenaActual,
        @NotBlank @Size(min = 8, max = 72) String contrasenaNueva) {
}
