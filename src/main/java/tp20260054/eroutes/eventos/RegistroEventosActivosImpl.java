package tp20260054.eroutes.eventos;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tp20260054.eroutes.routing.costo.SnapshotEventos;
import tp20260054.eroutes.routing.graph.GrafoEnMemoria;

/**
 * Implementación del índice en memoria de eventos viales activos.
 *
 * <p>Mantiene dos estructuras sincronizadas bajo {@code synchronized(lock)}:
 * <ul>
 *   <li>{@code porTramo}: multimap {@code tramoId → List<EventoVial>}, para
 *       construir {@link SnapshotEventos} en O(n tramos con eventos).
 *   <li>{@code todos}: lista plana para el escaneo lineal de {@link #cercanos}.
 * </ul>
 *
 * <p>Las escrituras (agregar/retirar/expirar) bloquean el monitor del objeto
 * ({@code lock}). Las lecturas producen copias defensivas instantáneas.
 */
@Component
@RequiredArgsConstructor
public class RegistroEventosActivosImpl implements RegistroEventosActivos {

    private static final Logger log = LoggerFactory.getLogger(RegistroEventosActivosImpl.class);

    private final EventoVialRepository repo;
    private final EventoVialProperties props;

    private final Object lock = new Object();
    // tramoId → eventos activos sobre ese tramo
    private final Map<Long, List<EventoVial>> porTramo = new ConcurrentHashMap<>();
    // lista plana para búsqueda por radio (escaneo lineal)
    private final List<EventoVial> todos = new ArrayList<>();

    // -------------------------------------------------------------------------
    // Carga inicial
    // -------------------------------------------------------------------------

    @EventListener(ApplicationReadyEvent.class)
    @Transactional(readOnly = true)
    public void cargarAlArrancar() {
        List<EventoVial> activos = repo.findByEstado(EstadoEventoVial.ACTIVO);
        synchronized (lock) {
            porTramo.clear();
            todos.clear();
            activos.forEach(this::indexar);
        }
        log.info("[Eventos] Índice cargado: {} evento(s) activo(s)", activos.size());
    }

    // -------------------------------------------------------------------------
    // Expiración periódica
    // -------------------------------------------------------------------------

    @Scheduled(fixedDelayString = "${eroutes.eventos.intervalo-expiracion-s:30}000")
    @Transactional
    public void expirarVencidos() {
        int n = repo.expirarVencidos(OffsetDateTime.now());
        if (n > 0) {
            log.info("[Eventos] Expirados {} evento(s) vencido(s); recargando índice", n);
            recargar();
        }
    }

    // -------------------------------------------------------------------------
    // API pública
    // -------------------------------------------------------------------------

    @Override
    public SnapshotEventos instantanea() {
        Set<String> bloqueantes = props.tiposQueBloqueanSet();
        synchronized (lock) {
            Set<Long> bloqueados = porTramo.entrySet().stream()
                    .filter(e -> e.getValue().stream()
                            .anyMatch(ev -> bloqueantes.contains(ev.getTipo().name())))
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toUnmodifiableSet());
            return new SnapshotEventos(bloqueados);
        }
    }

    @Override
    public List<EventoVial> cercanos(double lat, double lng, double radioM) {
        List<EventoVial> copia;
        synchronized (lock) {
            copia = List.copyOf(todos);
        }
        return copia.stream()
                .filter(ev -> GrafoEnMemoria.haversine(lat, lng,
                        ev.getGeom().getY(), ev.getGeom().getX()) <= radioM)
                .toList();
    }

    @Override
    public void agregar(EventoVial evento) {
        synchronized (lock) {
            indexar(evento);
        }
    }

    @Override
    public void retirar(Long eventoId) {
        synchronized (lock) {
            todos.removeIf(ev -> ev.getId().equals(eventoId));
            porTramo.values().forEach(lista -> lista.removeIf(ev -> ev.getId().equals(eventoId)));
            porTramo.entrySet().removeIf(e -> e.getValue().isEmpty());
        }
    }

    // -------------------------------------------------------------------------
    // Helpers privados
    // -------------------------------------------------------------------------

    private void indexar(EventoVial ev) {
        todos.add(ev);
        porTramo.computeIfAbsent(ev.getTramoId(), k -> new ArrayList<>()).add(ev);
    }

    private void recargar() {
        List<EventoVial> activos = repo.findByEstado(EstadoEventoVial.ACTIVO);
        synchronized (lock) {
            porTramo.clear();
            todos.clear();
            activos.forEach(this::indexar);
        }
    }
}
