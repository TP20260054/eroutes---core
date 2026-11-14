package tp20260054.eroutes.usuarios.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ActualizarUsuarioDto(

        @NotBlank @Email @Size(max = 160)
        String correo,

        @NotBlank @Size(max = 100)
        String nombres,

        @NotBlank @Size(max = 100)
        String apellidos,

        @Pattern(regexp = "^\\+?[0-9]{7,15}$")
        String telefono,

        @Valid
        ContactoEmergenciaDto contactoEmergencia) {
}
