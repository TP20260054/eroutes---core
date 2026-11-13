package tp20260054.eroutes.usuarios.dto;

public record AuthResponseDto(
        String token,
        String tipoToken,
        long expiraEnS,
        UsuarioDto usuario) {
}
