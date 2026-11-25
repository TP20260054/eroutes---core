package tp20260054.eroutes.eventos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import tp20260054.eroutes.mapeado.entity.Tramo;
import tp20260054.eroutes.mapeado.exception.RecursoNoEncontradoException;
import tp20260054.eroutes.mapeado.repository.TramoRepository;
import tp20260054.eroutes.usuarios.Usuario;
import tp20260054.eroutes.usuarios.UsuarioRepository;

/** Propiedad y límite de eventos activos por usuario. Sin Spring ni base de datos. */
class EventoVialServiceTest {

    private static final GeometryFactory GF = new GeometryFactory(new PrecisionModel(), 4326);
    private static final long AUTOR = 10L;
    private static final int MAXIMO = 5;

    private EventoVialRepository repo;
    private TramoRepository tramoRepository;
    private RegistroEventosActivos registro;
    private UsuarioRepository usuarioRepository;
    private EventoVialService service;

    @BeforeEach
    void setup() {
        repo = mock(EventoVialRepository.class);
        tramoRepository = mock(TramoRepository.class);
        registro = mock(RegistroEventosActivos.class);
        usuarioRepository = mock(UsuarioRepository.class);
        EventoVialProperties props = new EventoVialProperties(
                120, 50.0, List.of("BLOQUEO_TOTAL"), 30,
                new EventoVialProperties.Gravedad(1.0, 0.75, 0.5, 0.25), MAXIMO);
        service = new EventoVialService(repo, tramoRepository, registro, props, usuarioRepository);

        when(usuarioRepository.findByIdForUpdate(AUTOR)).thenReturn(Optional.of(mock(Usuario.class)));
        Tramo tramo = mock(Tramo.class);
        when(tramo.getId()).thenReturn(42L);
        when(tramoRepository.findTramoMasCercano(anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(Optional.of(tramo));
        when(repo.save(any(EventoVial.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private void activos(long n) {
        when(repo.countByAutorUsuarioIdAndEstadoAndExpiraEnAfter(
                eq(AUTOR), eq(EstadoEventoVial.ACTIVO), any(OffsetDateTime.class))).thenReturn(n);
    }

    private static EventoVial evento(long id, long autor) {
        EventoVial ev = new EventoVial();
        ev.setId(id);
        ev.setTipo(TipoEventoVial.OBRA);
        ev.setEstado(EstadoEventoVial.ACTIVO);
        ev.setAutorUsuarioId(autor);
        ev.setTramoId(42L);
        ev.setGeom(GF.createPoint(new Coordinate(-77.04, -12.09)));
        return ev;
    }

    // ------------------------------------------------------------- límite

    @Test
    void crear_porDebajoDelLimite_guardaYRegistra() {
        activos(MAXIMO - 1);

        EventoVial ev = service.crear(AUTOR, -12.09, -77.04, TipoEventoVial.OBRA, null);

        assertThat(ev.getAutorUsuarioId()).isEqualTo(AUTOR);
        verify(registro).agregar(ev);
    }

    @Test
    void crear_alLimite_lanzaLimiteEventos_sinGuardar() {
        activos(MAXIMO);

        assertThatThrownBy(() -> service.crear(AUTOR, -12.09, -77.04, TipoEventoVial.OBRA, null))
                .isInstanceOf(LimiteEventosActivosException.class)
                .hasMessageContaining(String.valueOf(MAXIMO));
        verify(repo, never()).save(any());
        verify(registro, never()).agregar(any());
    }

    @Test
    void crear_cuentaSoloLosEventosActivosYNoVencidosDelAutor() {
        activos(0);

        service.crear(AUTOR, -12.09, -77.04, TipoEventoVial.OBRA, null);

        verify(repo).countByAutorUsuarioIdAndEstadoAndExpiraEnAfter(
                eq(AUTOR), eq(EstadoEventoVial.ACTIVO), any(OffsetDateTime.class));
    }

    @Test
    void crear_usuarioInexistente_lanza404() {
        when(usuarioRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.crear(99L, -12.09, -77.04, TipoEventoVial.OBRA, null))
                .isInstanceOf(RecursoNoEncontradoException.class);
        verify(repo, never()).save(any());
    }

    // ------------------------------------------------------------- propiedad

    @Test
    void resolver_elAutor_marcaResueltoYRetiraDelIndice() {
        EventoVial ev = evento(7L, AUTOR);
        when(repo.findById(7L)).thenReturn(Optional.of(ev));

        service.resolver(AUTOR, 7L);

        assertThat(ev.getEstado()).isEqualTo(EstadoEventoVial.RESUELTO);
        assertThat(ev.getResueltoEn()).isNotNull();
        verify(repo).save(ev);
        verify(registro).retirar(7L);
    }

    @Test
    void resolver_otroUsuario_lanza404_sinModificarElEvento() {
        EventoVial ev = evento(7L, AUTOR);
        when(repo.findById(7L)).thenReturn(Optional.of(ev));

        assertThatThrownBy(() -> service.resolver(11L, 7L))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("7");
        assertThat(ev.getEstado()).isEqualTo(EstadoEventoVial.ACTIVO);
        verify(repo, never()).save(any());
        verify(registro, never()).retirar(any());
    }

    @Test
    void resolver_otroUsuario_yEventoInexistente_dan_elMismoError() {
        when(repo.findById(7L)).thenReturn(Optional.of(evento(7L, AUTOR)));
        when(repo.findById(8L)).thenReturn(Optional.empty());

        Throwable ajeno = org.assertj.core.api.Assertions.catchThrowable(() -> service.resolver(11L, 7L));
        Throwable inexistente = org.assertj.core.api.Assertions.catchThrowable(() -> service.resolver(11L, 8L));

        assertThat(ajeno).isInstanceOf(RecursoNoEncontradoException.class);
        assertThat(inexistente).isInstanceOf(RecursoNoEncontradoException.class);
    }
}
