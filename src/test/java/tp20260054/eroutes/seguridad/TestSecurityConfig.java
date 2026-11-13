package tp20260054.eroutes.seguridad;

import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Provee un JwtDecoder real con secreto de prueba para tests @WebMvcTest.
 * No valida el secreto (solo para tests).
 */
@TestConfiguration
public class TestSecurityConfig {

    public static final String TEST_SECRET = "test-secret-32bytes-para-pruebas-1234";

    @Bean
    @Primary
    JwtDecoder testJwtDecoder() {
        byte[] bytes = TEST_SECRET.getBytes(StandardCharsets.UTF_8);
        SecretKey key = new SecretKeySpec(bytes, "HmacSHA256");
        return NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    }
}
