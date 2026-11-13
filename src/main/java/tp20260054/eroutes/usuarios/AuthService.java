package tp20260054.eroutes.usuarios;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tp20260054.eroutes.mapeado.exception.RecursoNoEncontradoException;
import tp20260054.eroutes.seguridad.JwtService;
import tp20260054.eroutes.usuarios.dto.AuthResponseDto;
import tp20260054.eroutes.usuarios.dto.LoginRequestDto;
import tp20260054.eroutes.usuarios.dto.RegistroRequestDto;
import tp20260054.eroutes.usuarios.dto.UsuarioDto;
import tp20260054.eroutes.usuarios.dto.VehiculoDto;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UsuarioService usuarioService;
    private final UsuarioRepository usuarioRepository;
    private final VehiculoRepository vehiculoRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional
    public AuthResponseDto registrar(RegistroRequestDto req) {
        Usuario u = usuarioService.registrar(req);
        return buildResponse(u);
    }

    @Transactional(readOnly = true)
    public AuthResponseDto login(LoginRequestDto req) {
        // Mensaje genérico para no revelar cuál campo falló
        Usuario u = usuarioRepository.findByCorreoIgnoreCase(req.correo().trim())
                .orElseThrow(() -> new CredencialesInvalidasException(
                        "Correo o contraseña incorrectos"));
        if (!passwordEncoder.matches(req.contrasena(), u.getContrasenaHash())) {
            throw new CredencialesInvalidasException("Correo o contraseña incorrectos");
        }
        return buildResponse(u);
    }

    private AuthResponseDto buildResponse(Usuario u) {
        String token = jwtService.generarToken(u.getId());
        List<Vehiculo> vehiculos = vehiculoRepository.findByUsuarioIdOrderByCreadoEnAsc(u.getId());
        Long activoId = u.getVehiculoActivo() != null ? u.getVehiculoActivo().getId() : null;

        var contacto = u.getContactoEmergenciaNombre() != null
                ? new tp20260054.eroutes.usuarios.dto.ContactoEmergenciaDto(
                    u.getContactoEmergenciaNombre(), u.getContactoEmergenciaTelefono())
                : null;
        List<VehiculoDto> vDtos = vehiculos.stream()
                .map(v -> VehiculoMapper.toDto(v, activoId))
                .toList();
        UsuarioDto usuarioDto = new UsuarioDto(
                u.getId(), u.getCorreo(), u.getNombres(), u.getApellidos(),
                u.getTelefono(), contacto, activoId, vDtos);
        return new AuthResponseDto(token, "Bearer", jwtService.expiracionEnSegundos(), usuarioDto);
    }
}
