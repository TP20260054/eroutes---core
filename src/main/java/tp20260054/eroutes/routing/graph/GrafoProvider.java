package tp20260054.eroutes.routing.graph;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.Coordinate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import tp20260054.eroutes.mapeado.entity.Nodo;
import tp20260054.eroutes.mapeado.entity.Tramo;
import tp20260054.eroutes.mapeado.repository.NodoRepository;
import tp20260054.eroutes.mapeado.repository.TramoRepository;

/**
 * Construye y mantiene grafos en memoria indexados por categoría reglamentaria.
 * Los grafos se construyen bajo demanda y se cachean ({@code ConcurrentHashMap.computeIfAbsent}).
 * Al arrancar se precarga el grafo de AUTOMOTOR_MENOR_L (categoría más común).
 */
@Component
@RequiredArgsConstructor
public class GrafoProvider {

    private static final Logger log = LoggerFactory.getLogger(GrafoProvider.class);

    private final NodoRepository nodoRepository;
    private final TramoRepository tramoRepository;

    @Value("${eroutes.routing.tipos-via-excluidos-motorizado}")
    private String tiposMotorizadoCsv;

    @Value("${eroutes.routing.tipos-via-excluidos-personal}")
    private String tiposPersonalCsv;

    private final ConcurrentHashMap<String, GrafoEnMemoria> cache = new ConcurrentHashMap<>();

    private volatile List<GrafoEnMemoria.NodoRaw> nodosRaw;
    private volatile List<GrafoEnMemoria.AristaRaw> aristasRaw;

    // -------------------------------------------------------------------------

    @EventListener(ApplicationReadyEvent.class)
    public void precargarAlArrancar() {
        try {
            log.info("[Grafo] Cargando nodos y tramos desde la BD...");
            cargarRaw();
            // Precarga la categoría más común (motorizado)
            log.info("[Grafo] Precargando grafo para categoría 'AUTOMOTOR_MENOR_L'...");
            obtenerPorCategoria("AUTOMOTOR_MENOR_L");
        } catch (Exception e) {
            log.error("[Grafo] Fallo al precargar el grafo: {}", e.getMessage(), e);
        }
    }

    public GrafoEnMemoria obtenerPorCategoria(String categoriaReglamentaria) {
        if (nodosRaw == null) return null;
        return cache.computeIfAbsent(categoriaReglamentaria, this::construirParaCategoria);
    }

    // -------------------------------------------------------------------------

    private synchronized void cargarRaw() {
        if (nodosRaw != null) return;

        List<Nodo> nodos = nodoRepository.findAll();
        List<Tramo> tramos = tramoRepository.findAll();
        log.info("[Grafo] Raw: {} nodos, {} tramos", nodos.size(), tramos.size());

        nodosRaw = nodos.stream()
                .map(n -> new GrafoEnMemoria.NodoRaw(
                        n.getId(), n.getGeom().getY(), n.getGeom().getX(), n.getControl()))
                .toList();

        aristasRaw = tramos.stream()
                .map(t -> new GrafoEnMemoria.AristaRaw(
                        t.getNodoOrigen(), t.getNodoDestino(), t.getId(),
                        t.getNombre(), t.getTipoVia(), t.getLongitudM(),
                        t.getVelocidadMax(), t.isValidado(), t.isEsCiclovia(),
                        t.isSentidoUnico(), coordenadasDeGeom(t)))
                .toList();
    }

    private GrafoEnMemoria construirParaCategoria(String categoria) {
        log.info("[Grafo] Construyendo grafo para categoría '{}'...", categoria);
        String csv = "AUTOMOTOR_MENOR_L".equals(categoria) ? tiposMotorizadoCsv : tiposPersonalCsv;
        List<String> excList = Arrays.stream(csv.split(","))
                .map(String::trim).filter(s -> !s.isBlank()).toList();
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
            pts[i][0] = coords[i].getY();
            pts[i][1] = coords[i].getX();
        }
        return pts;
    }
}
