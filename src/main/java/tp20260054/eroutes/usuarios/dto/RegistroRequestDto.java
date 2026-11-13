package tp20260054.eroutes.usuarios.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegistroRequestDto(

        @NotBlank @Email @Size(max = 160)
        String correo,

        @NotBlank @Size(min = 8, max = 72, message = "La contraseña debe tener entre 8 y 72 caracteres")
        String contrasena,

        @NotBlank @Size(max = 100)
        String nombres,

        @NotBlank @Size(max = 100)
        String apellidos,

        @Pattern(regexp = "^\\+?[0-9]{7,15}$", message = "Formato de teléfono inválido")
        String telefono,

        @Valid
        VehiculoRequestDto vehiculo) {
}
