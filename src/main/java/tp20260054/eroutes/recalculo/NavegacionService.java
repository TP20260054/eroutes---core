package tp20260054.eroutes.recalculo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tp20260054.eroutes.mapeado.exception.RecursoNoEncontradoException;
import tp20260054.eroutes.navegacion.ConfiguracionNavegacion;
import tp20260054.eroutes.navegacion.EtiquetaNavegacion;
import tp20260054.eroutes.navegacion.Navegacion;
import tp20260054.eroutes.navegacion.NavegacionRepository;
import tp20260054.eroutes.recalculo.dto.NavegacionIniciadaDto;
import tp20260054.eroutes.recalculo.dto.RecalculoManualDto;
import tp20260054.eroutes.routing.algorithm.AStar;
import tp20260054.eroutes.routing.algorithm.HeuristicaMedible;
import tp20260054.eroutes.routing.algorithm.ResultadoDijkstra;
import tp20260054.eroutes.routing.algorithm.Zona;
import tp20260054.eroutes.routing.costo.CostoPreset;
import tp20260054.eroutes.routing.costo.CostoPresetFactory;
import tp20260054.eroutes.routing.costo.Preset;
import tp20260054.eroutes.routing.costo.PresetProperties;
import tp20260054.eroutes.routing.costo.ReferenciaRuta;
import tp20260054.eroutes.routing.costo.SnapshotEventos;
import tp20260054.eroutes.routing.dto.RutaDto;
import tp20260054.eroutes.routing.graph.GrafoEnMemoria;
import tp20260054.eroutes.routing.graph.GrafoProvider;
import tp20260054.eroutes.routing.graph.NodoInfo;
import tp20260054.eroutes.routing.service.GrafoNoDisponibleException;
import tp20260054.eroutes.routing.service.MismoNodoException;
import tp20260054.eroutes.routing.service.PuntoFueraDeCoberturaException;
import tp20260054.eroutes.routing.service.ReferenciaRutaService;
import tp20260054.eroutes.routing.service.RutaBuilder;
import tp20260054.eroutes.routing.service.SinCaminoException;
import tp20260054.eroutes.usuarios.UsuarioRepository;
import tp20260054.eroutes.usuarios.Vehiculo;
import tp20260054.eroutes.usuarios.VehiculoMapper;
import tp20260054.eroutes.usuarios.VehiculoRepository;
import tp20260054.eroutes.eventos.RegistroEventosActivos;
import tp20260054.eroutes.vehiculo.PerfilVehiculo;

/**
 * Lógica de negocio del inicio de navegación y del recálculo manual.
 *
 * <p>Responsabilidades:
 * <ul>
 *   <li>Resolver perfil de vehículo (mismo patrón que {@link tp20260054.eroutes.routing.service.RuteoService}).
 *   <li>Calcular la ruta inicial con A* + {@link CostoPreset}.
 *   <li>Persistir {@link Navegacion} y crear la {@link SesionNavegacion} en memoria.
 *   <li>Gestionar el recálculo manual (fuerza acción 1 sin consultar la política).
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class NavegacionService {

    private static final GeometryFactory GF = new GeometryFactory(new PrecisionModel(), 4326);

    private final UsuarioRepository usuarioRepo;
    private final VehiculoRepository vehiculoRepo;
    private final NavegacionRepository navegacionRepo;
    private final GrafoProvider grafoProvider;
    private final ReferenciaRutaService referenciaService;
    private final CostoPresetFactory costoPresetFactory;
    private final PresetProperties presetProps;
    private final RutaBuilder rutaBuilder;
    private final SesionNavegacionStore sesionStore;
    private final RegistroEventosActivos registroEventos;
    private final RecalculoProperties recalculoProps;
    private final ObjectMapper objectMapper;

    @Value("${eroutes.routing.max-distancia-snap-m:200}")
    private int maxSnapM;

    @Value("${eroutes.routing.a-estrella.margen-zona-m:1500}")
    private double margenZonaM;

    // =========================================================================
    // Inicio de navegación
    // =========================================================================

    @Transactional
    public NavegacionIniciadaDto iniciar(Long usuarioId,
                                         double latOrigen, double lngOrigen,
                                         double latDestino, double lngDestino,
                                         Preset presetSolicitado,
                                         Long vehiculoId,
                                         ConfiguracionNavegacion configSolicitada) {

        PerfilVehiculo perfil = resolverPerfil(usuarioId, vehiculoId);
        Preset preset = presetSolicitado != null ? presetSolicitado : Preset.RAPIDA;

        ConfiguracionNavegacion config = configSolicitada != null
                ? configSolicitada
                : ConfiguracionNavegacion.valueOf(
                        recalculoProps.configuracionDefecto().toUpperCase());

        SnapshotEventos snapshot = registroEventos.instantanea();

        // Referencia y ruta inicial
        ReferenciaRuta referencia = referenciaService.calcular(
                perfil, latOrigen, lngOrigen, latDestino, lngDestino);

        GrafoEnMemoria grafo = grafoProvider.obtenerPorCategoria(
                perfil.categoriaReglamentaria());
        if (grafo == null) {
            throw new GrafoNoDisponibleException("El grafo de ruteo no está disponible aún");
        }

        int[] snapO = grafo.nodoMasCercano(latOrigen, lngOrigen);
        int[] snapD = grafo.nodoMasCercano(latDestino, lngDestino);
        validarSnap(snapO[1], snapD[1]);
        if (snapO[0] == snapD[0]) {
            throw new MismoNodoException("El origen y el destino coinciden en el mismo nodo");
        }

        CostoPreset funcion = costoPresetFactory.crear(
                presetProps.de(preset), referencia, perfil, snapshot);

        NodoInfo nDest = grafo.nodo(snapD[0]);
        var heuristica = HeuristicaMedible.crear(
                nDest.lat(), nDest.lng(),
                perfil.velocidadMaximaKmh(), referencia, presetProps.de(preset));

        Zona zona = Zona.alrededor(latOrigen, lngOrigen, latDestino, lngDestino, margenZonaM);
        ResultadoDijkstra resultado = AStar.calcular(
                grafo, funcion, heuristica, snapO[0], snapD[0], zona);

        if (!resultado.tieneCamino()) {
            throw new SinCaminoException(
                    "No existe camino entre el origen y el destino indicados");
        }

        RutaDto rutaDto = rutaBuilder.construir(resultado, grafo, perfil, preset.name(),
                latOrigen, lngOrigen, snapO[1],
                latDestino, lngDestino, snapD[1],
                snapO[0], snapD[0]);

        // Serializar pesos_usados como JSONB
        com.fasterxml.jackson.databind.JsonNode pesosJson = serializar(presetProps.de(preset));

        // Persistir Navegacion
        Navegacion nav = new Navegacion();
        nav.setUsuarioId(usuarioId);
        nav.setVehiculoId(resolverVehiculoId(usuarioId, vehiculoId));
        nav.setOrigen(GF.createPoint(new Coordinate(lngOrigen, latOrigen)));
        nav.setDestino(GF.createPoint(new Coordinate(lngDestino, latDestino)));
        nav.setEtiqueta(presetAEtiqueta(preset));
        nav.setDistanciaM(rutaDto.distanciaM());
        nav.setTiempoEstimadoS(rutaDto.tiempoEstimadoS());
        nav.setPesosUsados(pesosJson);
        nav.setConfiguracion(config);
        nav.setCreadoEn(OffsetDateTime.now(ZoneOffset.UTC));
        nav = navegacionRepo.save(nav);

        // Crear sesión en memoria
        SesionNavegacion sesion = new SesionNavegacion(
                nav.getId(), usuarioId, perfil, config, preset,
                resultado.aristas());
        sesionStore.guardar(sesion);

        return new NavegacionIniciadaDto(nav.getId(), rutaDto, preset, pesosJson, config);
    }

    // =========================================================================
    // Recálculo manual
    // =========================================================================

    @Transactional
    public RecalculoManualDto recalcularManual(Long navegacionId, Long usuarioId,
                                               double latActual, double lngActual) {
        SesionNavegacion sesion = sesionStore.obtener(navegacionId, usuarioId)
                .orElseThrow(() -> new NavegacionNoEncontradaException(
                        "Navegación " + navegacionId + " no encontrada o no accesible"));

        Navegacion nav = navegacionRepo.findById(navegacionId)
                .orElseThrow(() -> new NavegacionNoEncontradaException(
                        "Navegación " + navegacionId + " no encontrada en BD"));

        double latDestino = nav.getDestino().getY();
        double lngDestino = nav.getDestino().getX();
        PerfilVehiculo perfil = sesion.perfil();
        Preset preset = sesion.preset();

        SnapshotEventos snapshot = registroEventos.instantanea();

        ReferenciaRuta referencia;
        try {
            referencia = referenciaService.calcular(
                    perfil, latActual, lngActual, latDestino, lngDestino);
        } catch (SinCaminoException | MismoNodoException | PuntoFueraDeCoberturaException e) {
            return new RecalculoManualDto(false, e.getMessage(), null);
        }

        GrafoEnMemoria grafo = grafoProvider.obtenerPorCategoria(
                perfil.categoriaReglamentaria());
        if (grafo == null) {
            return new RecalculoManualDto(false, "El grafo no está disponible", null);
        }

        int[] snapActual = grafo.nodoMasCercano(latActual, lngActual);
        int[] snapDest   = grafo.nodoMasCercano(latDestino, lngDestino);
        if (snapActual[1] > maxSnapM) {
            return new RecalculoManualDto(false, "Posición fuera de cobertura", null);
        }
        if (snapActual[0] == snapDest[0]) {
            return new RecalculoManualDto(false,
                    "La posición actual coincide con el destino", null);
        }

        CostoPreset funcion = costoPresetFactory.crear(
                presetProps.de(preset), referencia, perfil, snapshot);

        NodoInfo nDest = grafo.nodo(snapDest[0]);
        var heuristica = HeuristicaMedible.crear(
                nDest.lat(), nDest.lng(),
                perfil.velocidadMaximaKmh(), referencia, presetProps.de(preset));

        Zona zona = Zona.alrededor(latActual, lngActual, latDestino, lngDestino, margenZonaM);
        ResultadoDijkstra resultado = AStar.calcular(
                grafo, funcion, heuristica, snapActual[0], snapDest[0], zona);

        if (!resultado.tieneCamino()) {
            return new RecalculoManualDto(false,
                    RecalculoService.mensajeSinAlternativa(), null);
        }

        RutaDto rutaDto = rutaBuilder.construir(resultado, grafo, perfil, preset.name(),
                latActual, lngActual, snapActual[1],
                latDestino, lngDestino, snapDest[1],
                snapActual[0], snapDest[0]);

        sesion.reemplazarRuta(resultado.aristas());
        return new RecalculoManualDto(true, null, rutaDto);
    }

    // =========================================================================
    // Helpers privados
    // =========================================================================

    private PerfilVehiculo resolverPerfil(Long usuarioId, Long vehiculoId) {
        var u = usuarioRepo.findByIdConVehiculoActivo(usuarioId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado"));
        Vehiculo v;
        if (vehiculoId != null) {
            v = vehiculoRepo.findByIdAndUsuarioId(vehiculoId, usuarioId)
                    .orElseThrow(() -> new RecursoNoEncontradoException("Vehículo no encontrado"));
        } else {
            v = u.getVehiculoActivo();
            if (v == null) {
                throw new PuntoFueraDeCoberturaException(
                        "Registra un vehículo para iniciar la navegación");
            }
        }
        return VehiculoMapper.toPerfilVehiculo(v);
    }

    private Long resolverVehiculoId(Long usuarioId, Long vehiculoId) {
        if (vehiculoId != null) return vehiculoId;
        var u = usuarioRepo.findByIdConVehiculoActivo(usuarioId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado"));
        return u.getVehiculoActivo() != null ? u.getVehiculoActivo().getId() : null;
    }

    private void validarSnap(int distO, int distD) {
        if (distO > maxSnapM) {
            throw new PuntoFueraDeCoberturaException(
                    "El origen está a " + distO + " m del nodo más cercano (máximo "
                    + maxSnapM + " m)");
        }
        if (distD > maxSnapM) {
            throw new PuntoFueraDeCoberturaException(
                    "El destino está a " + distD + " m del nodo más cercano (máximo "
                    + maxSnapM + " m)");
        }
    }

    private EtiquetaNavegacion presetAEtiqueta(Preset p) {
        return switch (p) {
            case RAPIDA -> EtiquetaNavegacion.RAPIDA;
            case CORTA  -> EtiquetaNavegacion.CORTA;
            case SEGURA -> EtiquetaNavegacion.SEGURA;
        };
    }

    private com.fasterxml.jackson.databind.JsonNode serializar(
            tp20260054.eroutes.routing.costo.PesosPreset pesos) {
        ObjectNode n = objectMapper.createObjectNode();
        n.put("tiempo",     pesos.tiempo());
        n.put("distancia",  pesos.distancia());
        n.put("exposicion", pesos.exposicion());
        n.put("noValidado", pesos.noValidado());
        n.put("cruce",      pesos.cruce());
        return n;
    }
}
