package tp20260054.eroutes.vehiculo;

import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import tp20260054.eroutes.mapeado.exception.RecursoNoEncontradoException;
import tp20260054.eroutes.usuarios.UsuarioRepository;
import tp20260054.eroutes.usuarios.UsuarioService;
import tp20260054.eroutes.usuarios.VehiculoMapper;

/**
 * Implementación que construye el perfil desde el vehículo activo del usuario autenticado.
 * Si no hay usuario autenticado (ej. durante la precarga del grafo), usa AUTOMOTOR_MENOR_L.
 */
@Component
@RequiredArgsConstructor
public class PerfilVehiculoProviderImpl implements PerfilVehiculoProvider {

    private final UsuarioRepository usuarioRepository;

    @Value("${eroutes.routing.tipos-via-excluidos-motorizado}")
    private String tiposMotorizadoCsv;

    @Value("${eroutes.routing.tipos-via-excluidos-personal}")
    private String tiposPersonalCsv;

    @Override
    public PerfilVehiculo perfil() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            Long usuarioId = Long.parseLong(jwt.getSubject());
            var u = usuarioRepository.findByIdConVehiculoActivo(usuarioId)
                    .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado"));
            var v = u.getVehiculoActivo();
            if (v == null) {
                throw new tp20260054.eroutes.routing.service.GrafoNoDisponibleException(
                        "El usuario no tiene vehículo activo. Registra un vehículo para calcular rutas.");
            }
            return VehiculoMapper.toPerfilVehiculo(v);
        }
        // Sin autenticación (p. ej. precarga del grafo): perfil por defecto motorizado
        return perfilPorDefecto();
    }

    @Override
    public List<String> tiposExcluidosPorCategoria(String categoriaReglamentaria) {
        String csv = "AUTOMOTOR_MENOR_L".equals(categoriaReglamentaria)
                ? tiposMotorizadoCsv : tiposPersonalCsv;
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();
    }

    private PerfilVehiculo perfilPorDefecto() {
        List<String> excluidos = tiposExcluidosPorCategoria("AUTOMOTOR_MENOR_L");
        return new PerfilVehiculo("Genérico", "BICIMOTO", 45,
                "AUTOMOTOR_MENOR_L", false, excluidos);
    }
}
