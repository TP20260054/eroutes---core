package tp20260054.eroutes.seguridad;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * Servicio JWT: generación y decodificación de tokens HS256.
 * Instanciado como bean por {@link JwtConfig}; la validación del secreto
 * se hace al crear el bean (antes de que la app arranque).
 */
public class JwtService {

    private final SeguridadProperties props;

    public JwtService(SeguridadProperties props) {
        this.props = props;
    }

    /** Llamado por {@link JwtConfig} al crear el bean. */
    public void validarSecreto() {
        String secreto = props.jwtSecreto();
        if (secreto == null || secreto.isBlank()) {
            throw new IllegalStateException(
                "JWT_SECRET no configurado. Agrega JWT_SECRET=<valor de al menos 32 bytes> "
                + "a .env.local o a las variables de entorno.");
        }
        if (secreto.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                "JWT_SECRET demasiado corto. Debe tener al menos 32 bytes en UTF-8.");
        }
    }

    /** Genera un JWT firmado con HS256. Sub = id del usuario (como String). */
    public String generarToken(Long usuarioId) {
        Instant ahora = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("eroutes")
                .subject(String.valueOf(usuarioId))
                .issuedAt(ahora)
                .expiresAt(ahora.plus(props.jwtExpiracionHoras(), ChronoUnit.HOURS))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder().encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public long expiracionEnSegundos() {
        return (long) props.jwtExpiracionHoras() * 3600;
    }

    public JwtDecoder decoder() {
        return NimbusJwtDecoder.withSecretKey(secretKey()).macAlgorithm(MacAlgorithm.HS256).build();
    }

    private JwtEncoder encoder() {
        return new NimbusJwtEncoder(new ImmutableSecret<>(secretKey()));
    }

    private SecretKey secretKey() {
        byte[] bytes = props.jwtSecreto().getBytes(StandardCharsets.UTF_8);
        return new SecretKeySpec(bytes, "HmacSHA256");
    }
}
