package tp20260054.eroutes.routing.costo;

import tp20260054.eroutes.routing.graph.Arista;

/**
 * Interfaz de la función de costo usada por Dijkstra.
 */
public interface FuncionCosto {
    double costo(Arista arista, ContextoNodo destino);
}
