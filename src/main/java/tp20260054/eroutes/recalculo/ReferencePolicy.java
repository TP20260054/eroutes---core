package tp20260054.eroutes.recalculo;

import org.springframework.stereotype.Component;

/**
 * Política de referencia (OB2 3.5, configuración REFERENCE).
 *
 * <ul>
 *   <li>Desvío activo → {@link Accion#RECALCULAR_VIGENTE} (con costo TIEMPO)
 *   <li>En otro caso → {@link Accion#MANTENER}
 * </ul>
 *
 * <p>No reacciona a eventos; el recálculo usa costo {@code TIEMPO} (gestionado
 * por el servicio según la configuración de la sesión).
 */
@Component
public class ReferencePolicy implements RecalculationPolicy {

    private static final String VERSION = "REFERENCE-1.0";

    @Override
    public Accion decide(EstadoRecalculo estado) {
        return estado.desvio() ? Accion.RECALCULAR_VIGENTE : Accion.MANTENER;
    }

    @Override
    public String version() { return VERSION; }
}
