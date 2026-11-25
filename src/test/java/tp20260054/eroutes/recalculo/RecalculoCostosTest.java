package tp20260054.eroutes.recalculo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import tp20260054.eroutes.eventos.RegistroEventosActivos;
import tp20260054.eroutes.mapeado.dto.Coordenada;
import tp20260054.eroutes.navegacion.ConfiguracionNavegacion;
import tp20260054.eroutes.navegacion.DisparadorRecalculo;
import tp20260054.eroutes.navegacion.EtiquetaNavegacion;
import tp20260054.eroutes.navegacion.Navegacion;
import tp20260054.eroutes.navegacion.NavegacionRepository;
import tp20260054.eroutes.navegacion.Recalculo;
import tp20260054.eroutes.navegacion.RecalculoRepository;
import tp20260054.eroutes.routing.costo.CostoMulticriterio;
import tp20260054.eroutes.routing.costo.CostoPresetFactory;
import tp20260054.eroutes.routing.costo.CostoProperties;
import tp20260054.eroutes.routing.costo.CostoTiempo;
import tp20260054.eroutes.routing.costo.PesosPreset;
import tp20260054.eroutes.routing.costo.Preset;
import tp20260054.eroutes.routing.costo.PresetProperties;
import tp20260054.eroutes.routing.costo.ReferenciaRuta;
import tp20260054.eroutes.routing.costo.SnapshotEventos;
import tp20260054.eroutes.routing.dto.ExtremoRutaDto;
import tp20260054.eroutes.routing.dto.MetricasRutaDto;
import tp20260054.eroutes.routing.dto.PerfilResumenDto;
import tp20260054.eroutes.routing.dto.RutaDto;
import tp20260054.eroutes.routing.graph.Arista;
import tp20260054.eroutes.routing.graph.GrafoEnMemoria;
import tp20260054.eroutes.routing.graph.GrafoEnMemoria.AristaRaw;
import tp20260054.eroutes.routing.graph.GrafoEnMemoria.NodoRaw;
import tp20260054.eroutes.routing.graph.GrafoProvider;
import tp20260054.eroutes.routing.service.ReferenciaRutaService;
import tp20260054.eroutes.routing.service.RutaBuilder;
import tp20260054.eroutes.vehiculo.PerfilVehiculo;

/**
 * Costos comparables y recálculo manual de {@link RecalculoService}, con un grafo real
 * pequeño, las funciones de costo reales (CostoPreset, CostoTiempo con demoras por nodo)
 * y dobles (Mockito) para repositorios y servicios. Sin Spring ni base de datos.
 *
 * <pre>
 *   n0 --1-- n1 --2-- n2(destino)        fila norte (ruta directa, semáforo en n1)
 *   |        |        |
 *   3        7        6
 *   |        |        |
 *   s0 --4-- s1 --5-- s2                 fila sur (desvío / ruta larga)
 * </pre>
 */
class RecalculoCostosTest {

    private static final GeometryFactory GF = new GeometryFactory(new PrecisionModel(), 4326);
    private static final double D = 0.0005;          // ~55 m
    private static final double LAT0 = -12.0931;
    private static final double LNG0 = -77.0465;

    // osmId de los nodos
    private static final long N0 = 0, N1 = 1, N2 = 2, S0 = 10, S1 = 11, S2 = 12;

    private GrafoEnMemoria grafo;
    private SesionNavegacionStore sesionStore;
    private RegistroEventosActivos registroEventos;
    private RecalculoRepository recalculoRepo;
    private NavegacionRepository navegacionRepo;
    private GrafoProvider grafoProvider;
    private FixedRulePolicy fixedRule;
    private RecalculoService service;
    private final List<Recalculo> guardados = new ArrayList<>();

    // -------------------------------------------------------------------------
    @BeforeEach
    void setup() {
        grafo = construirGrafo();

        sesionStore = mock(SesionNavegacionStore.class);
        registroEventos = mock(RegistroEventosActivos.class);
        recalculoRepo = mock(RecalculoRepository.class);
        navegacionRepo = mock(NavegacionRepository.class);
        grafoProvider = mock(GrafoProvider.class);
        fixedRule = mock(FixedRulePolicy.class);
        ReferenciaRutaService referenciaService = mock(ReferenciaRutaService.class);
        PresetProperties presetProperties = mock(PresetProperties.class);
        RutaBuilder rutaBuilder = mock(RutaBuilder.class, inv -> rutaFake());

        when(grafoProvider.obtenerPorCategoria(any())).thenReturn(grafo);
        when(registroEventos.instantanea()).thenReturn(SnapshotEventos.VACIO);
        when(registroEventos.cercanos(anyDouble(), anyDouble(), anyDouble())).thenReturn(List.of());
        when(recalculoRepo.contarAccionesDesde(anyLong(), any())).thenReturn(0);
        when(recalculoRepo.save(any(Recalculo.class))).thenAnswer(inv -> {
            Recalculo r = inv.getArgument(0);
            guardados.add(r);
            return r;
        });
        when(navegacionRepo.findById(anyLong())).thenReturn(Optional.of(navegacionFake()));
        when(referenciaService.calcular(any(), anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(new ReferenciaRuta(120.0, 120.0, 0));
        when(presetProperties.de(any())).thenReturn(new PesosPreset(0.55, 0.15, 0.10, 0.10, 0.10));
        when(fixedRule.decide(any())).thenReturn(Accion.RECALCULAR_VIGENTE);
        when(fixedRule.version()).thenReturn("RULE-1.0");

        CostoMulticriterio multicriterio = multicriterio();
        RecalculoProperties props = new RecalculoProperties(
                30, 2, 120, 500, 15, 1000, 300, 5, 1000.0, 5000.0,
                0.05, "AI", true, new RecalculoProperties.Modelo(""));

        service = new RecalculoService(
                sesionStore, registroEventos, recalculoRepo, navegacionRepo,
                grafoProvider, referenciaService, new CostoPresetFactory(multicriterio),
                new CostoTiempo(multicriterio), presetProperties, rutaBuilder, props,
                new DetectorDesvio(30, 2), mock(DQLPolicyConRespaldo.class), fixedRule,
                mock(ReferencePolicy.class), new com.fasterxml.jackson.databind.ObjectMapper());
        try {
            var f1 = RecalculoService.class.getDeclaredField("maxSnapM");
            f1.setAccessible(true);
            f1.setInt(service, 200);
            var f2 = RecalculoService.class.getDeclaredField("margenZonaM");
            f2.setAccessible(true);
            f2.setDouble(service, 1500.0);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // =========================================================================
    // 1) Costos comparables
    // =========================================================================

    @Test
    void rutaQueNoCambia_costoAnteriorIgualCostoNuevo() {
        SesionNavegacion sesion = sesion(ConfiguracionNavegacion.RULE, rutaDirecta());
        when(sesionStore.obtener(1L, 10L)).thenReturn(Optional.of(sesion));

        // Posición en el origen de la ruta, que ya es la óptima
        var r = service.recalcularManual(1L, 10L, LAT0, LNG0, 20.0);

        assertThat(r.exito()).isTrue();
        Recalculo reg = unicoRegistro();
        assertThat(reg.getCostoAnterior()).isNotNull();
        assertThat(reg.getCostoNuevo()).isNotNull();
        assertThat(reg.getCostoAnterior()).isCloseTo(reg.getCostoNuevo(), within(1e-9));
        // Misma comparación en segundos (CostoTiempo con demora del semáforo en n1)
        assertThat(reg.getCostoAnteriorTiempoS()).isGreaterThan(0.0);
        assertThat(reg.getCostoAnteriorTiempoS())
                .isCloseTo(reg.getCostoNuevoTiempoS(), within(1e-9));
        assertThat(reg.getRutaAnteriorBloqueada()).isFalse();
    }

    @Test
    void demorasPorNodo_seIncluyenEnElTiempoDeAmbasRutas() {
        // La ruta directa pasa por el semáforo de n1 (15 s). Sin la demora, el tiempo sería menor.
        SesionNavegacion sesion = sesion(ConfiguracionNavegacion.RULE, rutaDirecta());
        when(sesionStore.obtener(1L, 10L)).thenReturn(Optional.of(sesion));

        service.recalcularManual(1L, 10L, LAT0, LNG0, 20.0);

        CostoMulticriterio m = multicriterio();
        double sinDemora = 0.0;
        for (Arista a : rutaDirecta()) {
            sinDemora += m.tiempoAristaS(a, null, 45.0, false);
        }
        assertThat(unicoRegistro().getCostoNuevoTiempoS()).isGreaterThan(sinDemora + 14.9);
    }

    @Test
    void rutaAnteriorMasLarga_costoNuevoEsMenorEstricto_yReemplazaLaRuta() {
        SesionNavegacion sesion = sesion(ConfiguracionNavegacion.RULE, rutaLarga());
        when(sesionStore.obtener(1L, 10L)).thenReturn(Optional.of(sesion));

        var r = service.recalcularManual(1L, 10L, LAT0, LNG0, 20.0);

        assertThat(r.exito()).isTrue();
        Recalculo reg = unicoRegistro();
        assertThat(reg.getCostoNuevo()).isLessThan(reg.getCostoAnterior());
        assertThat(reg.getCostoNuevoTiempoS()).isLessThan(reg.getCostoAnteriorTiempoS());
        assertThat(sesion.aristasRuta()).extracting(Arista::tramoId).containsExactly(1L, 2L);
    }

    @Test
    void desvio_costoNuevoMenorOIgualQueElAnteriorRecalculadoDesdeLaPosicion() {
        SesionNavegacion sesion = sesion(ConfiguracionNavegacion.RULE, rutaDirecta());
        when(sesionStore.obtener(1L, 10L)).thenReturn(Optional.of(sesion));

        // El vehículo está en s1 (~55 m al sur de la ruta): dos muestras desviadas → REROUTE
        double latS1 = LAT0 - D;
        double lngS1 = LNG0 + D;
        var primera = service.procesarPosicion(1L, 10L, latS1, lngS1, 20.0);
        assertThat(primera.huboDisparador()).isFalse();
        var segunda = service.procesarPosicion(1L, 10L, latS1, lngS1, 20.0);

        assertThat(segunda.huboDisparador()).isTrue();
        assertThat(segunda.disparador()).isEqualTo(DisparadorRecalculo.REROUTE);
        Recalculo reg = unicoRegistro();
        assertThat(reg.getCostoAnterior()).isNotNull();
        assertThat(reg.getCostoNuevo()).isLessThanOrEqualTo(reg.getCostoAnterior() + 1e-9);
        assertThat(reg.getCostoNuevoTiempoS()).isNotNull();
        assertThat(reg.getCostoAnteriorTiempoS()).isNotNull();
    }

    @Test
    void rutaAnteriorBloqueada_costoAnteriorNull_yBanderaActiva() {
        SesionNavegacion sesion = sesion(ConfiguracionNavegacion.RULE, rutaDirecta());
        when(sesionStore.obtener(1L, 10L)).thenReturn(Optional.of(sesion));
        when(registroEventos.instantanea()).thenReturn(new SnapshotEventos(Set.of(2L))); // n1-n2

        var r = service.recalcularManual(1L, 10L, LAT0, LNG0, 20.0);

        assertThat(r.exito()).isTrue();
        Recalculo reg = unicoRegistro();
        assertThat(reg.getCostoAnterior()).isNull();
        assertThat(reg.getCostoAnteriorTiempoS()).isNull();
        assertThat(reg.getRutaAnteriorBloqueada()).isTrue();
        assertThat(reg.getCostoNuevo()).isNotNull();
    }

    @Test
    void configuracionReference_usaCostoTiempoEnAmbasRutas() {
        SesionNavegacion sesion = sesion(ConfiguracionNavegacion.REFERENCE, rutaDirecta());
        when(sesionStore.obtener(1L, 10L)).thenReturn(Optional.of(sesion));

        service.recalcularManual(1L, 10L, LAT0, LNG0, 20.0);

        Recalculo reg = unicoRegistro();
        // Con REFERENCE el costo ES el tiempo (CostoTiempo): coincide con los segundos
        assertThat(reg.getCostoAnterior()).isCloseTo(reg.getCostoAnteriorTiempoS(), within(1e-9));
        assertThat(reg.getCostoNuevo()).isCloseTo(reg.getCostoNuevoTiempoS(), within(1e-9));
        assertThat(reg.getCostoAnterior()).isCloseTo(reg.getCostoNuevo(), within(1e-9));
    }

    // =========================================================================
    // 2) Recálculo manual registrado
    // =========================================================================

    @Test
    void manual_seRegistraConDisparadorManual_yLosMismosCamposQueElAutomatico() {
        SesionNavegacion sesion = sesion(ConfiguracionNavegacion.RULE, rutaLarga());
        when(sesionStore.obtener(1L, 10L)).thenReturn(Optional.of(sesion));

        service.recalcularManual(1L, 10L, LAT0, LNG0, 22.5);

        Recalculo reg = unicoRegistro();
        assertThat(reg.getDisparador()).isEqualTo(DisparadorRecalculo.MANUAL);
        assertThat(reg.getAccion()).isEqualTo((short) Accion.RECALCULAR_VIGENTE.codigo());
        assertThat(reg.getConfiguracion()).isEqualTo("RULE");
        assertThat(reg.getPolicyVersion()).isEqualTo(RecalculoService.POLITICA_MANUAL);
        assertThat(reg.isUsoRespaldo()).isFalse();
        assertThat(reg.getMotivoRespaldo()).isNull();
        assertThat(reg.isExito()).isTrue();
        assertThat(reg.getNavegacion()).isNotNull();
        assertThat(reg.getCreadoEn()).isNotNull();
        assertThat(reg.getTiempoInferenciaMs()).isNotNull();
        assertThat(reg.getTiempoCalculoMs()).isNotNull();
        assertThat(reg.getTiempoTotalServidorMs()).isNotNull();
        assertThat(reg.getInnecesario()).isNotNull();

        // estado_entrada: 14 features + crudos, sin coordenadas; velocidad_rel = 22.5 / 45 = 0.5
        String json = reg.getEstadoEntrada().toString();
        for (String nombre : EstadoRecalculo.NOMBRES_FEATURES) {
            assertThat(json).contains("\"" + nombre + "\"");
        }
        assertThat(json).doesNotContain("lat\":").doesNotContain("lng\":");
        assertThat(reg.getEstadoEntrada().get("velocidad_rel").asDouble()).isEqualTo(0.5);
    }

    @Test
    void manual_noConsultaLaPolitica_niAplicaAntirrebote() {
        SesionNavegacion sesion = sesion(ConfiguracionNavegacion.RULE, rutaLarga());
        // Decisión automática reciente con el mismo conjunto de disparadores: no debe afectar
        sesion.registrarDecision(1 << DisparadorRecalculo.MANUAL.ordinal());
        when(sesionStore.obtener(1L, 10L)).thenReturn(Optional.of(sesion));

        service.recalcularManual(1L, 10L, LAT0, LNG0, 20.0);

        verify(fixedRule, never()).decide(any());
        assertThat(guardados).hasSize(1);
    }

    @Test
    void manual_sinRuta_registraExitoFalse() {
        SesionNavegacion sesion = sesion(ConfiguracionNavegacion.RULE, rutaDirecta());
        when(sesionStore.obtener(1L, 10L)).thenReturn(Optional.of(sesion));
        when(grafoProvider.obtenerPorCategoria(any())).thenReturn(null);

        var r = service.recalcularManual(1L, 10L, LAT0, LNG0, 20.0);

        assertThat(r.exito()).isFalse();
        assertThat(r.nuevaRuta()).isNull();
        Recalculo reg = unicoRegistro();
        assertThat(reg.getDisparador()).isEqualTo(DisparadorRecalculo.MANUAL);
        assertThat(reg.isExito()).isFalse();
        assertThat(reg.getMensaje()).isNotBlank();
        assertThat(sesion.aristasRuta()).hasSize(2); // la ruta activa no cambia
    }

    @Test
    void manual_sesionAjena_lanza404_sinRegistrar() {
        when(sesionStore.obtener(anyLong(), anyLong())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.recalcularManual(1L, 99L, LAT0, LNG0, 20.0))
                .isInstanceOf(NavegacionNoEncontradaException.class);
        verify(recalculoRepo, never()).save(any());
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private Recalculo unicoRegistro() {
        assertThat(guardados).hasSize(1);
        return guardados.get(0);
    }

    private static CostoMulticriterio multicriterio() {
        CostoProperties props = new CostoProperties(
                new CostoProperties.Pesos(1, 1, 1, 0.5, 5),
                new CostoProperties.VelDefecto(50, 50, 40, 30, 30, 20, 20, 30, 10),
                new CostoProperties.Exposicion(1.0, 0.8, 0.5, 0.35, 0.25, 0.1, 0.2, 0.5),
                0.75,
                new CostoProperties.DemoraNode(15, 5, 2, 1));
        return new CostoMulticriterio(props);
    }

    private static double[] pos(long osmId) {
        return switch ((int) osmId) {
            case 0 -> new double[]{LAT0, LNG0};
            case 1 -> new double[]{LAT0, LNG0 + D};
            case 2 -> new double[]{LAT0, LNG0 + 2 * D};
            case 10 -> new double[]{LAT0 - D, LNG0};
            case 11 -> new double[]{LAT0 - D, LNG0 + D};
            case 12 -> new double[]{LAT0 - D, LNG0 + 2 * D};
            default -> throw new IllegalArgumentException("nodo " + osmId);
        };
    }

    private static AristaRaw arista(long a, long b, long tramoId) {
        return new AristaRaw(a, b, tramoId, null, "residential", 60.0, null, true, false, false,
                new double[][]{pos(a), pos(b)});
    }

    private static GrafoEnMemoria construirGrafo() {
        List<NodoRaw> nodos = List.of(
                new NodoRaw(N0, pos(N0)[0], pos(N0)[1], null),
                new NodoRaw(N1, pos(N1)[0], pos(N1)[1], "traffic_signals"),
                new NodoRaw(N2, pos(N2)[0], pos(N2)[1], null),
                new NodoRaw(S0, pos(S0)[0], pos(S0)[1], null),
                new NodoRaw(S1, pos(S1)[0], pos(S1)[1], null),
                new NodoRaw(S2, pos(S2)[0], pos(S2)[1], null));
        List<AristaRaw> aristas = List.of(
                arista(N0, N1, 1L), arista(N1, N2, 2L), arista(N0, S0, 3L),
                arista(S0, S1, 4L), arista(S1, S2, 5L), arista(S2, N2, 6L),
                arista(S1, N1, 7L));
        return GrafoEnMemoria.construir(nodos, aristas, Set.of());
    }

    private Arista ar(long desdeOsm, long tramoId) {
        int idx = grafo.indice(desdeOsm);
        return grafo.adyacentes(idx).stream()
                .filter(a -> a.tramoId() == tramoId).findFirst().orElseThrow();
    }

    /** n0 → n1 → n2 (la óptima). */
    private List<Arista> rutaDirecta() {
        return List.of(ar(N0, 1L), ar(N1, 2L));
    }

    /** n0 → s0 → s1 → s2 → n2 (mucho más larga que la directa). */
    private List<Arista> rutaLarga() {
        return List.of(ar(N0, 3L), ar(S0, 4L), ar(S1, 5L), ar(S2, 6L));
    }

    private SesionNavegacion sesion(ConfiguracionNavegacion config, List<Arista> aristas) {
        PerfilVehiculo perfil = new PerfilVehiculo("M", "BICIMOTO", 45,
                "AUTOMOTOR_MENOR_L", false, List.of());
        return new SesionNavegacion(1L, 10L, perfil, config, Preset.RAPIDA, aristas);
    }

    private Navegacion navegacionFake() {
        Navegacion nav = new Navegacion();
        nav.setId(1L);
        nav.setUsuarioId(10L);
        nav.setConfiguracion(ConfiguracionNavegacion.RULE);
        nav.setEtiqueta(EtiquetaNavegacion.RAPIDA);
        nav.setDistanciaM(120);
        nav.setTiempoEstimadoS(60);
        nav.setCreadoEn(OffsetDateTime.now());
        nav.setOrigen(GF.createPoint(new Coordinate(pos(N0)[1], pos(N0)[0])));
        nav.setDestino(GF.createPoint(new Coordinate(pos(N2)[1], pos(N2)[0])));
        nav.setPesosUsados(new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode());
        return nav;
    }

    private static RutaDto rutaFake() {
        Coordenada c = new Coordenada(LAT0, LNG0);
        return new RutaDto("RULE",
                new PerfilResumenDto("M", "BICIMOTO", 45),
                new ExtremoRutaDto(c, c, 0), new ExtremoRutaDto(c, c, 0),
                120.0, 60.0,
                new MetricasRutaDto(0.3, 0, 0, 0, 5.0),
                List.of(c), List.of());
    }
}
