package tp20260054.eroutes.vehiculo;

import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Implementación hardcodeada que lee el perfil desde application.properties.
 * Sustituir por una implementación que lea el vehículo del usuario autenticado.
 */
@Component
@RequiredArgsConstructor
public class PerfilVehiculoProviderImpl implements PerfilVehiculoProvider {

    private final PerfilVehiculoProperties props;

    @Value("${eroutes.routing.tipos-via-excluidos-motorizado}")
    private String tiposMotorizadoCsv;

    @Value("${eroutes.routing.tipos-via-excluidos-personal}")
    private String tiposPersonalCsv;

    @Override
    public PerfilVehiculo perfil() {
        int vel = props.velocidadMaximaKmh();
        boolean esMotorizado = vel > 25;
        String categoria = esMotorizado ? "AUTOMOTOR_MENOR_L" : "MOVILIDAD_PERSONAL";
        boolean puedeUsarCiclovia = !esMotorizado;
        List<String> excluidos = tiposExcluidosPorCategoria(categoria);
        return new PerfilVehiculo(props.modelo(), props.tipo(), vel,
                categoria, puedeUsarCiclovia, excluidos);
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
}
