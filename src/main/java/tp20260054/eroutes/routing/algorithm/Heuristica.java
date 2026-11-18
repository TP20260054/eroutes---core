package tp20260054.eroutes.routing.algorithm;

/**
 * Heurística admisible para A*: estimación del costo restante desde un nodo
 * (dado por sus coordenadas) hasta el destino.
 *
 * <p>La heurística debe ser admisible y consistente para la {@link tp20260054.eroutes.routing.costo.FuncionCosto}
 * usada (p. ej. {@link HeuristicaMedible} para {@link tp20260054.eroutes.routing.costo.CostoPreset}).
 */
@FunctionalInterface
public interface Heuristica {
    double estimacion(double lat, double lng);
}
