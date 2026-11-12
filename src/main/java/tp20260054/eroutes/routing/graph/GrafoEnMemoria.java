package tp20260054.eroutes.routing.graph;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Grafo dirigido en memoria. Sin dependencias de Spring ni JPA.
 *
 * <p>Construcción: usa {@link #construir(List, List, Set)} pasando registros planos.
 * Internamente almacena arrays para lat/lng, mapa osmId→índice y lista de adyacencia.
 */
public final class GrafoEnMemoria {

    /** Record plano para construir el grafo desde cualquier fuente. */
    public record NodoRaw(long osmId, double lat, double lng, String control) {}

    /** Record plano para construir el grafo desde cualquier fuente. */
    public record AristaRaw(
            long origenOsmId, long destinoOsmId,
            long tramoId, String nombre, String tipoVia,
            double longitudM, Integer velocidadMax,
            boolean validado, boolean esCiclovia,
            boolean sentidoUnico,
            double[][] geom) {}

    // --- Datos del grafo final ---
    private final NodoInfo[] nodos;
    private final List<Arista>[] adyacencia;  // índice → aristas salientes
    private final Map<Long, Integer> osmIdAIndice;

    // --- Estadísticas de construcción ---
    private final int totalNodosRaw;
    private final int totalAristasRaw;
    private final int aristasExcluidasTipoVia;
    private final int nodosFinales;
    private final int aristasFinales;

    @SuppressWarnings("unchecked")
    private GrafoEnMemoria(NodoInfo[] nodos, List<Arista>[] adyacencia,
                           Map<Long, Integer> osmIdAIndice,
                           int totalNodosRaw, int totalAristasRaw,
                           int aristasExcluidasTipoVia) {
        this.nodos = nodos;
        this.adyacencia = adyacencia;
        this.osmIdAIndice = osmIdAIndice;
        this.totalNodosRaw = totalNodosRaw;
        this.totalAristasRaw = totalAristasRaw;
        this.aristasExcluidasTipoVia = aristasExcluidasTipoVia;
        this.nodosFinales = nodos.length;
        int total = 0;
        for (List<Arista> adj : adyacencia) total += adj.size();
        this.aristasFinales = total;
    }

    // -------------------------------------------------------------------------
    // Construcción
    // -------------------------------------------------------------------------

    /**
     * Construye el grafo aplicando filtros de tipo de vía y conservando solo la
     * componente fuertemente conexa más grande (algoritmo de Kosaraju iterativo).
     *
     * @param nodosRaw      lista de nodos
     * @param aristasRaw    lista de aristas (incluye ambas direcciones si no es sentido único)
     * @param tiposExcluidos conjunto de tipos de vía a descartar
     */
    @SuppressWarnings("unchecked")
    public static GrafoEnMemoria construir(
            List<NodoRaw> nodosRaw,
            List<AristaRaw> aristasRaw,
            Set<String> tiposExcluidos) {

        int N = nodosRaw.size();
        // mapa osmId → índice provisional (todos los nodos)
        Map<Long, Integer> idx = new HashMap<>(N * 2);
        double[] lat = new double[N];
        double[] lng = new double[N];
        String[] control = new String[N];
        for (int i = 0; i < N; i++) {
            NodoRaw n = nodosRaw.get(i);
            idx.put(n.osmId(), i);
            lat[i] = n.lat();
            lng[i] = n.lng();
            control[i] = n.control();
        }

        // Aristas filtradas por tipo de vía
        List<int[]> aristasIdx = new ArrayList<>();  // [origen, destino, aristaRawIndex]
        int excluidas = 0;
        for (int i = 0; i < aristasRaw.size(); i++) {
            AristaRaw a = aristasRaw.get(i);
            String tv = a.tipoVia();
            if (tv != null && tiposExcluidos.contains(tv)) {
                excluidas++;
                continue;
            }
            Integer o = idx.get(a.origenOsmId());
            Integer d = idx.get(a.destinoOsmId());
            if (o == null || d == null) continue;
            aristasIdx.add(new int[]{o, d, i});
            // Si no es sentido único, también añadimos la inversa
            if (!a.sentidoUnico()) {
                aristasIdx.add(new int[]{d, o, -(i + 1)}); // negativo = inversa
            }
        }

        // Componentes fuertemente conexas (Kosaraju iterativo)
        // Paso 1: DFS en el grafo original → orden de finalización
        List<List<Integer>> adj = buildAdj(N, aristasIdx, false);
        List<List<Integer>> adjT = buildAdj(N, aristasIdx, true);

        int[] orden = new int[N];
        int pos = 0;
        boolean[] visitado = new boolean[N];
        for (int i = 0; i < N; i++) {
            if (!visitado[i]) {
                pos = dfsIterativo(i, adj, visitado, orden, pos);
            }
        }

        // Paso 2: DFS en el grafo transpuesto en orden inverso de finalización
        int[] componente = new int[N];
        Arrays.fill(componente, -1);
        int compId = 0;
        int[] tamComp = new int[N];
        for (int i = N - 1; i >= 0; i--) {
            int nodo = orden[i];
            if (componente[nodo] == -1) {
                int tam = marcarComponente(nodo, adjT, componente, compId);
                tamComp[compId] = tam;
                compId++;
            }
        }

        // La componente más grande
        int mejorComp = 0;
        for (int c = 1; c < compId; c++) {
            if (tamComp[c] > tamComp[mejorComp]) mejorComp = c;
        }

        // Construir mapa de índice viejo → nuevo (solo nodos en la componente mayor)
        int[] nuevoIndice = new int[N];
        Arrays.fill(nuevoIndice, -1);
        int nuevoN = 0;
        for (int i = 0; i < N; i++) {
            if (componente[i] == mejorComp) nuevoIndice[i] = nuevoN++;
        }

        // Construir lista de adyacencia final con NodoInfo y Arista
        List<Arista>[] adyFinal = new List[nuevoN];
        for (int i = 0; i < nuevoN; i++) adyFinal[i] = new ArrayList<>();

        // Primero necesitamos calcular el grado para el riesgo de cruce
        // Contamos aristas incidentes (entrantes + salientes) por nodo
        // Para el riesgo necesitamos conocer las aristas incidentes de cada nodo destino
        // Lo haremos en dos pasadas

        // Pasada 1: recopilar aristas válidas en la componente
        record AristaFinalTmp(int origen, int destino, AristaRaw raw, boolean esInversa) {}
        List<AristaFinalTmp> aristasFin = new ArrayList<>();

        for (int[] ae : aristasIdx) {
            int o = ae[0], d = ae[1];
            if (nuevoIndice[o] < 0 || nuevoIndice[d] < 0) continue;
            boolean esInversa = ae[2] < 0;
            int rawIdx = esInversa ? (-ae[2] - 1) : ae[2];
            aristasFin.add(new AristaFinalTmp(nuevoIndice[o], nuevoIndice[d],
                    aristasRaw.get(rawIdx), esInversa));
        }

        // Calcular grado de cada nodo (aristas distintas incidentes)
        // y tipos de vías incidentes (para el riesgo de cruce)
        int[] grado = new int[nuevoN];
        // Para el riesgo guardamos el nivel máximo de vías incidentes por nodo
        double[] riesgoBase = new double[nuevoN]; // 0.0 = no inicializado

        for (AristaFinalTmp a : aristasFin) {
            grado[a.destino()]++;
            grado[a.origen()]++;
        }
        // quitar duplicados (cada arista bidireccional conta 2 veces por nodo)
        // en realidad grado aquí es aristas totales incidentes (con repetición)
        // Para el criterio "grado < 3 de aristas DISTINTAS" usamos el total entrante/saliente
        // Recalculamos: contamos solo desde la perspectiva de aristas salientes del grafo original
        int[] gradoSaliente = new int[nuevoN];
        int[] gradoEntrante = new int[nuevoN];
        for (AristaFinalTmp a : aristasFin) {
            gradoSaliente[a.origen()]++;
            gradoEntrante[a.destino()]++;
        }

        // Nivel de vía (para riesgo de cruce)
        // primary/secondary/trunk = 1.0; tertiary = 0.5; resto = 0.2
        double[] nivelMaxVia = new double[nuevoN]; // nivel de la vía más alta incidente
        for (AristaFinalTmp a : aristasFin) {
            double niv = nivelVia(a.raw().tipoVia());
            if (niv > nivelMaxVia[a.destino()]) nivelMaxVia[a.destino()] = niv;
            if (niv > nivelMaxVia[a.origen()]) nivelMaxVia[a.origen()] = niv;
        }

        // Construir NodoInfo[] con riesgo precalculado
        NodoInfo[] nodosFinales = new NodoInfo[nuevoN];
        Map<Long, Integer> osmIdx = new HashMap<>(nuevoN * 2);
        for (int viejo = 0; viejo < N; viejo++) {
            int nuevo = nuevoIndice[viejo];
            if (nuevo < 0) continue;
            long osmId = nodosRaw.get(viejo).osmId();
            osmIdx.put(osmId, nuevo);
            int gradoNodo = gradoSaliente[nuevo] + gradoEntrante[nuevo];
            double riesgo = calcularRiesgoCruce(gradoNodo, nivelMaxVia[nuevo], control[viejo]);
            nodosFinales[nuevo] = new NodoInfo(osmId, lat[viejo], lng[viejo], control[viejo], riesgo);
        }

        // Construir lista de adyacencia final con geometría orientada
        for (AristaFinalTmp a : aristasFin) {
            AristaRaw raw = a.raw();
            double[][] geomFinal = a.esInversa() ? invertirGeom(raw.geom()) : raw.geom();
            adyFinal[a.origen()].add(new Arista(
                    a.destino(), raw.tramoId(), raw.nombre(), raw.tipoVia(),
                    raw.longitudM(), raw.velocidadMax(), raw.validado(), raw.esCiclovia(),
                    geomFinal));
        }

        return new GrafoEnMemoria(nodosFinales, adyFinal, osmIdx,
                nodosRaw.size(), aristasRaw.size(), excluidas);
    }

    // -------------------------------------------------------------------------
    // Helpers de construcción
    // -------------------------------------------------------------------------

    private static List<List<Integer>> buildAdj(int N, List<int[]> aristas, boolean transpuesto) {
        List<List<Integer>> adj = new ArrayList<>(N);
        for (int i = 0; i < N; i++) adj.add(new ArrayList<>());
        for (int[] a : aristas) {
            if (transpuesto) adj.get(a[1]).add(a[0]);
            else adj.get(a[0]).add(a[1]);
        }
        return adj;
    }

    private static int dfsIterativo(int inicio, List<List<Integer>> adj,
                                     boolean[] visitado, int[] orden, int pos) {
        Deque<int[]> stack = new ArrayDeque<>(); // [nodo, iterIndex]
        stack.push(new int[]{inicio, 0});
        visitado[inicio] = true;
        while (!stack.isEmpty()) {
            int[] top = stack.peek();
            int u = top[0];
            List<Integer> vecinos = adj.get(u);
            if (top[1] < vecinos.size()) {
                int v = vecinos.get(top[1]++);
                if (!visitado[v]) {
                    visitado[v] = true;
                    stack.push(new int[]{v, 0});
                }
            } else {
                stack.pop();
                orden[pos++] = u;
            }
        }
        return pos;
    }

    private static int marcarComponente(int inicio, List<List<Integer>> adjT,
                                         int[] componente, int compId) {
        Deque<Integer> stack = new ArrayDeque<>();
        stack.push(inicio);
        componente[inicio] = compId;
        int tam = 0;
        while (!stack.isEmpty()) {
            int u = stack.pop();
            tam++;
            for (int v : adjT.get(u)) {
                if (componente[v] == -1) {
                    componente[v] = compId;
                    stack.push(v);
                }
            }
        }
        return tam;
    }

    private static double nivelVia(String tipoVia) {
        if (tipoVia == null) return 0.2;
        return switch (tipoVia) {
            case "primary", "primary_link", "secondary", "secondary_link", "trunk", "trunk_link" -> 1.0;
            case "tertiary", "tertiary_link" -> 0.5;
            default -> 0.2;
        };
    }

    private static double calcularRiesgoCruce(int grado, double nivelMaxVia, String control) {
        if (grado < 3) return 0.0;
        double factorControl = switch (control == null ? "" : control) {
            case "traffic_signals" -> 0.3;
            case "stop" -> 0.6;
            default -> 1.0;
        };
        return nivelMaxVia * factorControl;
    }

    private static double[][] invertirGeom(double[][] geom) {
        if (geom == null || geom.length == 0) return geom;
        double[][] inv = new double[geom.length][];
        for (int i = 0; i < geom.length; i++) inv[i] = geom[geom.length - 1 - i];
        return inv;
    }

    // -------------------------------------------------------------------------
    // API pública
    // -------------------------------------------------------------------------

    public int size() { return nodos.length; }

    public NodoInfo nodo(int i) { return nodos[i]; }

    public List<Arista> adyacentes(int i) { return adyacencia[i]; }

    public Integer indice(long osmId) { return osmIdAIndice.get(osmId); }

    /**
     * Nodo más cercano a (lat, lng) usando haversine (fuerza bruta).
     * @return índice del nodo más cercano y su distancia en metros
     */
    public int[] nodoMasCercano(double lat, double lng) {
        int mejorIdx = 0;
        double mejorDist = Double.MAX_VALUE;
        for (int i = 0; i < nodos.length; i++) {
            double d = haversine(lat, lng, nodos[i].lat(), nodos[i].lng());
            if (d < mejorDist) { mejorDist = d; mejorIdx = i; }
        }
        return new int[]{mejorIdx, (int) Math.round(mejorDist)};
    }

    private static double haversine(double lat1, double lng1, double lat2, double lng2) {
        final double R = 6_371_000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    // --- Estadísticas ---
    public int getTotalNodosRaw()          { return totalNodosRaw; }
    public int getTotalAristasRaw()        { return totalAristasRaw; }
    public int getAristasExcluidasTipoVia(){ return aristasExcluidasTipoVia; }
    public int getNodosFinales()           { return nodosFinales; }
    public int getAristasFinales()         { return aristasFinales; }
}
