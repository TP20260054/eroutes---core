package tp20260054.eroutes.seguridad;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Value;
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

import com.fasterxml.jackson.databind.ObjectMapper;

import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

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
    SecurityFilterChain filterChain(HttpSecurity http, JwtDecoder jwtDecoder,
            @Value("${springdoc.swagger-ui.enabled:false}") boolean swaggerHabilitado) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> {
                auth.requestMatchers("/api/v1/auth/**").permitAll();
                // Health y sus grupos (liveness/readiness) para los probes de la plataforma
                auth.requestMatchers("/actuator/health", "/actuator/health/**").permitAll();
                // Swagger/OpenAPI solo son públicos si springdoc.swagger-ui.enabled=true
                if (swaggerHabilitado) {
                    auth.requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll();
                }
                auth.requestMatchers(HttpMethod.GET, "/api/v1/map/**").permitAll();
                auth.requestMatchers(HttpMethod.GET, "/api/v1/places/**").permitAll();
                auth.requestMatchers("/api/v1/routes").authenticated();
                auth.requestMatchers("/api/v1/me/**").authenticated();
                auth.requestMatchers("/api/v1/vehiculos/**").authenticated();
                auth.requestMatchers("/api/v1/events/**").authenticated();
                auth.requestMatchers("/api/v1/navigations/**").authenticated();
                auth.anyRequest().authenticated();
            })
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
