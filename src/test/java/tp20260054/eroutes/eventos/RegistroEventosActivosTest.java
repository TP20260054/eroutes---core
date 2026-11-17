package tp20260054.eroutes.eventos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import tp20260054.eroutes.routing.costo.SnapshotEventos;

/**
 * Tests unitarios de {@link RegistroEventosActivosImpl}.
 * Sin Spring ni base de datos: instanciación directa con repositorio mockeado.
 */
class RegistroEventosActivosTest {

    private static final GeometryFactory GF = new GeometryFactory(new PrecisionModel(), 4326);

    // Coordenadas de San Isidro, Lima (aproximadas para los tests)
    private static final double LAT_BASE = -12.0931;
    private static final double LNG_BASE = -77.0465;

    private EventoVialRepository repo;
    private EventoVialProperties props;
    private RegistroEventosActivosImpl registro;

    @BeforeEach
    void setUp() {
        repo = mock(EventoVialRepository.class);
        when(repo.findByEstado(EstadoEventoVial.ACTIVO)).thenReturn(List.of());

        props = new EventoVialProperties(
                120, 50.0, List.of("BLOQUEO_TOTAL"), 30,
                new EventoVialProperties.Gravedad(1.0, 0.75, 0.5, 0.25));

        registro = new RegistroEventosActivosImpl(repo, props);
        registro.cargarAlArrancar();
    }

    private EventoVial evento(long id, TipoEventoVial tipo, double lat, double lng) {
        EventoVial ev = new EventoVial();
        ev.setId(id);
        ev.setTipo(tipo);
        ev.setGravedad(props.gravedadDe(tipo));
        ev.setTramoId(id * 10);
        ev.setGeom(GF.createPoint(new Coordinate(lng, lat)));
        ev.setEstado(EstadoEventoVial.ACTIVO);
        ev.setAutorUsuarioId(1L);
        ev.setCreadoEn(OffsetDateTime.now());
        ev.setExpiraEn(OffsetDateTime.now().plusHours(2));
        return ev;
    }

    @Test
    void registroVacio_snapshotSinBloqueos() {
        SnapshotEventos snap = registro.instantanea();
        assertThat(snap.tramosBloqueados()).isEmpty();
    }

    @Test
    void agregarBloqueoTotal_aparecerEnSnapshot() {
        EventoVial ev = evento(1L, TipoEventoVial.BLOQUEO_TOTAL, LAT_BASE, LNG_BASE);
        registro.agregar(ev);

        SnapshotEventos snap = registro.instantanea();
        assertThat(snap.tramosBloqueados()).contains(ev.getTramoId());
        assertThat(snap.estaBloqueado(ev.getTramoId())).isTrue();
    }

    @Test
    void agregarObra_noBloquea_noEstaEnSnapshot() {
        EventoVial ev = evento(2L, TipoEventoVial.OBRA, LAT_BASE, LNG_BASE);
        registro.agregar(ev);

        SnapshotEventos snap = registro.instantanea();
        assertThat(snap.estaBloqueado(ev.getTramoId())).isFalse();
    }

    @Test
    void retirarEvento_desapareceDel_snapshot() {
        EventoVial ev = evento(1L, TipoEventoVial.BLOQUEO_TOTAL, LAT_BASE, LNG_BASE);
        registro.agregar(ev);
        assertThat(registro.instantanea().estaBloqueado(ev.getTramoId())).isTrue();

        registro.retirar(ev.getId());
        assertThat(registro.instantanea().estaBloqueado(ev.getTramoId())).isFalse();
    }

    @Test
    void cercanos_encuentraEventoDentroDelRadio() {
        // Evento a ~0 m (mismo punto)
        EventoVial ev = evento(1L, TipoEventoVial.OBRA, LAT_BASE, LNG_BASE);
        registro.agregar(ev);

        List<EventoVial> resultado = registro.cercanos(LAT_BASE, LNG_BASE, 100.0);
        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).getId()).isEqualTo(1L);
    }

    @Test
    void cercanos_ignoraEventoFueraDelRadio() {
        // Evento a ~1.5 km al norte
        double latLejos = LAT_BASE - 0.013; // ~1.4 km
        EventoVial ev = evento(1L, TipoEventoVial.BLOQUEO_TOTAL, latLejos, LNG_BASE);
        registro.agregar(ev);

        List<EventoVial> resultado = registro.cercanos(LAT_BASE, LNG_BASE, 100.0);
        assertThat(resultado).isEmpty();
    }

    @Test
    void cercanos_eventoDentroYFuera_soloDevuelveCercano() {
        EventoVial cerca = evento(1L, TipoEventoVial.OBRA, LAT_BASE, LNG_BASE);
        double latLejos = LAT_BASE - 0.013;
        EventoVial lejos = evento(2L, TipoEventoVial.OBRA, latLejos, LNG_BASE);
        registro.agregar(cerca);
        registro.agregar(lejos);

        List<EventoVial> resultado = registro.cercanos(LAT_BASE, LNG_BASE, 100.0);
        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).getId()).isEqualTo(1L);
    }

    @Test
    void snapshotEsInmutable_cambiosPosterioresNoAfectan() {
        SnapshotEventos snap1 = registro.instantanea();
        EventoVial ev = evento(1L, TipoEventoVial.BLOQUEO_TOTAL, LAT_BASE, LNG_BASE);
        registro.agregar(ev);

        // snap1 se tomó antes del agregar; no debe verse afectado
        assertThat(snap1.estaBloqueado(ev.getTramoId())).isFalse();
        // snap2 sí ve el nuevo evento
        assertThat(registro.instantanea().estaBloqueado(ev.getTramoId())).isTrue();
    }

    @Test
    void variosBloqueos_mismoTramo_sigueBloqueo_hastaRetirarTodos() {
        EventoVial ev1 = evento(1L, TipoEventoVial.BLOQUEO_TOTAL, LAT_BASE, LNG_BASE);
        EventoVial ev2 = new EventoVial();
        ev2.setId(2L);
        ev2.setTipo(TipoEventoVial.BLOQUEO_TOTAL);
        ev2.setGravedad(1.0);
        ev2.setTramoId(ev1.getTramoId()); // mismo tramo
        ev2.setGeom(GF.createPoint(new Coordinate(LNG_BASE + 0.00001, LAT_BASE)));
        ev2.setEstado(EstadoEventoVial.ACTIVO);
        ev2.setAutorUsuarioId(1L);
        ev2.setCreadoEn(OffsetDateTime.now());
        ev2.setExpiraEn(OffsetDateTime.now().plusHours(2));

        registro.agregar(ev1);
        registro.agregar(ev2);
        assertThat(registro.instantanea().estaBloqueado(ev1.getTramoId())).isTrue();

        registro.retirar(ev1.getId());
        // ev2 todavía bloquea el mismo tramo
        assertThat(registro.instantanea().estaBloqueado(ev1.getTramoId())).isTrue();

        registro.retirar(ev2.getId());
        assertThat(registro.instantanea().estaBloqueado(ev1.getTramoId())).isFalse();
    }
}
