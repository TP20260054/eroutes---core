package tp20260054.eroutes.routing.algorithm;

import java.util.List;
import tp20260054.eroutes.routing.graph.Arista;

/**
 * Resultado de ejecutar Dijkstra.
 *
 * @param aristas lista de aristas en orden origen → destino (vacía si no hay camino)
 * @param costoTotal costo acumulado según la función usada
 */
public record ResultadoDijkstra(List<Arista> aristas, double costoTotal) {

    public boolean tieneCamino() { return !aristas.isEmpty(); }
}
