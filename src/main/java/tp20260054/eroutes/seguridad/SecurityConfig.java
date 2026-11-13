package tp20260054.eroutes.seguridad;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Configura Spring Security: sin estado, JWT HS256, CSRF deshabilitado.
 * 401 y 403 devuelven ProblemDetail JSON.
 */
@Configuration
@EnableWebSecurity
@SecurityScheme(
        name  = "bearerAuth",
        type  = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT")
public class SecurityConfig {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, JwtDecoder jwtDecoder) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/auth/**").permitAll()
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/map/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/places/**").permitAll()
                .requestMatchers("/api/v1/routes").authenticated()
                .requestMatchers("/api/v1/me/**").authenticated()
                .requestMatchers("/api/v1/vehiculos/**").authenticated()
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(rs -> rs
                .jwt(jwt -> jwt.decoder(jwtDecoder))
                .authenticationEntryPoint(this::handleAuthError)
                .accessDeniedHandler(this::handleAccessDenied)
            );
        return http.build();
    }

    // ---- Handlers de error como ProblemDetail ----

    private void handleAuthError(HttpServletRequest req, HttpServletResponse res,
                                  org.springframework.security.core.AuthenticationException ex)
            throws IOException {
        writeProblem(res, HttpStatus.UNAUTHORIZED, "No autenticado",
                "Se requiere autenticación para acceder a este recurso");
    }

    private void handleAccessDenied(HttpServletRequest req, HttpServletResponse res,
                                     org.springframework.security.access.AccessDeniedException ex)
            throws IOException {
        writeProblem(res, HttpStatus.FORBIDDEN, "Acceso denegado",
                "No tiene permisos para realizar esta operación");
    }

    private void writeProblem(HttpServletResponse res, HttpStatus status,
                               String title, String detail) throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        res.setStatus(status.value());
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(res.getWriter(), problem);
    }
}
