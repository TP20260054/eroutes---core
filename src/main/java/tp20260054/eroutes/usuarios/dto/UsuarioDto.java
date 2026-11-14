package tp20260054.eroutes.usuarios.dto;

import java.util.List;

public record UsuarioDto(
        Long id,
        String correo,
        String nombres,
        String apellidos,
        String telefono,
        ContactoEmergenciaDto contactoEmergencia,
        Long vehiculoActivoId,
        List<VehiculoDto> vehiculos) {
}
