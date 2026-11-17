package tp20260054.eroutes.recalculo;

import org.springframework.stereotype.Component;
import tp20260054.eroutes.eventos.EventoVialProperties;

/**
 * Política de regla fija (OB2 3.5, configuración RULE).
 *
 * <ul>
 *   <li>Desvío activo → {@link Accion#RECALCULAR_VIGENTE}
 *   <li>Evento relevante en la ruta restante de un tipo bloqueante → {@link Accion#RECALCULAR_VIGENTE}
 *   <li>En otro caso → {@link Accion#MANTENER}
 * </ul>
 *
 * <p>Nunca devuelve {@link Accion#RECALCULAR_SEGURA}.
 * También actúa como política de respaldo de {@link DQLPolicyConRespaldo}.
 */
@Component
public class FixedRulePolicy implements RecalculationPolicy {

    private static final String VERSION = "RULE-1.0";

    private final EventoVialProperties eventoProps;

    public FixedRulePolicy(EventoVialProperties eventoProps) {
        this.eventoProps = eventoProps;
    }

    @Override
    public Accion decide(EstadoRecalculo estado) {
        if (estado.desvio()) {
            return Accion.RECALCULAR_VIGENTE;
        }
        if (estado.eventoEnRutaRestante() && estado.eventoBloquea()) {
            return Accion.RECALCULAR_VIGENTE;
        }
        return Accion.MANTENER;
    }

    @Override
    public String version() { return VERSION; }
}
