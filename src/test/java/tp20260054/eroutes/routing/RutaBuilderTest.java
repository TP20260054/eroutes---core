package tp20260054.eroutes.routing;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tp20260054.eroutes.routing.algorithm.Dijkstra;
import tp20260054.eroutes.routing.costo.CostoMulticriterio;
import tp20260054.eroutes.routing.costo.CostoProperties;
import tp20260054.eroutes.routing.dto.RutaDto;
import tp20260054.eroutes.routing.graph.GrafoEnMemoria;
import tp20260054.eroutes.routing.graph.GrafoEnMemoria.AristaRaw;
import tp20260054.eroutes.routing.graph.GrafoEnMemoria.NodoRaw;
import tp20260054.eroutes.routing.service.RutaBuilder;
import tp20260054.eroutes.vehiculo.PerfilVehiculo;

/**
 * Verifica que RutaBuilder (extraído de RuteoService.construirRuta) construye el
 * RutaDto correctamente. Sin Spring ni base de datos.
 */
class RutaBuilderTest {

    private static final double[][] GEOM = {{0.0, 0.0}, {0.0005, 0.0}, {0.001, 0.0}};

    private static GrafoEnMemoria grafo() {
        List<NodoRaw> nodos = List.of(
                new NodoRaw(0, 0, 0, null),
                new NodoRaw(1, 0.001, 0, null));
        List<AristaRaw> aristas = List.of(
                new AristaRaw(0, 1, 7L, "Calle", "residential", 100.0, null,
                        false, false, false, GEOM));
        return GrafoEnMemoria.construir(nodos, aristas, Set.of());
    }

    private static RutaBuilder builder() {
        CostoProperties props = new CostoProperties(
                new CostoProperties.Pesos(1, 1, 1, 0.5, 5),
                new CostoProperties.VelDefecto(50, 50, 40, 30, 30, 20, 20, 30, 10),
                new CostoProperties.Exposicion(1.0, 0.8, 0.5, 0.35, 0.25, 0.1, 0.2, 0.5),
                1.0,
                new CostoProperties.DemoraNode(0, 0, 0, 0));
        return new RutaBuilder(new CostoMulticriterio(props));
    }

    @Test
    void construir_rutaSimple() {
        GrafoEnMemoria g = grafo();
        int i0 = g.indice(0), i1 = g.indice(1);
        var res = Dijkstra.calcular(g, (a, ctx) -> a.longitudM(), i0, i1);
        assertThat(res.tieneCamino()).isTrue();

        PerfilVehiculo perfil = new PerfilVehiculo("Aima Kumai", "BICIMOTO", 45,
                "AUTOMOTOR_MENOR_L", false, List.of());

        RutaDto ruta = builder().construir(res, g, perfil, "DISTANCIA",
                -12.09, -77.02, 0, -12.10, -77.03, 0, i0, i1);

        assertThat(ruta.criterio()).isEqualTo("DISTANCIA");
        assertThat(ruta.distanciaM()).isEqualTo(100.0);
        assertThat(ruta.tiempoEstimadoS()).isGreaterThan(0.0);
        assertThat(ruta.tramos()).hasSize(1);
        assertThat(ruta.tramos().get(0).id()).isEqualTo(7L);
        assertThat(ruta.coordenadas()).isNotEmpty();
        assertThat(ruta.perfil().modelo()).isEqualTo("Aima Kumai");
        assertThat(ruta.metricas().costoTotal()).isEqualTo(100.0);
    }
}
