package tp20260054.eroutes.recalculo;

/**
 * Contrato de la política de recálculo (OB2 3.5).
 *
 * <p>La política recibe el {@link EstadoRecalculo} y devuelve la {@link Accion} a tomar.
 * No conoce el grafo ni calcula rutas; solo decide.
 */
public interface RecalculationPolicy {

    Accion decide(EstadoRecalculo estado);

    /**
     * Versión de la política, almacenada en {@code recalculos.policy_version}.
     * Formato sugerido: {@code "RULE-1.0"}, {@code "dql-2024-01-15"}, etc.
     */
    String version();
}
