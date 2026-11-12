package tp20260054.eroutes.routing.algorithm;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.PriorityQueue;
import tp20260054.eroutes.routing.costo.ContextoNodo;
import tp20260054.eroutes.routing.costo.FuncionCosto;
import tp20260054.eroutes.routing.graph.Arista;
import tp20260054.eroutes.routing.graph.GrafoEnMemoria;

/**
 * Dijkstra estándar con {@link PriorityQueue} sobre el grafo en memoria.
 * Sin dependencias de Spring; instanciar directamente.
 */
public final class Dijkstra {

    private Dijkstra() {}

    /**
     * Calcula el camino de menor costo entre {@code origen} y {@code destino}.
     *
     * @param grafo    grafo en memoria
     * @param funcion  función de costo a aplicar
     * @param origen   índice del nodo origen
     * @param destino  índice del nodo destino
     * @return resultado con la lista de aristas o lista vacía si no hay camino
     */
    public static ResultadoDijkstra calcular(
            GrafoEnMemoria grafo, FuncionCosto funcion, int origen, int destino) {

        int N = grafo.size();
        double[] dist = new double[N];
        int[] prevNodo = new int[N];
        Arista[] prevArista = new Arista[N];

        Arrays.fill(dist, Double.MAX_VALUE);
        Arrays.fill(prevNodo, -1);
        dist[origen] = 0.0;

        // [costo acumulado, índice de nodo]
        PriorityQueue<double[]> pq = new PriorityQueue<>(
                (a, b) -> Double.compare(a[0], b[0]));
        pq.offer(new double[]{0.0, origen});

        while (!pq.isEmpty()) {
            double[] top = pq.poll();
            double costoActual = top[0];
            int u = (int) top[1];

            if (costoActual > dist[u]) continue; // entrada obsoleta
            if (u == destino) break;

            for (Arista arista : grafo.adyacentes(u)) {
                int v = arista.destino();
                ContextoNodo ctx = new ContextoNodo(grafo.nodo(v));
                double nuevoCosto = dist[u] + funcion.costo(arista, ctx);
                if (nuevoCosto < dist[v]) {
                    dist[v] = nuevoCosto;
                    prevNodo[v] = u;
                    prevArista[v] = arista;
                    pq.offer(new double[]{nuevoCosto, v});
                }
            }
        }

        if (dist[destino] == Double.MAX_VALUE) {
            return new ResultadoDijkstra(List.of(), Double.MAX_VALUE);
        }

        // Reconstruir camino
        List<Arista> camino = new ArrayList<>();
        int actual = destino;
        while (prevArista[actual] != null) {
            camino.add(prevArista[actual]);
            actual = prevNodo[actual];
        }
        Collections.reverse(camino);
        return new ResultadoDijkstra(camino, dist[destino]);
    }
}
