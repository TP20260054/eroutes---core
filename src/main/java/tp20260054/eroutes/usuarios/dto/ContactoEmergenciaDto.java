package tp20260054.eroutes.usuarios.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ContactoEmergenciaDto(
        @NotBlank @Size(max = 100) String nombre,
        @NotBlank @Pattern(regexp = "^\\+?[0-9]{7,15}$") String telefono) {
}
