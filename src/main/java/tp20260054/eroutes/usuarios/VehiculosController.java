package tp20260054.eroutes.usuarios;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tp20260054.eroutes.usuarios.dto.VehiculoDto;
import tp20260054.eroutes.usuarios.dto.VehiculoRequestDto;

@RestController
@RequestMapping("/api/v1/vehiculos")
@RequiredArgsConstructor
@Tag(name = "Vehículos")
@SecurityRequirement(name = "bearerAuth")
public class VehiculosController {

    private final UsuarioService usuarioService;

    @GetMapping
    @Operation(summary = "Listar vehículos del usuario")
    public List<VehiculoDto> listar(@AuthenticationPrincipal Jwt jwt) {
        return usuarioService.listarVehiculos(uid(jwt));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crear vehículo")
    public VehiculoDto crear(@AuthenticationPrincipal Jwt jwt,
                              @Valid @RequestBody VehiculoRequestDto req) {
        return usuarioService.crearVehiculo(uid(jwt), req);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener vehículo por id")
    public VehiculoDto obtener(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return usuarioService.obtenerVehiculo(uid(jwt), id);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Editar vehículo")
    public VehiculoDto editar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                               @Valid @RequestBody VehiculoRequestDto req) {
        return usuarioService.actualizarVehiculo(uid(jwt), id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Eliminar vehículo")
    public void eliminar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        usuarioService.eliminarVehiculo(uid(jwt), id);
    }

    @PutMapping("/{id}/activar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Activar vehículo")
    public void activar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        usuarioService.activarVehiculo(uid(jwt), id);
    }

    @GetMapping("/perfil")
    @Operation(summary = "Perfil del vehículo activo")
    public tp20260054.eroutes.vehiculo.PerfilVehiculo perfil(@AuthenticationPrincipal Jwt jwt) {
        return usuarioService.perfilVehiculoActivo(uid(jwt));
    }

    private Long uid(Jwt jwt) {
        return Long.parseLong(jwt.getSubject());
    }
}
