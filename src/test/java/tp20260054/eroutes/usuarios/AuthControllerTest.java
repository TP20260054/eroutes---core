package tp20260054.eroutes.usuarios;

import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tp20260054.eroutes.mapeado.exception.ApiExceptionHandler;
import tp20260054.eroutes.seguridad.JwtConfig;
import tp20260054.eroutes.seguridad.JwtService;
import tp20260054.eroutes.seguridad.SecurityConfig;
import tp20260054.eroutes.seguridad.TestSecurityConfig;
import tp20260054.eroutes.usuarios.dto.AuthResponseDto;
import tp20260054.eroutes.usuarios.dto.UsuarioDto;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, JwtConfig.class, TestSecurityConfig.class, ApiExceptionHandler.class})
@TestPropertySource(properties = {
        "eroutes.seguridad.jwt-secreto=test-secret-32bytes-para-pruebas-1234",
        "eroutes.seguridad.jwt-expiracion-horas=1"
})
class AuthControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean AuthService authService;
    @MockitoBean JwtService jwtService;

    private static final String BODY_REGISTRO = """
            {"correo":"usuario@test.com","contrasena":"password123",
             "nombres":"Juan","apellidos":"Pérez"}
            """;

    private static final String BODY_LOGIN = """
            {"correo":"usuario@test.com","contrasena":"password123"}
            """;

    private AuthResponseDto fakeResponse() {
        UsuarioDto u = new UsuarioDto(1L, "usuario@test.com", "Juan", "Pérez",
                null, null, null, List.of());
        return new AuthResponseDto("tok.en.test", "Bearer", 604800L, u);
    }

    @Test
    void registro_validoDevuelve201() throws Exception {
        Mockito.when(authService.registrar(any())).thenReturn(fakeResponse());

        mockMvc.perform(post("/api/v1/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY_REGISTRO))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipoToken").value("Bearer"))
                .andExpect(jsonPath("$.usuario.correo").value("usuario@test.com"));
    }

    @Test
    void registro_sinNombresDevuelve400() throws Exception {
        String body = """
                {"correo":"a@b.com","contrasena":"password123","apellidos":"X"}
                """;
        mockMvc.perform(post("/api/v1/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registro_contrasenaCorta_devuelve400() throws Exception {
        String body = """
                {"correo":"a@b.com","contrasena":"corta","nombres":"A","apellidos":"B"}
                """;
        mockMvc.perform(post("/api/v1/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registro_correoInvalido_devuelve400() throws Exception {
        String body = """
                {"correo":"no-es-email","contrasena":"password123","nombres":"A","apellidos":"B"}
                """;
        mockMvc.perform(post("/api/v1/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registro_correoDuplicado_devuelve409() throws Exception {
        Mockito.when(authService.registrar(any()))
                .thenThrow(new CorreoDuplicadoException("correo duplicado"));

        mockMvc.perform(post("/api/v1/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY_REGISTRO))
                .andExpect(status().isConflict());
    }

    @Test
    void login_validoDevuelve200() throws Exception {
        Mockito.when(authService.login(any())).thenReturn(fakeResponse());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY_LOGIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("tok.en.test"));
    }

    @Test
    void login_credencialesInvalidas_devuelve401() throws Exception {
        Mockito.when(authService.login(any()))
                .thenThrow(new CredencialesInvalidasException("Correo o contraseña incorrectos"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY_LOGIN))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void endpoint_noExisteTokenEnRespuesta_sinHashEnBody() throws Exception {
        // El hash BCrypt nunca debe aparecer en el cuerpo de respuesta
        Mockito.when(authService.registrar(any())).thenReturn(fakeResponse());

        var result = mockMvc.perform(post("/api/v1/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY_REGISTRO))
                .andExpect(status().isCreated())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        // El hash BCrypt empieza con "$2a$"
        org.assertj.core.api.Assertions.assertThat(body).doesNotContain("$2a$");
    }
}
