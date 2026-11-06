package tp20260054.eroutes.routing.graph;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.Coordinate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import tp20260054.eroutes.mapeado.entity.Nodo;
import tp20260054.eroutes.mapeado.entity.Tramo;
import tp20260054.eroutes.mapeado.repository.NodoRepository;
import tp20260054.eroutes.mapeado.repository.TramoRepository;
import tp20260054.eroutes.vehiculo.PerfilVehiculoProvider;

/**
 * Construye y mantiene grafos en memoria indexados por categoría reglamentaria.
 *
 * <p>Cada categoría implica un conjunto distinto de tipos de vía excluidos, por lo que
 * requiere su propio grafo. Los grafos se construyen bajo demanda la primera vez que
 * se solicitan ({@code ConcurrentHashMap.computeIfAbsent}) y se reutilizan en adelante.
 *
 * <p>Al arrancar ({@code ApplicationReadyEvent}) se precarga el grafo de la categoría
 * del perfil hardcodeado actual.
 */
@Component
@RequiredArgsConstructor
public class GrafoProvider {

    private static final Logger log = LoggerFactory.getLogger(GrafoProvider.class);

    private final NodoRepository nodoRepository;
    private final TramoRepository tramoRepository;
    private final PerfilVehiculoProvider perfilProvider;

    /** Caché de grafos: categoría reglamentaria → GrafoEnMemoria */
    private final ConcurrentHashMap<String, GrafoEnMemoria> cache = new ConcurrentHashMap<>();

    /** Nodos y tramos raw cargados una sola vez desde la BD */
    private volatile List<GrafoEnMemoria.NodoRaw> nodosRaw;
    private volatile List<GrafoEnMemoria.AristaRaw> aristasRaw;

    // -------------------------------------------------------------------------
    // Arranque
    // -------------------------------------------------------------------------

    @EventListener(ApplicationReadyEvent.class)
    public void precargarAlArrancar() {
        try {
            log.info("[Grafo] Cargando nodos y tramos desde la BD...");
            cargarRaw();
            String categoriaActual = perfilProvider.perfil().categoriaReglamentaria();
            log.info("[Grafo] Precargando grafo para categoría '{}'...", categoriaActual);
            obtenerPorCategoria(categoriaActual);
        } catch (Exception e) {
            log.error("[Grafo] Fallo al precargar el grafo: {}", e.getMessage(), e);
        }
    }

    // -------------------------------------------------------------------------
    // API pública
    // -------------------------------------------------------------------------

    /**
     * Devuelve el grafo para la categoría reglamentaria dada.
     * Lo construye la primera vez (bajo demanda) y lo cachea.
     *
     * @return el grafo, o {@code null} si los datos raw aún no se han cargado
     */
    public GrafoEnMemoria obtenerPorCategoria(String categoriaReglamentaria) {
        if (nodosRaw == null) return null;
        return cache.computeIfAbsent(categoriaReglamentaria, this::construirParaCategoria);
    }

    /** Conveniencia: grafo del perfil activo. */
    public GrafoEnMemoria obtenerParaPerfilActual() {
        return obtenerPorCategoria(perfilProvider.perfil().categoriaReglamentaria());
    }

    // -------------------------------------------------------------------------
    // Construcción
    // -------------------------------------------------------------------------

    private synchronized void cargarRaw() {
        if (nodosRaw != null) return; // ya cargado por otro hilo

        List<Nodo> nodos = nodoRepository.findAll();
        List<Tramo> tramos = tramoRepository.findAll();
        log.info("[Grafo] Raw: {} nodos, {} tramos", nodos.size(), tramos.size());

        nodosRaw = nodos.stream()
                .map(n -> new GrafoEnMemoria.NodoRaw(
                        n.getId(),
                        n.getGeom().getY(),  // lat = Y
                        n.getGeom().getX(),  // lng = X
                        n.getControl()))
                .toList();

        aristasRaw = tramos.stream()
                .map(t -> new GrafoEnMemoria.AristaRaw(
                        t.getNodoOrigen(),
                        t.getNodoDestino(),
                        t.getId(),
                        t.getNombre(),
                        t.getTipoVia(),
                        t.getLongitudM(),
                        t.getVelocidadMax(),
                        t.isValidado(),
                        t.isEsCiclovia(),
                        t.isSentidoUnico(),
                        coordenadasDeGeom(t)))
                .toList();
    }

    private GrafoEnMemoria construirParaCategoria(String categoria) {
        log.info("[Grafo] Construyendo grafo para categoría '{}'...", categoria);
        List<String> excList = perfilProvider.tiposExcluidosPorCategoria(categoria);
        Set<String> excluidos = Set.copyOf(excList);

        GrafoEnMemoria g = GrafoEnMemoria.construir(nodosRaw, aristasRaw, excluidos);

        log.info("[Grafo][{}] Excluidos por tipo de vía: {} aristas", categoria,
                g.getAristasExcluidasTipoVia());
        log.info("[Grafo][{}] Nodos finales (SCC mayor): {}, Aristas finales: {}",
                categoria, g.getNodosFinales(), g.getAristasFinales());
        return g;
    }

    private static double[][] coordenadasDeGeom(Tramo t) {
        Coordinate[] coords = t.getGeom().getCoordinates();
        double[][] pts = new double[coords.length][2];
        for (int i = 0; i < coords.length; i++) {
            pts[i][0] = coords[i].getY(); // lat
            pts[i][1] = coords[i].getX(); // lng
        }
        return pts;
    }
}
