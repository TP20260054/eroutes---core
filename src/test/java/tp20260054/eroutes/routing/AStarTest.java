package tp20260054.eroutes.routing;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tp20260054.eroutes.routing.algorithm.AStar;
import tp20260054.eroutes.routing.algorithm.Dijkstra;
import tp20260054.eroutes.routing.algorithm.Heuristica;
import tp20260054.eroutes.routing.algorithm.HeuristicaMedible;
import tp20260054.eroutes.routing.algorithm.ResultadoDijkstra;
import tp20260054.eroutes.routing.algorithm.Zona;
import tp20260054.eroutes.routing.costo.CostoPresetFactory;
import tp20260054.eroutes.routing.costo.CostoProperties;
import tp20260054.eroutes.routing.costo.CostoMulticriterio;
import tp20260054.eroutes.routing.costo.PesosPreset;
import tp20260054.eroutes.routing.costo.ReferenciaRuta;
import tp20260054.eroutes.routing.costo.SnapshotEventos;
import tp20260054.eroutes.routing.graph.Arista;
import tp20260054.eroutes.routing.graph.GrafoEnMemoria;
import tp20260054.eroutes.routing.graph.GrafoEnMemoria.AristaRaw;
import tp20260054.eroutes.routing.graph.GrafoEnMemoria.NodoRaw;
import tp20260054.eroutes.vehiculo.PerfilVehiculo;

/**
 * A* acotado por zona: los costos deben coincidir con Dijkstra (oráculo).
 * Nodos muy juntos (haversine pequeño) para que las aristas declaradas sean ≥ la
 * recta y la heurística (haversine/ShortPaper) sea admisible.
 */
class AStarTest {

    private static final double[][] GEOM = {{0.0, 0.0}, {1.0, 1.0}};

    /** 0 -(100 m)-> 1 ; 0 -(30 m)-> 2 -(30 m)-> 1  (rodeo 60 m). */
    private static GrafoEnMemoria grafoLineal() {
        List<NodoRaw> nodos = List.of(
                new NodoRaw(0, 0, 0, null),
                new NodoRaw(1, 0.00001, 0, null),
                new NodoRaw(2, 0, 0.00001, null));
        List<AristaRaw> aristas = List.of(
                new AristaRaw(0, 1, 1L, null, "residential", 100.0, null, false, false, false, GEOM),
                new AristaRaw(0, 2, 2L, null, "residential", 30.0, null, false, false, false, GEOM),
                new AristaRaw(2, 1, 3L, null, "residential", 30.0, null, false, false, false, GEOM));
        return GrafoEnMemoria.construir(nodos, aristas, Set.of());
    }

    private static int idx(GrafoEnMemoria g, long osmId) {
        Integer i = g.indice(osmId);
        assert i != null : "osmId " + osmId + " no encontrado";
        return i;
    }

    private static CostoMulticriterio multicriterio() {
        CostoProperties props = new CostoProperties(
                new CostoProperties.Pesos(1, 1, 1, 0.5, 5),
                new CostoProperties.VelDefecto(50, 50, 40, 30, 30, 20, 20, 30, 10),
                new CostoProperties.Exposicion(1.0, 0.8, 0.5, 0.35, 0.25, 0.1, 0.2, 0.5),
                1.0,
                new CostoProperties.DemoraNode(0, 0, 0, 0));
        return new CostoMulticriterio(props);
    }

    private static PerfilVehiculo perfil() {
        return new PerfilVehiculo("M", "BICIMOTO", 45, "AUTOMOTOR_MENOR_L", false, List.of());
    }

    @Test
    void aStar_costoCoincideConDijkstra_distancia() {
        GrafoEnMemoria g = grafoLineal();
        int i0 = idx(g, 0), i1 = idx(g, 1);
        var n0 = g.nodo(i0);
        var n1 = g.nodo(i1);
        Heuristica h = (lat, lng) -> GrafoEnMemoria.haversine(lat, lng, n1.lat(), n1.lng());
        Zona zona = Zona.alrededor(n0.lat(), n0.lng(), n1.lat(), n1.lng(), 5000);

        ResultadoDijkstra dDij = Dijkstra.calcular(g, (a, ctx) -> a.longitudM(), i0, i1);
        ResultadoDijkstra dA = AStar.calcular(g, (a, ctx) -> a.longitudM(), h, i0, i1, zona);

        assertThat(dA.tieneCamino()).isTrue();
        assertThat(dA.costoTotal()).isEqualTo(dDij.costoTotal());
        assertThat(dA.costoTotal()).isEqualTo(60.0); // rodeo 30+30 < directo 100
    }

    @Test
    void aStar_costoCoincideConDijkstra_costoPreset() {
        GrafoEnMemoria g = grafoLineal();
        int i0 = idx(g, 0), i1 = idx(g, 1);
        var n0 = g.nodo(i0);
        var n1 = g.nodo(i1);
        PesosPreset pesos = new PesosPreset(0.2, 0.2, 0.2, 0.2, 0.2);
        ReferenciaRuta ref = new ReferenciaRuta(60.0, 160.0, 0);
        var cp = new CostoPresetFactory(multicriterio()).crear(pesos, ref, perfil(), SnapshotEventos.VACIO);
        Heuristica h = HeuristicaMedible.crear(n1.lat(), n1.lng(), 45, ref, pesos);
        Zona zona = Zona.alrededor(n0.lat(), n0.lng(), n1.lat(), n1.lng(), 5000);

        ResultadoDijkstra dDij = Dijkstra.calcular(g, cp, i0, i1);
        ResultadoDijkstra dA = AStar.calcular(g, cp, h, i0, i1, zona);

        assertThat(dA.tieneCamino()).isTrue();
        assertThat(dA.costoTotal()).isEqualTo(dDij.costoTotal());
    }

    @Test
    void aStar_reintentaSinZona_siNoHayCaminoEnZona() {
        // 0 -> 2 -> 3 -> 1, con 2 y 3 muy lejos (fuera de la zona pequeña). Sin arista 0-1 directa.
        List<NodoRaw> nodos = List.of(
                new NodoRaw(0, 0, 0, null),
                new NodoRaw(1, 0.00001, 0, null),
                new NodoRaw(2, 10, 10, null),
                new NodoRaw(3, 10, 0.00001, null));
        List<AristaRaw> aristas = List.of(
                new AristaRaw(0, 2, 1L, null, "residential", 100.0, null, false, false, false, GEOM),
                new AristaRaw(2, 3, 2L, null, "residential", 100.0, null, false, false, false, GEOM),
                new AristaRaw(3, 1, 3L, null, "residential", 100.0, null, false, false, false, GEOM));
        GrafoEnMemoria g = GrafoEnMemoria.construir(nodos, aristas, Set.of());
        int i0 = idx(g, 0), i1 = idx(g, 1);
        var n0 = g.nodo(i0);
        var n1 = g.nodo(i1);
        Heuristica h = (lat, lng) -> 0.0; // heurística trivial (admisible)
        Zona zona = Zona.alrededor(n0.lat(), n0.lng(), n1.lat(), n1.lng(), 200);

        ResultadoDijkstra dDij = Dijkstra.calcular(g, (a, ctx) -> a.longitudM(), i0, i1);
        ResultadoDijkstra dA = AStar.calcular(g, (a, ctx) -> a.longitudM(), h, i0, i1, zona);

        assertThat(dDij.tieneCamino()).isTrue();
        assertThat(dA.tieneCamino()).isTrue(); // prueba que reintentó sin zona
        assertThat(dA.costoTotal()).isEqualTo(dDij.costoTotal());
        assertThat(dA.costoTotal()).isEqualTo(300.0);
    }

    @Test
    void aStar_evitaTramoBloqueado_coincideConDijkstra() {
        // directo 0->1 (tramo 1, 50 m); rodeo 0->2 (tramo 2, 80 m) + 2->1 (tramo 3, 80 m)
        List<NodoRaw> nodos = List.of(
                new NodoRaw(0, 0, 0, null),
                new NodoRaw(1, 0.00001, 0, null),
                new NodoRaw(2, 0, 0.00001, null));
        List<AristaRaw> aristas = List.of(
                new AristaRaw(0, 1, 1L, null, "residential", 50.0, null, false, false, false, GEOM),
                new AristaRaw(0, 2, 2L, null, "residential", 80.0, null, false, false, false, GEOM),
                new AristaRaw(2, 1, 3L, null, "residential", 80.0, null, false, false, false, GEOM));
        GrafoEnMemoria g = GrafoEnMemoria.construir(nodos, aristas, Set.of());
        int i0 = idx(g, 0), i1 = idx(g, 1);
        var n0 = g.nodo(i0);
        var n1 = g.nodo(i1);
        PesosPreset pesos = new PesosPreset(0.2, 0.2, 0.2, 0.2, 0.2);
        ReferenciaRuta ref = new ReferenciaRuta(60.0, 160.0, 0);
        var cp = new CostoPresetFactory(multicriterio())
                .crear(pesos, ref, perfil(), new SnapshotEventos(Set.of(1L)));
        Heuristica h = HeuristicaMedible.crear(n1.lat(), n1.lng(), 45, ref, pesos);
        Zona zona = Zona.alrededor(n0.lat(), n0.lng(), n1.lat(), n1.lng(), 5000);

        ResultadoDijkstra dDij = Dijkstra.calcular(g, cp, i0, i1);
        ResultadoDijkstra dA = AStar.calcular(g, cp, h, i0, i1, zona);

        assertThat(dA.tieneCamino()).isTrue();
        assertThat(dA.costoTotal()).isEqualTo(dDij.costoTotal());
        List<Long> tramos = dA.aristas().stream().map(Arista::tramoId).toList();
        assertThat(tramos).doesNotContain(1L);
        assertThat(tramos).containsExactlyInAnyOrder(2L, 3L);
    }
}
