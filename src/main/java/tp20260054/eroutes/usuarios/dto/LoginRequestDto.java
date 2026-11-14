package tp20260054.eroutes.usuarios.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequestDto(
        @NotBlank @Email String correo,
        @NotBlank String contrasena) {
}
