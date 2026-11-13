package tp20260054.eroutes.seguridad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtDecoder;

class JwtServiceTest {

    private static final String SECRET_32 = "test-secret-32bytes-para-pruebas-1234";

    private JwtService crearServicio(String secreto, int horas) {
        SeguridadProperties props = new SeguridadProperties(secreto, horas);
        JwtService svc = new JwtService(props);
        svc.validarSecreto(); // simula @PostConstruct
        return svc;
    }

    @Test
    void generarToken_codificaSubYExpiresAt() {
        JwtService svc = crearServicio(SECRET_32, 1);
        String token = svc.generarToken(42L);

        assertThat(token).isNotBlank();
        // Decodificar y verificar sub
        JwtDecoder decoder = svc.decoder();
        var jwt = decoder.decode(token);
        assertThat(jwt.getSubject()).isEqualTo("42");
        assertThat(jwt.getExpiresAt()).isNotNull();
        assertThat(jwt.getExpiresAt()).isAfter(java.time.Instant.now());
    }

    @Test
    void expiracionEnSegundos_coincideConHoras() {
        JwtService svc = crearServicio(SECRET_32, 168);
        assertThat(svc.expiracionEnSegundos()).isEqualTo(168L * 3600);
    }

    @Test
    void secretoCorto_lanzaIllegalState() {
        SeguridadProperties props = new SeguridadProperties("corto", 1);
        JwtService svc = new JwtService(props);
        assertThatThrownBy(svc::validarSecreto)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }

    @Test
    void secretoNulo_lanzaIllegalState() {
        SeguridadProperties props = new SeguridadProperties(null, 1);
        JwtService svc = new JwtService(props);
        assertThatThrownBy(svc::validarSecreto)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
    }

    @Test
    void hashBcryptNoAparece_enToken() {
        // El token JWT no debe contener hashes BCrypt
        JwtService svc = crearServicio(SECRET_32, 1);
        String token = svc.generarToken(1L);
        assertThat(token).doesNotContain("$2a$");
    }
}
