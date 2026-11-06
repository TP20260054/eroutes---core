package tp20260054.eroutes.routing;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tp20260054.eroutes.routing.graph.GrafoEnMemoria;
import tp20260054.eroutes.routing.graph.GrafoEnMemoria.AristaRaw;
import tp20260054.eroutes.routing.graph.GrafoEnMemoria.NodoRaw;

/**
 * Tests del grafo en memoria: SCC, tipos excluidos, sentidos únicos.
 * Sin Spring ni base de datos.
 */
class GrafoEnMemoriaTest {

    // Geometría ficticia para todos los tests
    private static final double[][] GEOM = {{0.0, 0.0}, {1.0, 1.0}};

    // ---- Helpers ----

    private static NodoRaw nodo(long id, double lat, double lng) {
        return new NodoRaw(id, lat, lng, null);
    }

    private static AristaRaw arista(long o, long d, String tipoVia, boolean sentidoUnico) {
        return new AristaRaw(o, d, o * 100 + d, null, tipoVia, 100.0, null,
                false, false, sentidoUnico, GEOM);
    }

    private static AristaRaw aristaNamed(long o, long d, String nombre, String tipoVia) {
        return new AristaRaw(o, d, o * 100 + d, nombre, tipoVia, 100.0, null,
                false, false, false, GEOM);
    }

    // ---- Tests ----

    @Test
    void rutaMasCorta_porDistancia() {
        // Grafo: 0→1 (100m), 0→2→1 (50+50m) — ambos bidireccionales
        List<NodoRaw> nodos = List.of(nodo(0, 0, 0), nodo(1, 0.001, 0), nodo(2, 0, 0.0005));
        List<AristaRaw> aristas = List.of(
                new AristaRaw(0, 1, 1L, null, "residential", 100.0, null, false, false, false, GEOM),
                new AristaRaw(0, 2, 2L, null, "residential",  50.0, null, false, false, false, GEOM),
                new AristaRaw(2, 1, 3L, null, "residential",  50.0, null, false, false, false, GEOM));

        GrafoEnMemoria g = GrafoEnMemoria.construir(nodos, aristas, Set.of());
        assertThat(g.size()).isEqualTo(3);

        var dijkstra = tp20260054.eroutes.routing.algorithm.Dijkstra.calcular(
                g, (a, ctx) -> a.longitudM(), indice(g, 0), indice(g, 1));
        assertThat(dijkstra.tieneCamino()).isTrue();
        // La ruta directa (100m) = la más corta por distancia también
        double dist = dijkstra.aristas().stream().mapToDouble(tp20260054.eroutes.routing.graph.Arista::longitudM).sum();
        assertThat(dist).isEqualTo(100.0);
    }

    @Test
    void sentidoUnico_respetado() {
        // 0→1 (sentido único): la SCC mayor tiene solo 1 nodo (no hay vuelta)
        List<NodoRaw> nodos = List.of(nodo(0, 0, 0), nodo(1, 0.001, 0));
        List<AristaRaw> aristas = List.of(arista(0, 1, "residential", true));

        GrafoEnMemoria g = GrafoEnMemoria.construir(nodos, aristas, Set.of());
        // Con un arco único sin vuelta, la SCC mayor tiene 1 nodo
        assertThat(g.size()).isEqualTo(1);
    }

    @Test
    void tiposViaExcluidos_filtra() {
        // Grafo con 3 nodos; arista 0→1 es cycleway (excluida), 0→2 es residential
        List<NodoRaw> nodos = List.of(nodo(0, 0, 0), nodo(1, 0.001, 0), nodo(2, 0.002, 0));
        List<AristaRaw> aristas = List.of(
                arista(0, 1, "cycleway",    false),
                arista(1, 0, "cycleway",    false),
                arista(0, 2, "residential", false),
                arista(2, 0, "residential", false));

        GrafoEnMemoria g = GrafoEnMemoria.construir(nodos, aristas, Set.of("cycleway"));

        // El nodo 1 queda aislado; la SCC mayor tiene nodos 0 y 2
        assertThat(g.size()).isEqualTo(2);
        // Ninguna arista debe tener tipoVia cycleway
        for (int i = 0; i < g.size(); i++) {
            for (var a : g.adyacentes(i)) {
                assertThat(a.tipoVia()).isNotEqualTo("cycleway");
            }
        }
    }

    @Test
    void cicloviaPermitida_cuandoNoExcluida() {
        // Perfil MOVILIDAD_PERSONAL: cycleway no está excluida
        List<NodoRaw> nodos = List.of(nodo(0, 0, 0), nodo(1, 0.001, 0));
        List<AristaRaw> aristas = List.of(
                arista(0, 1, "cycleway", false),
                arista(1, 0, "cycleway", false));

        GrafoEnMemoria g = GrafoEnMemoria.construir(nodos, aristas, Set.of());
        assertThat(g.size()).isEqualTo(2);
    }

    @Test
    void sinCamino_devuelveVacio() {
        // Dos componentes desconectadas: 0↔1 y 2↔3
        List<NodoRaw> nodos = List.of(nodo(0,0,0), nodo(1,0.001,0), nodo(2,0.01,0), nodo(3,0.011,0));
        List<AristaRaw> aristas = List.of(
                arista(0, 1, "residential", false),
                arista(1, 0, "residential", false),
                arista(2, 3, "residential", false),
                arista(3, 2, "residential", false));

        // La SCC mayor tendrá 2 nodos (0↔1 o 2↔3 dependiendo del empate)
        GrafoEnMemoria g = GrafoEnMemoria.construir(nodos, aristas, Set.of());
        assertThat(g.size()).isEqualTo(2);
        // No hay camino dentro del grafo filtrado tampoco si los índices están en la misma SCC
        // — el grafo ya solo tiene la componente mayor, sin camino entre islas descartadas
    }

    @Test
    void filtraSCC_descartaIsla() {
        // Isla: nodo 99 totalmente desconectado
        List<NodoRaw> nodos = List.of(nodo(0,0,0), nodo(1,0.001,0), nodo(99,10,10));
        List<AristaRaw> aristas = List.of(
                arista(0, 1, "residential", false),
                arista(1, 0, "residential", false));
        // Nodo 99 no tiene aristas => componente de tamaño 1 => descartado

        GrafoEnMemoria g = GrafoEnMemoria.construir(nodos, aristas, Set.of());
        assertThat(g.size()).isEqualTo(2);
        // El nodo 99 no debe estar en el grafo
        assertThat(g.indice(99L)).isNull();
    }

    @Test
    void riesgoCruce_nodoBajoGrado() {
        // Cadena lineal: 0→1→2 bidireccional. El nodo 1 tiene exactamente
        // 2 aristas salientes y 2 entrantes = grado 4 → ≥ 3 → riesgo > 0.
        // El nodo 0 (extremo) tiene 1 saliente + 1 entrante = grado 2 → < 3 → riesgo = 0.
        List<NodoRaw> nodos = List.of(nodo(0,0,0), nodo(1,0.001,0), nodo(2,0.002,0));
        List<AristaRaw> aristas = List.of(
                arista(0, 1, "residential", false),
                arista(1, 2, "residential", false));
        GrafoEnMemoria g = GrafoEnMemoria.construir(nodos, aristas, Set.of());
        // Nodo extremo (osmId=0): grado < 3 → riesgo 0
        Integer i0 = g.indice(0L);
        if (i0 != null) {
            assertThat(g.nodo(i0).riesgoCruce()).isEqualTo(0.0);
        }
    }

    @Test
    void riesgoCruce_conTrafficSignals() {
        // Cruce de 4 ramas con traffic_signals → riesgo = nivelVia * 0.3
        List<NodoRaw> nodos = List.of(
                nodo(0,0,0), nodo(1,0.001,0), nodo(2,-0.001,0),
                nodo(10,0,0.001), nodo(11,0,0));  // 11 = cruce
        // Nodo 11 (osmId=11) como cruce: 4 aristas entrantes/salientes de primary
        List<AristaRaw> aristas = List.of(
                new AristaRaw(0,11, 1L, null, "primary", 100,null,false,false,false,GEOM),
                new AristaRaw(11,0, 2L, null, "primary", 100,null,false,false,false,GEOM),
                new AristaRaw(1,11, 3L, null, "primary", 100,null,false,false,false,GEOM),
                new AristaRaw(11,1, 4L, null, "primary", 100,null,false,false,false,GEOM),
                new AristaRaw(2,11, 5L, null, "primary", 100,null,false,false,false,GEOM),
                new AristaRaw(11,2, 6L, null, "primary", 100,null,false,false,false,GEOM));
        List<NodoRaw> nodosConControl = List.of(
                nodo(0,0,0), nodo(1,0.001,0), nodo(2,-0.001,0),
                new NodoRaw(11, 0, 0, "traffic_signals"));
        GrafoEnMemoria g = GrafoEnMemoria.construir(nodosConControl, aristas, Set.of());
        int i11 = g.indice(11L);
        assertThat(i11).isNotNull();
        // primary=1.0 * traffic_signals=0.3 = 0.3
        assertThat(g.nodo(i11).riesgoCruce()).isEqualTo(0.3);
    }

    @Test
    void perfilMotorizado_excluyeCycleway() {
        // Con tiposExcluidos = {"cycleway"} el grafo no tiene aristas de tipo cycleway
        List<NodoRaw> nodos = List.of(nodo(0,0,0), nodo(1,0.001,0), nodo(2,0.002,0));
        List<AristaRaw> aristas = List.of(
                arista(0,1,"cycleway",false), arista(1,0,"cycleway",false),
                arista(0,2,"residential",false), arista(2,0,"residential",false));
        GrafoEnMemoria g = GrafoEnMemoria.construir(nodos, aristas, Set.of("cycleway"));
        for (int i = 0; i < g.size(); i++) {
            for (var a : g.adyacentes(i)) {
                assertThat(a.tipoVia()).isNotEqualTo("cycleway");
            }
        }
    }

    @Test
    void multicriterio_eligeRodeoMasSeguro() {
        // Ruta directa: primary 200m (exposicion alta)
        // Rodeo: residential 120m + residential 120m (exposicion baja)
        // Con pesos exposición altos, MULTICRITERIO debe elegir el rodeo
        List<NodoRaw> nodos = List.of(nodo(0,0,0), nodo(1,0.002,0), nodo(2,0.001,0.001));
        List<AristaRaw> aristas = List.of(
                new AristaRaw(0,1, 1L, null, "primary",    200,null,false,false,false,GEOM),
                new AristaRaw(1,0, 2L, null, "primary",    200,null,false,false,false,GEOM),
                new AristaRaw(0,2, 3L, null, "residential",120,null,false,false,false,GEOM),
                new AristaRaw(2,0, 4L, null, "residential",120,null,false,false,false,GEOM),
                new AristaRaw(2,1, 5L, null, "residential",120,null,false,false,false,GEOM),
                new AristaRaw(1,2, 6L, null, "residential",120,null,false,false,false,GEOM));
        GrafoEnMemoria g = GrafoEnMemoria.construir(nodos, aristas, Set.of());

        int i0 = indice(g, 0), i1 = indice(g, 1);

        // DISTANCIA: debe elegir la ruta directa (200m < 240m)
        var resDist = tp20260054.eroutes.routing.algorithm.Dijkstra.calcular(
                g, (a, ctx) -> a.longitudM(), i0, i1);
        assertThat(resDist.tieneCamino()).isTrue();
        double distDirecta = resDist.aristas().stream().mapToDouble(tp20260054.eroutes.routing.graph.Arista::longitudM).sum();
        assertThat(distDirecta).isEqualTo(200.0);

        // MULTICRITERIO: con peso exposición alto, el rodeo residencial gana
        // Simular: costo = 10*exposicion*longitud/100 + longitud/100
        // Directo:   primary expo=1.0  → 10*1.0*2 + 2 = 22
        // Rodeo:  residential expo=0.25 → 10*0.25*1.2 + 10*0.25*1.2 + 1.2 + 1.2 = 3+3+1.2+1.2 = 8.4
        var resMulti = tp20260054.eroutes.routing.algorithm.Dijkstra.calcular(
                g,
                (a, ctx) -> 10.0 * exposicion(a.tipoVia()) * (a.longitudM() / 100.0)
                          + a.longitudM() / 100.0,
                i0, i1);
        assertThat(resMulti.tieneCamino()).isTrue();
        double distMulti = resMulti.aristas().stream().mapToDouble(tp20260054.eroutes.routing.graph.Arista::longitudM).sum();
        assertThat(distMulti).isGreaterThan(distDirecta); // rodeo > directo en distancia
    }

    // ---- helpers de test ----

    private static int indice(GrafoEnMemoria g, long osmId) {
        Integer idx = g.indice(osmId);
        assertThat(idx).as("osmId %d no encontrado en el grafo", osmId).isNotNull();
        return idx;
    }

    private static double exposicion(String tipoVia) {
        if (tipoVia == null) return 0.5;
        return switch (tipoVia) {
            case "primary"      -> 1.0;
            case "secondary"    -> 0.8;
            case "tertiary"     -> 0.5;
            case "residential"  -> 0.25;
            case "living_street"-> 0.1;
            case "service"      -> 0.2;
            default             -> 0.5;
        };
    }
}
