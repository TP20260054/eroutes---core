package tp20260054.eroutes.recalculo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import tp20260054.eroutes.eventos.EventoVial;
import tp20260054.eroutes.eventos.EstadoEventoVial;
import tp20260054.eroutes.eventos.EventoVialProperties;
import tp20260054.eroutes.eventos.RegistroEventosActivos;
import tp20260054.eroutes.eventos.TipoEventoVial;
import tp20260054.eroutes.navegacion.ConfiguracionNavegacion;
import tp20260054.eroutes.navegacion.DisparadorRecalculo;
import tp20260054.eroutes.navegacion.EtiquetaNavegacion;
import tp20260054.eroutes.navegacion.Navegacion;
import tp20260054.eroutes.navegacion.NavegacionRepository;
import tp20260054.eroutes.navegacion.Recalculo;
import tp20260054.eroutes.navegacion.RecalculoRepository;
import tp20260054.eroutes.routing.costo.CostoPresetFactory;
import tp20260054.eroutes.routing.costo.CostoTiempo;
import tp20260054.eroutes.routing.costo.Preset;
import tp20260054.eroutes.routing.costo.PresetProperties;
import tp20260054.eroutes.routing.costo.SnapshotEventos;
import tp20260054.eroutes.routing.dto.ExtremoRutaDto;
import tp20260054.eroutes.routing.dto.MetricasRutaDto;
import tp20260054.eroutes.routing.dto.PerfilResumenDto;
import tp20260054.eroutes.routing.dto.RutaDto;
import tp20260054.eroutes.routing.graph.Arista;
import tp20260054.eroutes.routing.graph.GrafoEnMemoria;
import tp20260054.eroutes.routing.graph.GrafoProvider;
import tp20260054.eroutes.routing.service.ReferenciaRutaService;
import tp20260054.eroutes.routing.service.RutaBuilder;
import tp20260054.eroutes.vehiculo.PerfilVehiculo;
import tp20260054.eroutes.mapeado.dto.Coordenada;

/**
 * Tests unitarios de {@link RecalculoService} con dobles (Mockito).
 * Sin Spring ni base de datos.
 */
class RecalculoServiceTest {

    private static final GeometryFactory GF = new GeometryFactory(new PrecisionModel(), 4326);

    // ----- Coordenadas de prueba (San Isidro aproximado) -----
    private static final double LAT = -12.0931;
    private static final double LNG = -77.0465;
    private static final double LAT_DEST = -12.1000;
    private static final double LNG_DEST = -77.0400;

    // ----- Mocks -----
    private SesionNavegacionStore sesionStore;
    private RegistroEventosActivos registroEventos;
    private RecalculoRepository recalculoRepo;
    private NavegacionRepository navegacionRepo;
    private GrafoProvider grafoProvider;
    private ReferenciaRutaService referenciaService;
    private CostoPresetFactory costoPresetFactory;
    private CostoTiempo costoTiempo;
    private PresetProperties presetProperties;
    private RutaBuilder rutaBuilder;
    private RecalculoProperties props;
    private DetectorDesvio detector;
    private DQLPolicyConRespaldo dqlPolicy;
    private FixedRulePolicy fixedRule;
    private ReferencePolicy referencePolicy;
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    private RecalculoService service;

    @BeforeEach
    void setup() {
        sesionStore      = mock(SesionNavegacionStore.class);
        registroEventos  = mock(RegistroEventosActivos.class);
        recalculoRepo    = mock(RecalculoRepository.class);
        navegacionRepo   = mock(NavegacionRepository.class);
        grafoProvider    = mock(GrafoProvider.class);
        referenciaService = mock(ReferenciaRutaService.class);
        costoPresetFactory = mock(CostoPresetFactory.class);
        costoTiempo      = mock(CostoTiempo.class);
        presetProperties = mock(PresetProperties.class);
        rutaBuilder      = mock(RutaBuilder.class);
        dqlPolicy        = mock(DQLPolicyConRespaldo.class);
        fixedRule        = mock(FixedRulePolicy.class);
        referencePolicy  = mock(ReferencePolicy.class);
        objectMapper     = new com.fasterxml.jackson.databind.ObjectMapper();

        props = new RecalculoProperties(
                30, 2, 120, 500, 15, 1000, 300, 5, 1000.0, 5000.0,
                0.05, "AI", true, new RecalculoProperties.Modelo(""));

        detector = new DetectorDesvio(30, 2);

        service = new RecalculoService(
                sesionStore, registroEventos, recalculoRepo, navegacionRepo,
                grafoProvider, referenciaService, costoPresetFactory,
                costoTiempo, presetProperties, rutaBuilder, props,
                detector, dqlPolicy, fixedRule, referencePolicy, objectMapper);

        // Inyectar maxSnapM y margenZonaM vía reflexión (no hay setter; son @Value)
        try {
            var f1 = RecalculoService.class.getDeclaredField("maxSnapM");
            f1.setAccessible(true); f1.setInt(service, 200);
            var f2 = RecalculoService.class.getDeclaredField("margenZonaM");
            f2.setAccessible(true); f2.setDouble(service, 1500.0);
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private SesionNavegacion sesion(ConfiguracionNavegacion config,
                                    List<Arista> aristas) {
        PerfilVehiculo perfil = new PerfilVehiculo("M", "BICIMOTO", 45,
                "AUTOMOTOR_MENOR_L", false, List.of());
        return new SesionNavegacion(1L, 10L, perfil, config, Preset.RAPIDA, aristas);
    }

    private static Arista aristaFake(long tramoId, double longM) {
        double[][] geom = {{LAT, LNG}, {LAT_DEST, LNG_DEST}};
        return new Arista(1, tramoId, null, "residential", longM, 30, true, false, geom);
    }

    private Navegacion navegacionFake() {
        Navegacion nav = new Navegacion();
        nav.setId(1L);
        nav.setUsuarioId(10L);
        nav.setConfiguracion(ConfiguracionNavegacion.RULE);
        nav.setEtiqueta(EtiquetaNavegacion.RAPIDA);
        nav.setDistanciaM(1000);
        nav.setTiempoEstimadoS(120);
        nav.setCreadoEn(OffsetDateTime.now());
        nav.setOrigen(GF.createPoint(new Coordinate(LNG, LAT)));
        nav.setDestino(GF.createPoint(new Coordinate(LNG_DEST, LAT_DEST)));
        nav.setPesosUsados(objectMapper.createObjectNode());
        return nav;
    }

    private RutaDto rutaFake() {
        Coordenada c = new Coordenada(LAT, LNG);
        return new RutaDto("RULE",
                new PerfilResumenDto("M", "BICIMOTO", 45),
                new ExtremoRutaDto(c, c, 0), new ExtremoRutaDto(c, c, 0),
                800.0, 100.0,
                new MetricasRutaDto(0.3, 0, 0, 0, 5.0),
                List.of(c), List.of());
    }

    // =========================================================================
    // Sin disparador → solo eventos aledaños, sin registro
    // =========================================================================

    @Test
    void sinDisparador_noRegistra_devuelveAledanos() {
        // Ruta con 1 arista sin bloqueos; posición sobre la ruta
        Arista a = aristaFake(1L, 300.0);
        SesionNavegacion sesion = sesion(ConfiguracionNavegacion.RULE, List.of(a));
        when(sesionStore.obtener(1L, 10L)).thenReturn(Optional.of(sesion));
        when(registroEventos.instantanea()).thenReturn(SnapshotEventos.VACIO);
        when(registroEventos.cercanos(anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.of());
        when(recalculoRepo.contarAccionesDesde(anyLong(), any())).thenReturn(0);

        // Punto sobre la ruta (muy cercano) → no hay desvío después de 1 muestra
        var result = service.procesarPosicion(1L, 10L, LAT, LNG, 15.0);

        assertThat(result.huboDisparador()).isFalse();
        assertThat(result.nuevaRuta()).isNull();
        verify(recalculoRepo, never()).save(any());
    }

    // =========================================================================
    // Sesión ajena → 404
    // =========================================================================

    @Test
    void sesionAjena_lanzaNavegacionNoEncontrada() {
        when(sesionStore.obtener(anyLong(), anyLong())).thenReturn(Optional.empty());
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> service.procesarPosicion(1L, 99L, LAT, LNG, 15.0))
                .isInstanceOf(NavegacionNoEncontradaException.class);
    }

    // =========================================================================
    // Antirrebote (D12)
    // =========================================================================

    @Test
    void antirrebote_mismoDisparador_enCooldown_noRegistra() {
        Arista a = aristaFake(1L, 300.0);
        SesionNavegacion sesion = sesion(ConfiguracionNavegacion.RULE, List.of(a));
        // Simulamos que ya hubo una decisión muy reciente con el mismo disparador
        sesion.registrarDecision(1 << DisparadorRecalculo.REROUTE.ordinal());

        when(sesionStore.obtener(1L, 10L)).thenReturn(Optional.of(sesion));
        when(registroEventos.instantanea()).thenReturn(SnapshotEventos.VACIO);
        when(registroEventos.cercanos(anyDouble(), anyDouble(), anyDouble())).thenReturn(List.of());
        when(recalculoRepo.contarAccionesDesde(anyLong(), any())).thenReturn(0);

        // Dos muestras consecutivas lejos de la ruta para activar desvío
        double latLejos = LAT + 0.001; // ~100 m norte
        service.procesarPosicion(1L, 10L, latLejos, LNG, 15.0);
        service.procesarPosicion(1L, 10L, latLejos, LNG, 15.0);

        // Aunque haya disparador REROUTE, el antirrebote lo suprime
        verify(recalculoRepo, never()).save(any());
    }

    // =========================================================================
    // Acción 2 NO cambia el preset vigente (D10)
    // =========================================================================

    @Test
    void accion2_noModificaPresetVigente() {
        Arista a = aristaFake(99L, 300.0);
        SesionNavegacion sesion = sesion(ConfiguracionNavegacion.AI, List.of(a));
        assertThat(sesion.preset()).isEqualTo(Preset.RAPIDA);

        when(sesionStore.obtener(1L, 10L)).thenReturn(Optional.of(sesion));
        when(registroEventos.instantanea())
                .thenReturn(new SnapshotEventos(Set.of(99L))); // bloqueada
        EventoVial ev = new EventoVial();
        ev.setId(1L); ev.setTipo(TipoEventoVial.BLOQUEO_TOTAL);
        ev.setGravedad(1.0); ev.setTramoId(99L);
        ev.setEstado(EstadoEventoVial.ACTIVO);
        ev.setGeom(GF.createPoint(new Coordinate(LNG, LAT)));
        ev.setAutorUsuarioId(1L);
        ev.setCreadoEn(OffsetDateTime.now());
        ev.setExpiraEn(OffsetDateTime.now().plusHours(2));
        when(registroEventos.cercanos(anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.of(ev));
        when(recalculoRepo.contarAccionesDesde(anyLong(), any())).thenReturn(0);
        when(dqlPolicy.decidirConMetadatos(any()))
                .thenReturn(new DecisionConMetadatos(
                        Accion.RECALCULAR_SEGURA, false, null, "dql-test", 5L));
        when(dqlPolicy.decide(any())).thenReturn(Accion.RECALCULAR_SEGURA);
        when(grafoProvider.obtenerPorCategoria(any())).thenReturn(null); // sin grafo → sinRuta
        when(navegacionRepo.findById(anyLong())).thenReturn(Optional.of(navegacionFake()));
        when(recalculoRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.procesarPosicion(1L, 10L, LAT, LNG, 15.0);

        // El preset de la sesión sigue siendo RAPIDA
        assertThat(sesion.preset()).isEqualTo(Preset.RAPIDA);
    }

    // =========================================================================
    // Sin ruta alternativa → exito=false, sin excepción HTTP
    // =========================================================================

    @Test
    void sinRutaAlternativa_exitoFalse_sinExcepcion() {
        Arista a = aristaFake(99L, 300.0);
        SesionNavegacion sesion = sesion(ConfiguracionNavegacion.RULE, List.of(a));
        when(sesionStore.obtener(1L, 10L)).thenReturn(Optional.of(sesion));
        when(registroEventos.instantanea())
                .thenReturn(new SnapshotEventos(Set.of(99L)));

        EventoVial ev = new EventoVial();
        ev.setId(1L); ev.setTipo(TipoEventoVial.BLOQUEO_TOTAL);
        ev.setGravedad(1.0); ev.setTramoId(99L);
        ev.setEstado(EstadoEventoVial.ACTIVO);
        ev.setGeom(GF.createPoint(new Coordinate(LNG, LAT)));
        ev.setAutorUsuarioId(1L);
        ev.setCreadoEn(OffsetDateTime.now());
        ev.setExpiraEn(OffsetDateTime.now().plusHours(2));
        when(registroEventos.cercanos(anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.of(ev));
        when(recalculoRepo.contarAccionesDesde(anyLong(), any())).thenReturn(0);
        when(fixedRule.decide(any())).thenReturn(Accion.RECALCULAR_VIGENTE);
        when(fixedRule.version()).thenReturn("RULE-1.0");
        when(grafoProvider.obtenerPorCategoria(any())).thenReturn(null); // sin grafo
        when(navegacionRepo.findById(anyLong())).thenReturn(Optional.of(navegacionFake()));
        when(recalculoRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // No debe lanzar excepción
        var result = service.procesarPosicion(1L, 10L, LAT, LNG, 15.0);

        assertThat(result.huboDisparador()).isTrue();
        assertThat(result.exito()).isFalse();
        assertThat(result.nuevaRuta()).isNull();
    }

    // =========================================================================
    // JSON de estado no contiene coordenadas
    // =========================================================================

    @Test
    void estadoEntrada_noContieneCoords() {
        Arista a = aristaFake(99L, 300.0);
        SesionNavegacion sesion = sesion(ConfiguracionNavegacion.RULE, List.of(a));
        when(sesionStore.obtener(1L, 10L)).thenReturn(Optional.of(sesion));
        when(registroEventos.instantanea())
                .thenReturn(new SnapshotEventos(Set.of(99L)));

        EventoVial ev = new EventoVial();
        ev.setId(1L); ev.setTipo(TipoEventoVial.BLOQUEO_TOTAL);
        ev.setGravedad(1.0); ev.setTramoId(99L);
        ev.setEstado(EstadoEventoVial.ACTIVO);
        ev.setGeom(GF.createPoint(new Coordinate(LNG, LAT)));
        ev.setAutorUsuarioId(1L);
        ev.setCreadoEn(OffsetDateTime.now());
        ev.setExpiraEn(OffsetDateTime.now().plusHours(2));
        when(registroEventos.cercanos(anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.of(ev));
        when(recalculoRepo.contarAccionesDesde(anyLong(), any())).thenReturn(0);
        when(fixedRule.decide(any())).thenReturn(Accion.MANTENER);
        when(fixedRule.version()).thenReturn("RULE-1.0");
        when(grafoProvider.obtenerPorCategoria(any())).thenReturn(null);
        when(navegacionRepo.findById(anyLong())).thenReturn(Optional.of(navegacionFake()));

        final Recalculo[] capturado = {null};
        when(recalculoRepo.save(any(Recalculo.class))).thenAnswer(inv -> {
            capturado[0] = inv.getArgument(0);
            return capturado[0];
        });

        service.procesarPosicion(1L, 10L, LAT, LNG, 15.0);

        assertThat(capturado[0]).isNotNull();
        String json = capturado[0].getEstadoEntrada().toString();
        // No debe contener lat/lng ni coordenadas de la posición
        assertThat(json).doesNotContain(String.valueOf(LAT).substring(0, 6));
        assertThat(json).doesNotContain("lat\":");
        assertThat(json).doesNotContain("lng\":");
        // Debe contener las 14 features
        for (String nombre : EstadoRecalculo.NOMBRES_FEATURES) {
            assertThat(json).contains("\"" + nombre + "\"");
        }
    }

    // =========================================================================
    // Mensajes estáticos (plantillas fijas)
    // =========================================================================

    @Test
    void mensajes_noVacios_enEspanol() {
        assertThat(RecalculoService.mensajeBloqueoRecalculo()).isNotBlank();
        assertThat(RecalculoService.mensajeBloqueoPermanece()).isNotBlank();
        assertThat(RecalculoService.mensajeDesvioRecalculo()).isNotBlank();
        assertThat(RecalculoService.mensajeSinAlternativa()).isNotBlank();
    }
}
