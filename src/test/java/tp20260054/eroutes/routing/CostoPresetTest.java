package tp20260054.eroutes.routing;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tp20260054.eroutes.routing.algorithm.Dijkstra;
import tp20260054.eroutes.routing.costo.CostoPreset;
import tp20260054.eroutes.routing.costo.CostoPresetFactory;
import tp20260054.eroutes.routing.costo.CostoProperties;
import tp20260054.eroutes.routing.costo.CostoMulticriterio;
import tp20260054.eroutes.routing.costo.ContextoNodo;
import tp20260054.eroutes.routing.costo.PesosPreset;
import tp20260054.eroutes.routing.costo.ReferenciaRuta;
import tp20260054.eroutes.routing.costo.SnapshotEventos;
import tp20260054.eroutes.routing.graph.Arista;
import tp20260054.eroutes.routing.graph.GrafoEnMemoria;
import tp20260054.eroutes.routing.graph.GrafoEnMemoria.AristaRaw;
import tp20260054.eroutes.routing.graph.GrafoEnMemoria.NodoRaw;
import tp20260054.eroutes.vehiculo.PerfilVehiculo;

/**
 * Tests de CostoPreset + Dijkstra: tramos bloqueados tienen costo infinito y
 * Dijkstra los omite, sin alterar el comportamiento con costos finitos.
 * Sin Spring ni base de datos.
 */
class CostoPresetTest {

    private static final double[][] GEOM = {{0.0, 0.0}, {1.0, 1.0}};

    /** Grafo: directo 0->1 (tramo 1, 50 m); rodeo 0->2 (tramo 2, 80 m) + 2->1 (tramo 3, 80 m). */
    private static GrafoEnMemoria grafo() {
        List<NodoRaw> nodos = List.of(
                new NodoRaw(0, 0, 0, null),
                new NodoRaw(1, 0.001, 0, null),
                new NodoRaw(2, 0, 0.001, null));
        List<AristaRaw> aristas = List.of(
                new AristaRaw(0, 1, 1L, null, "residential", 50.0, null, false, false, false, GEOM),
                new AristaRaw(0, 2, 2L, null, "residential", 80.0, null, false, false, false, GEOM),
                new AristaRaw(2, 1, 3L, null, "residential", 80.0, null, false, false, false, GEOM));
        return GrafoEnMemoria.construir(nodos, aristas, Set.of());
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

    private static int indice(GrafoEnMemoria g, long osmId) {
        Integer i = g.indice(osmId);
        assert i != null : "osmId " + osmId + " no encontrado";
        return i;
    }

    private static Arista arista(GrafoEnMemoria g, int desde, long tramoId) {
        return g.adyacentes(desde).stream()
                .filter(a -> a.tramoId() == tramoId).findFirst().orElseThrow();
    }

    @Test
    void tramoBloqueado_tieneCostoInfinito() {
        GrafoEnMemoria g = grafo();
        CostoPreset cp = new CostoPresetFactory(multicriterio())
                .crear(new PesosPreset(0.2, 0.2, 0.2, 0.2, 0.2),
                        new ReferenciaRuta(60.0, 160.0, 0), perfil(),
                        new SnapshotEventos(Set.of(1L)));

        int i0 = indice(g, 0), i1 = indice(g, 1);
        double directa = cp.costo(arista(g, i0, 1L), new ContextoNodo(g.nodo(i1)));
        assertThat(directa).isEqualTo(Double.POSITIVE_INFINITY);

        double alt = cp.costo(arista(g, i0, 2L), new ContextoNodo(g.nodo(indice(g, 2))));
        assertThat(alt).isFinite().isGreaterThan(0.0);
    }

    @Test
    void sinBloqueo_costoFinito() {
        GrafoEnMemoria g = grafo();
        CostoPreset cp = new CostoPresetFactory(multicriterio())
                .crear(new PesosPreset(0.2, 0.2, 0.2, 0.2, 0.2),
                        new ReferenciaRuta(60.0, 160.0, 0), perfil(), SnapshotEventos.VACIO);

        int i0 = indice(g, 0), i1 = indice(g, 1);
        assertThat(cp.costo(arista(g, i0, 1L), new ContextoNodo(g.nodo(i1)))).isFinite();
    }

    @Test
    void dijkstra_evitaTramoBloqueado() {
        GrafoEnMemoria g = grafo();
        CostoPreset cp = new CostoPresetFactory(multicriterio())
                .crear(new PesosPreset(0.2, 0.2, 0.2, 0.2, 0.2),
                        new ReferenciaRuta(60.0, 160.0, 0), perfil(),
                        new SnapshotEventos(Set.of(1L)));

        var res = Dijkstra.calcular(g, cp, indice(g, 0), indice(g, 1));
        assertThat(res.tieneCamino()).isTrue();
        List<Long> tramos = res.aristas().stream().map(Arista::tramoId).toList();
        assertThat(tramos).doesNotContain(1L);
        assertThat(tramos).containsExactlyInAnyOrder(2L, 3L);
    }

    @Test
    void dijkstra_tomaDirectoSinBloqueo() {
        GrafoEnMemoria g = grafo();
        CostoPreset cp = new CostoPresetFactory(multicriterio())
                .crear(new PesosPreset(0.2, 0.2, 0.2, 0.2, 0.2),
                        new ReferenciaRuta(60.0, 160.0, 0), perfil(), SnapshotEventos.VACIO);

        var res = Dijkstra.calcular(g, cp, indice(g, 0), indice(g, 1));
        assertThat(res.tieneCamino()).isTrue();
        List<Long> tramos = res.aristas().stream().map(Arista::tramoId).toList();
        assertThat(tramos).containsExactly(1L);
    }
}
