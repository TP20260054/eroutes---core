package tp20260054.eroutes.usuarios;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tp20260054.eroutes.usuarios.dto.AuthResponseDto;
import tp20260054.eroutes.usuarios.dto.LoginRequestDto;
import tp20260054.eroutes.usuarios.dto.RegistroRequestDto;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Autenticación")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/registro")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registrar nuevo usuario")
    public AuthResponseDto registro(@Valid @RequestBody RegistroRequestDto req) {
        return authService.registrar(req);
    }

    @PostMapping("/login")
    @Operation(summary = "Iniciar sesión")
    public AuthResponseDto login(@Valid @RequestBody LoginRequestDto req) {
        return authService.login(req);
    }
}
