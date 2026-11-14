package tp20260054.eroutes.seguridad;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import tp20260054.eroutes.usuarios.UsuarioRepository;

/**
 * Carga el usuario por correo (en minúsculas) para la autenticación por password.
 * No expone el hash en ningún log.
 */
@Service
@RequiredArgsConstructor
public class UsuarioDetailsService implements UserDetailsService {

    private final UsuarioRepository usuarioRepository;

    @Override
    public UserDetails loadUserByUsername(String correo) throws UsernameNotFoundException {
        var usuario = usuarioRepository.findByCorreoIgnoreCase(correo)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado"));
        // Roles vacíos: sin sistema de roles en este sprint
        return User.withUsername(String.valueOf(usuario.getId()))
                .password(usuario.getContrasenaHash())
                .roles()
                .build();
    }
}
