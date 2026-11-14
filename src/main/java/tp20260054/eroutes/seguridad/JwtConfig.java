package tp20260054.eroutes.seguridad;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * Registra JwtService, JwtDecoder, PasswordEncoder y AuthenticationManager.
 * La validación del secreto se hace en un ApplicationRunner que solo se
 * ejecuta en el contexto completo (no en @WebMvcTest), evitando fallos en tests.
 */
@Configuration
public class JwtConfig {

    @Bean
    JwtService jwtService(SeguridadProperties props) {
        return new JwtService(props);
    }

    @Bean
    JwtDecoder jwtDecoder(JwtService jwtService) {
        return jwtService.decoder();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration cfg) throws Exception {
        return cfg.getAuthenticationManager();
    }

    /** Valida el secreto al arrancar la app completa (no en tests de slice). */
    @Bean
    ApplicationRunner jwtSecretValidator(JwtService jwtService) {
        return args -> jwtService.validarSecreto();
    }
}
