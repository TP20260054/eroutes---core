package tp20260054.eroutes.routing.costo;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tp20260054.eroutes.vehiculo.PerfilVehiculo;

/**
 * Construye {@link CostoPreset} por consulta, inyectando el colaborador de fórmulas
 * {@link CostoMulticriterio}. Las entradas de dominio por consulta son los 4
 * argumentos de {@link #crear}: pesos, referencia, perfil y snapshot de eventos.
 */
@Component
@RequiredArgsConstructor
public class CostoPresetFactory {

    private final CostoMulticriterio costoMulticriterio;

    public CostoPreset crear(PesosPreset pesos, ReferenciaRuta referencia,
                            PerfilVehiculo perfil, SnapshotEventos eventos) {
        return new CostoPreset(pesos, referencia, perfil, eventos, costoMulticriterio);
    }
}
