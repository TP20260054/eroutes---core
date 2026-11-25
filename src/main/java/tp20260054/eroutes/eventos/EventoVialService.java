package tp20260054.eroutes.eventos;

import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Servicio de creación y resolución de eventos viales.
 *
 * <p>Creación: busca el tramo más cercano dentro de {@code max-distancia-tramo-m}
 * usando el operador KNN {@code <->} vía {@link tp20260054.eroutes.mapeado.repository.TramoRepositoryCustom}
 * (índice GIST en {@code tramos.geom}). Si no hay tramo dentro del radio → 422.
 * La gravedad se toma de {@link EventoVialProperties} según el tipo (D6).
 * Cada usuario puede tener como máximo {@code max-activos-por-usuario} eventos activos (409).
 */
@Service
@RequiredArgsConstructor
public class EventoVialService {

    private static final GeometryFactory GF =
            new GeometryFactory(new PrecisionModel(), 4326);

    private final EventoVialRepository repo;
    private final tp20260054.eroutes.mapeado.repository.TramoRepository tramoRepository;
    private final RegistroEventosActivos registro;
    private final EventoVialProperties props;
    private final tp20260054.eroutes.usuarios.UsuarioRepository usuarioRepository;

    // -------------------------------------------------------------------------

    @Transactional
    public EventoVial crear(Long autorUsuarioId, double lat, double lng,
                            TipoEventoVial tipo, String motivo) {
        // Bloqueo de la fila del usuario: serializa creaciones concurrentes y respeta el límite
        usuarioRepository.findByIdForUpdate(autorUsuarioId)
                .orElseThrow(() -> new tp20260054.eroutes.mapeado.exception
                        .RecursoNoEncontradoException("Usuario no encontrado"));
        long activos = repo.countByAutorUsuarioIdAndEstadoAndExpiraEnAfter(
                autorUsuarioId, EstadoEventoVial.ACTIVO, OffsetDateTime.now());
        if (activos >= props.maxActivosPorUsuario()) {
            throw new LimiteEventosActivosException(
                    "Solo puedes tener hasta " + props.maxActivosPorUsuario()
                    + " eventos activos a la vez");
        }

        var tramo = tramoRepository.findTramoMasCercano(lng, lat, props.maxDistanciaTramoM())
                .orElseThrow(() -> new EventoFueraDeCoberturaException(
                        "No hay tramo vial a menos de " + (int) props.maxDistanciaTramoM()
                        + " m del punto indicado"));

        Point geom = GF.createPoint(new Coordinate(lng, lat));

        EventoVial ev = new EventoVial();
        ev.setTipo(tipo);
        ev.setGravedad(props.gravedadDe(tipo));
        ev.setTramoId(tramo.getId());
        ev.setGeom(geom);
        ev.setEstado(EstadoEventoVial.ACTIVO);
        ev.setAutorUsuarioId(autorUsuarioId);
        ev.setMotivo(motivo);
        ev.setCreadoEn(OffsetDateTime.now());
        ev.setExpiraEn(OffsetDateTime.now().plusMinutes(props.ttlBloqueoMin()));

        ev = repo.save(ev);
        registro.agregar(ev);
        return ev;
    }

    /**
     * Resuelve un evento. Solo su autor puede hacerlo: para cualquier otro usuario el
     * evento "no existe" (404), igual que si el id no existiera.
     */
    @Transactional
    public void resolver(Long usuarioId, Long eventoId) {
        EventoVial ev = repo.findById(eventoId)
                .filter(e -> e.getAutorUsuarioId().equals(usuarioId))
                .orElseThrow(() -> new tp20260054.eroutes.mapeado.exception
                        .RecursoNoEncontradoException("Evento no encontrado: " + eventoId));
        ev.setEstado(EstadoEventoVial.RESUELTO);
        ev.setResueltoEn(OffsetDateTime.now());
        repo.save(ev);
        registro.retirar(eventoId);
    }
}
