package tp20260054.eroutes.usuarios;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tp20260054.eroutes.usuarios.dto.ActualizarUsuarioDto;
import tp20260054.eroutes.usuarios.dto.CambiarContrasenaDto;
import tp20260054.eroutes.usuarios.dto.UsuarioDto;

@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
@Tag(name = "Mi perfil")
@SecurityRequirement(name = "bearerAuth")
public class MeController {

    private final UsuarioService usuarioService;

    @GetMapping
    @Operation(summary = "Obtener datos del usuario autenticado")
    public UsuarioDto obtener(@AuthenticationPrincipal Jwt jwt) {
        return usuarioService.obtener(usuarioId(jwt));
    }

    @PutMapping
    @Operation(summary = "Actualizar datos del usuario autenticado")
    public UsuarioDto actualizar(@AuthenticationPrincipal Jwt jwt,
                                  @Valid @RequestBody ActualizarUsuarioDto req) {
        return usuarioService.actualizar(usuarioId(jwt), req);
    }

    @PutMapping("/contrasena")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Cambiar contraseña")
    public void cambiarContrasena(@AuthenticationPrincipal Jwt jwt,
                                   @Valid @RequestBody CambiarContrasenaDto req) {
        usuarioService.cambiarContrasena(usuarioId(jwt), req);
    }

    private Long usuarioId(Jwt jwt) {
        return Long.parseLong(jwt.getSubject());
    }
}
