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
 * A* acotado por zona.
 *
 * <p>Firma: {@code calcular(grafo, funcion, heuristica, origen, destino, zona)}.
 * La heurística debe ser admisible y consistente para {@code funcion} (p. ej.
 * {@link HeuristicaMedible} para {@link tp20260054.eroutes.routing.costo.CostoPreset}).
 *
 * <p>"Acotado por zona": solo se expanden los nodos dentro de {@code zona}
 * (rectángulo origen-destino ampliado por el margen). Si no hay camino dentro de la
 * zona, reintenta sin acotar. Los costos coinciden con {@link Dijkstra} cuando la
 * ruta óptima queda dentro de la zona (caso típico con el margen calibrado) o cuando
 * no hay ruta en la zona y se reintenta sin acotar.
 *
 * <p>Omite aristas de costo no finito (tramos bloqueados), igual que {@link Dijkstra}.
 * Devuelve {@link ResultadoDijkstra} (mismo tipo que Dijkstra) para que los costos
 * sean comparables.
 */
public final class AStar {

    private AStar() {}

    public static ResultadoDijkstra calcular(GrafoEnMemoria grafo, FuncionCosto funcion,
            Heuristica heuristica, int origen, int destino, Zona zona) {
        var acotado = buscar(grafo, funcion, heuristica, origen, destino, zona);
        if (acotado.tieneCamino()) {
            return acotado;
        }
        // Sin camino dentro de la zona: reintenta sin acotar.
        return buscar(grafo, funcion, heuristica, origen, destino, null);
    }

    private static ResultadoDijkstra buscar(GrafoEnMemoria grafo, FuncionCosto funcion,
            Heuristica heuristica, int origen, int destino, Zona zona) {

        int N = grafo.size();
        double[] dist = new double[N];   // g(n): mejor costo conocido a cada nodo
        int[] prevNodo = new int[N];
        Arista[] prevArista = new Arista[N];

        Arrays.fill(dist, Double.MAX_VALUE);
        Arrays.fill(prevNodo, -1);
        dist[origen] = 0.0;

        // Entradas del heap: [f = g + h, g, índice de nodo], ordenadas por f.
        PriorityQueue<double[]> pq = new PriorityQueue<>((a, b) -> Double.compare(a[0], b[0]));
        var nOrigen = grafo.nodo(origen);
        pq.offer(new double[]{heuristica.estimacion(nOrigen.lat(), nOrigen.lng()), 0.0, origen});

        while (!pq.isEmpty()) {
            double[] top = pq.poll();
            double g = top[1];
            int u = (int) top[2];
            if (g > dist[u]) continue; // entrada obsoleta
            if (u == destino) break;  // heurística consistente: al extraer el destino, g es óptimo
            for (Arista arista : grafo.adyacentes(u)) {
                int v = arista.destino();
                var nv = grafo.nodo(v);
                if (zona != null && !zona.contiene(nv.lat(), nv.lng())) {
                    continue; // fuera de la zona: no se expande
                }
                double costoArista = funcion.costo(arista, new ContextoNodo(nv));
                if (!Double.isFinite(costoArista)) {
                    continue; // tramo bloqueado / inválido
                }
                double nuevoG = dist[u] + costoArista;
                if (nuevoG < dist[v]) {
                    dist[v] = nuevoG;
                    prevNodo[v] = u;
                    prevArista[v] = arista;
                    double fV = nuevoG + heuristica.estimacion(nv.lat(), nv.lng());
                    pq.offer(new double[]{fV, nuevoG, v});
                }
            }
        }

        if (dist[destino] == Double.MAX_VALUE) {
            return new ResultadoDijkstra(List.of(), Double.MAX_VALUE);
        }

        // Reconstruir camino (igual que Dijkstra).
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
