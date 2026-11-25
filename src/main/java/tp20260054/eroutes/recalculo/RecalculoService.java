package tp20260054.eroutes.recalculo;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.RequiredArgsConstructor;
import tp20260054.eroutes.eventos.EventoVial;
import tp20260054.eroutes.eventos.RegistroEventosActivos;
import tp20260054.eroutes.eventos.TipoEventoVial;
import tp20260054.eroutes.navegacion.ConfiguracionNavegacion;
import tp20260054.eroutes.navegacion.DisparadorRecalculo;
import tp20260054.eroutes.navegacion.Navegacion;
import tp20260054.eroutes.navegacion.NavegacionRepository;
import tp20260054.eroutes.navegacion.Recalculo;
import tp20260054.eroutes.navegacion.RecalculoRepository;
import tp20260054.eroutes.routing.algorithm.AStar;
import tp20260054.eroutes.routing.algorithm.Heuristica;
import tp20260054.eroutes.routing.algorithm.HeuristicaMedible;
import tp20260054.eroutes.routing.algorithm.ResultadoDijkstra;
import tp20260054.eroutes.routing.algorithm.Zona;
import tp20260054.eroutes.routing.costo.ContextoNodo;
import tp20260054.eroutes.routing.costo.CostoPresetFactory;
import tp20260054.eroutes.routing.costo.CostoTiempo;
import tp20260054.eroutes.routing.costo.FuncionCosto;
import tp20260054.eroutes.routing.costo.Preset;
import tp20260054.eroutes.routing.costo.PresetProperties;
import tp20260054.eroutes.routing.costo.ReferenciaRuta;
import tp20260054.eroutes.routing.costo.SnapshotEventos;
import tp20260054.eroutes.routing.dto.RutaDto;
import tp20260054.eroutes.routing.graph.Arista;
import tp20260054.eroutes.routing.graph.GrafoEnMemoria;
import tp20260054.eroutes.routing.graph.GrafoProvider;
import tp20260054.eroutes.routing.graph.NodoInfo;
import tp20260054.eroutes.routing.service.MismoNodoException;
import tp20260054.eroutes.routing.service.PuntoFueraDeCoberturaException;
import tp20260054.eroutes.routing.service.ReferenciaRutaService;
import tp20260054.eroutes.routing.service.RutaBuilder;
import tp20260054.eroutes.routing.service.SinCaminoException;

/**
 * Orquesta el recálculo adaptativo para cada muestra de posición recibida.
 *
 * <p>Flujo por muestra (pasos 1–8 del bloque G del prompt):
 * <ol>
 *   <li>Cargar sesión; actualizar progreso, desvío y velocidad.
 *   <li>Snapshot de eventos; obtener eventos aledaños.
 *   <li>Determinar disparador; sin disparador → solo eventos aledaños sin registro.
 *       Aplicar antirrebote (D12).
 *   <li>Construir {@link EstadoRecalculo}; decidir con la política; medir tiempos.
 *   <li>Ejecutar acción (0 / 1 / 2).
 *   <li>Registrar en {@code recalculos}.
 *   <li>Sin ruta alternativa → {@code exito=false}, sin lanzar error HTTP.
 *   <li>Reemplazar ruta activa solo si hubo nueva ruta.
 * </ol>
 *
 * <p>Privacidad (RNF-03/06): las coordenadas de las muestras de posición nunca
 * se persisten ni se loguean. Solo se persisten variables derivadas.
 */
@Service
@RequiredArgsConstructor
public class RecalculoService {

    private static final Logger log = LoggerFactory.getLogger(RecalculoService.class);

    /** Versión de política registrada en {@code recalculos.policy_version} para recálculos manuales. */
    static final String POLITICA_MANUAL = "MANUAL-1.0";

    // ---- Colaboradores ----
    private final SesionNavegacionStore sesionStore;
    private final RegistroEventosActivos registroEventos;
    private final RecalculoRepository recalculoRepo;
    private final NavegacionRepository navegacionRepo;
    private final GrafoProvider grafoProvider;
    private final ReferenciaRutaService referenciaService;
    private final CostoPresetFactory costoPresetFactory;
    private final CostoTiempo costoTiempo;
    private final PresetProperties presetProperties;
    private final RutaBuilder rutaBuilder;
    private final RecalculoProperties props;
    private final DetectorDesvio detectorDesvio;
    private final DQLPolicyConRespaldo dqlPolicy;
    private final FixedRulePolicy fixedRulePolicy;
    private final ReferencePolicy referencePolicy;
    private final ObjectMapper objectMapper;

    @Value("${eroutes.routing.max-distancia-snap-m:200}")
    private int maxSnapM;

    @Value("${eroutes.routing.a-estrella.margen-zona-m:1500}")
    private double margenZonaM;

    // =========================================================================
    // API pública
    // =========================================================================

    /**
     * Procesa una muestra de posición y devuelve el resultado del recálculo.
     *
     * @param navegacionId  id de la navegación activa
     * @param usuarioId     id del usuario (del JWT); necesario para verificar propietario
     * @param lat           latitud de la muestra (NO SE PERSISTE)
     * @param lng           longitud de la muestra (NO SE PERSISTE)
     * @param velocidadKmh  velocidad en km/h informada por el cliente
     * @return resultado con eventos aledaños y, si hubo disparador, el recálculo
     * @throws NavegacionNoEncontradaException si la sesión no existe o es de otro usuario
     */
    @Transactional
    public ResultadoPosicion procesarPosicion(long navegacionId, long usuarioId,
                                              double lat, double lng, double velocidadKmh) {
        long tInicio = System.currentTimeMillis();

        // ----- Paso 1: cargar sesión y actualizar progreso -----
        SesionNavegacion sesion = sesionStore.obtener(navegacionId, usuarioId)
                .orElseThrow(() -> new NavegacionNoEncontradaException(
                        "Navegación " + navegacionId + " no encontrada o no accesible"));

        boolean desvioAlcanzado;
        synchronized (sesion) {
            desvioAlcanzado = detectorDesvio.procesar(
                    sesion.estadoDesvio(), lat, lng);
        }

        // ----- Paso 2: snapshot de eventos y aledaños -----
        SnapshotEventos snapshot = registroEventos.instantanea();
        List<EventoVial> aledanos = registroEventos.cercanos(lat, lng, props.umbralBloqueoM());

        // ----- Paso 3: determinar disparador -----
        // Evento bloqueante más cercano
        EventoVial eventoRelevante = seleccionarEventoRelevante(
                aledanos, sesion.aristasRuta(), sesion.estadoDesvio().indiceArista(), lat, lng);

        DisparadorRecalculo disparador = determinarDisparador(
                desvioAlcanzado, eventoRelevante != null);

        if (disparador == null) {
            return ResultadoPosicion.soloAledanos(aledanos);
        }

        // Antirrebote (D12)
        int bitmaskActual = bitmask(disparador);
        if (estaEnCooldown(sesion, bitmaskActual)) {
            return ResultadoPosicion.soloAledanos(aledanos);
        }

        // ----- Paso 4: construir EstadoRecalculo y decidir -----
        int recalculosRecientes = recalculoRepo.contarAccionesDesde(
                navegacionId,
                OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(props.ventanaRecalculosS()));

        EstadoRecalculo estado = construirEstado(
                sesion, disparador, desvioAlcanzado, eventoRelevante,
                lat, lng, velocidadKmh, recalculosRecientes);

        RecalculationPolicy politica = seleccionarPolitica(sesion.configuracion());
        DecisionConMetadatos decision;
        long tInicioInf = System.currentTimeMillis();
        if (politica instanceof DQLPolicyConRespaldo dql) {
            decision = dql.decidirConMetadatos(estado);
        } else {
            Accion a = politica.decide(estado);
            decision = new DecisionConMetadatos(a, false, null, politica.version(),
                    System.currentTimeMillis() - tInicioInf);
        }

        sesion.registrarDecision(bitmaskActual);

        return ejecutarYRegistrar(sesion, snapshot, aledanos, estado, decision, lat, lng, tInicio);
    }

    /**
     * Recálculo manual: fuerza {@link Accion#RECALCULAR_VIGENTE} sin consultar la política
     * (sin antirrebote) y lo registra en {@code recalculos} con disparador
     * {@link DisparadorRecalculo#MANUAL}, con los mismos campos que el recálculo automático.
     *
     * @param velocidadKmh velocidad informada por el cliente (alimenta la feature velocidad_rel)
     * @throws NavegacionNoEncontradaException si la sesión no existe o es de otro usuario
     */
    @Transactional
    public ResultadoPosicion recalcularManual(long navegacionId, long usuarioId,
                                              double lat, double lng, double velocidadKmh) {
        long tInicio = System.currentTimeMillis();

        SesionNavegacion sesion = sesionStore.obtener(navegacionId, usuarioId)
                .orElseThrow(() -> new NavegacionNoEncontradaException(
                        "Navegación " + navegacionId + " no encontrada o no accesible"));

        boolean desvio;
        synchronized (sesion) {
            desvio = detectorDesvio.procesar(sesion.estadoDesvio(), lat, lng);
        }

        SnapshotEventos snapshot = registroEventos.instantanea();
        List<EventoVial> aledanos = registroEventos.cercanos(lat, lng, props.umbralBloqueoM());
        EventoVial eventoRelevante = seleccionarEventoRelevante(
                aledanos, sesion.aristasRuta(), sesion.estadoDesvio().indiceArista(), lat, lng);

        int recalculosRecientes = recalculoRepo.contarAccionesDesde(
                navegacionId,
                OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(props.ventanaRecalculosS()));

        EstadoRecalculo estado = construirEstado(
                sesion, DisparadorRecalculo.MANUAL, desvio, eventoRelevante,
                lat, lng, velocidadKmh, recalculosRecientes);

        DecisionConMetadatos decision = new DecisionConMetadatos(
                Accion.RECALCULAR_VIGENTE, false, null, POLITICA_MANUAL, 0L);

        return ejecutarYRegistrar(sesion, snapshot, aledanos, estado, decision, lat, lng, tInicio);
    }

    /**
     * Pasos 5 a 8 comunes al recálculo automático y al manual: ejecutar la acción,
     * registrar en {@code recalculos} y reemplazar la ruta activa si hubo una nueva.
     */
    private ResultadoPosicion ejecutarYRegistrar(SesionNavegacion sesion, SnapshotEventos snapshot,
                                                 List<EventoVial> aledanos, EstadoRecalculo estado,
                                                 DecisionConMetadatos decision,
                                                 double lat, double lng, long tInicio) {
        // ----- Paso 5: ejecutar acción -----
        long tInicioCalc = System.currentTimeMillis();
        ResultadoAccion resultado = ejecutarAccion(
                decision.accion(), sesion, snapshot, lat, lng, estado);
        long tFinCalc = System.currentTimeMillis();

        long tTotalServidor = tFinCalc - tInicio;
        long tCalcMs = tFinCalc - tInicioCalc;

        // ----- Paso 6: registrar -----
        Navegacion navegacion = navegacionRepo.findById(sesion.navegacionId())
                .orElseThrow(() -> new NavegacionNoEncontradaException(
                        "Navegación " + sesion.navegacionId() + " no encontrada en BD"));

        registrar(navegacion, decision, estado, estado.disparador(), resultado,
                decision.tiempoInferenciaMs(), tCalcMs, tTotalServidor);

        // ----- Paso 8: reemplazar ruta si hubo nueva ruta -----
        if (resultado.nuevaRuta() != null) {
            sesion.reemplazarRuta(resultado.nuevaRuta());
            detectorDesvio.reiniciar(sesion.estadoDesvio());
        }

        return ResultadoPosicion.conRecalculo(aledanos, decision, resultado,
                tCalcMs, estado.disparador());
    }

    // =========================================================================
    // Paso 3 — disparador y antirrebote
    // =========================================================================

    private DisparadorRecalculo determinarDisparador(boolean desvio, boolean hayEvento) {
        if (hayEvento) return DisparadorRecalculo.BLOCKED; // BLOCKED tiene precedencia
        if (desvio)   return DisparadorRecalculo.REROUTE;
        return null;
    }

    private boolean estaEnCooldown(SesionNavegacion sesion, int bitmaskActual) {
        if (sesion.ultimaDecision() == null) return false;
        boolean mismoConjunto = sesion.ultimosDisparadores() == bitmaskActual;
        boolean dentroDeCooldown = Instant.now().isBefore(
                sesion.ultimaDecision().plusSeconds(props.cooldownS()));
        return mismoConjunto && dentroDeCooldown;
    }

    private static int bitmask(DisparadorRecalculo d) {
        return 1 << d.ordinal();
    }

    // =========================================================================
    // Paso 3 — evento relevante
    // =========================================================================

    /**
     * Selecciona el evento más relevante siguiendo la prioridad de la sección 3.4:
     * primero los que están en la ruta restante (menor distancia a lo largo de la ruta);
     * si ninguno está en la ruta, el más cercano en línea recta; desempate por mayor gravedad.
     */
    static EventoVial seleccionarEventoRelevante(List<EventoVial> aledanos,
                                                 List<Arista> aristas, int desdeArista,
                                                 double lat, double lng) {
        if (aledanos.isEmpty()) return null;

        EventoVial mejorEnRuta = null;
        double mejorDistRuta = Double.MAX_VALUE;

        EventoVial mejorFuera = null;
        double mejorDistFuera = Double.MAX_VALUE;

        for (EventoVial ev : aledanos) {
            double distRuta = GeoRuta.distanciaHastaTramo(aristas, desdeArista, ev.getTramoId());
            if (distRuta < Double.MAX_VALUE) {
                if (mejorEnRuta == null
                        || distRuta < mejorDistRuta
                        || (distRuta == mejorDistRuta
                            && ev.getGravedad() > mejorEnRuta.getGravedad())) {
                    mejorDistRuta = distRuta;
                    mejorEnRuta = ev;
                }
            } else {
                // Fuera de la ruta: distancia real (haversine) en metros a la geometría del evento
                double dist = GeoRuta.distanciaAGeometriaM(lat, lng, ev.getGeom());
                if (mejorFuera == null
                        || dist < mejorDistFuera
                        || (dist == mejorDistFuera
                            && ev.getGravedad() > mejorFuera.getGravedad())) {
                    mejorDistFuera = dist;
                    mejorFuera = ev;
                }
            }
        }
        return mejorEnRuta != null ? mejorEnRuta : mejorFuera;
    }

    // =========================================================================
    // Paso 4 — construir EstadoRecalculo
    // =========================================================================

    private EstadoRecalculo construirEstado(SesionNavegacion sesion,
                                            DisparadorRecalculo disparador,
                                            boolean desvio,
                                            EventoVial eventoRelevante,
                                            double lat, double lng, double velocidadKmh,
                                            int recalculosRecientes) {
        List<Arista> aristas = sesion.aristasRuta();
        int desdeArista = sesion.estadoDesvio().indiceArista();

        boolean eventoEnRuta = eventoRelevante != null
                && GeoRuta.tramoEnRutaRestante(aristas, desdeArista,
                        eventoRelevante.getTramoId());

        boolean eventoBloquea = eventoRelevante != null
                && registroEventos.instantanea().estaBloqueado(
                        eventoRelevante.getTramoId());

        TipoEventoVial tipoEvento = eventoRelevante != null ? eventoRelevante.getTipo() : null;
        double gravedad = eventoRelevante != null ? eventoRelevante.getGravedad() : 0.0;

        // dist_evento_ruta normalizada: 1.0 si no hay evento en ruta
        double distEventoNorm = 1.0;
        if (eventoRelevante != null && eventoEnRuta) {
            double distRutaM = GeoRuta.distanciaHastaTramo(
                    aristas, desdeArista, eventoRelevante.getTramoId());
            distEventoNorm = Math.min(distRutaM / props.distEventoMaxM(), 1.0);
        }

        double velMax = sesion.perfil().velocidadMaximaKmh();
        double velocidadRel = Math.min(velocidadKmh / velMax, 1.0);

        double longRestante = GeoRuta.longitudRestante(aristas, desdeArista);
        double longRestanteNorm = Math.min(longRestante / props.longRestanteMaxM(), 1.0);

        double recalcNorm = Math.min(
                (double) recalculosRecientes / props.maxRecalculosNorm(), 1.0);

        return new EstadoRecalculo(
                disparador, eventoEnRuta, eventoBloquea,
                eventoRelevante != null ? eventoRelevante.getId() : null,
                desvio, tipoEvento, gravedad, distEventoNorm,
                velocidadRel,
                sesion.perfil().tipo(),
                longRestanteNorm, recalcNorm);
    }

    // =========================================================================
    // Paso 4 — selección de política
    // =========================================================================

    private RecalculationPolicy seleccionarPolitica(ConfiguracionNavegacion config) {
        return switch (config) {
            case REFERENCE -> referencePolicy;
            case RULE      -> fixedRulePolicy;
            case AI        -> dqlPolicy;
        };
    }

    // =========================================================================
    // Paso 5 — ejecutar acción
    // =========================================================================

    private ResultadoAccion ejecutarAccion(Accion accion, SesionNavegacion sesion,
                                           SnapshotEventos snapshot,
                                           double lat, double lng,
                                           EstadoRecalculo estado) {
        if (accion == Accion.MANTENER) {
            return ResultadoAccion.mantener(estado);
        }

        // Preset efectivo: acción 2 → SEGURA (D10: no cambia el preset vigente)
        Preset presetEfectivo = (accion == Accion.RECALCULAR_SEGURA)
                ? Preset.SEGURA : sesion.preset();

        // Para REFERENCE siempre se usa costo TIEMPO
        boolean usarCostoTiempo =
                (sesion.configuracion() == ConfiguracionNavegacion.REFERENCE);

        // Destino original: lo leemos de la BD
        Navegacion nav = navegacionRepo.findById(sesion.navegacionId())
                .orElseThrow(() -> new NavegacionNoEncontradaException(
                        "Navegación " + sesion.navegacionId() + " no encontrada"));

        double latDestino = nav.getDestino().getY();
        double lngDestino = nav.getDestino().getX();

        GrafoEnMemoria grafo = grafoProvider.obtenerPorCategoria(
                sesion.perfil().categoriaReglamentaria());
        if (grafo == null) {
            return ResultadoAccion.sinRuta("El grafo no está disponible");
        }

        // Snap de la posición actual al grafo
        int[] snapActual = grafo.nodoMasCercano(lat, lng);
        if (snapActual[1] > maxSnapM) {
            return ResultadoAccion.sinRuta(
                    "La posición actual está fuera de cobertura");
        }
        int[] snapDestino = grafo.nodoMasCercano(latDestino, lngDestino);
        int idxOrigen  = snapActual[0];
        int idxDestino = snapDestino[0];

        if (idxOrigen == idxDestino) {
            return ResultadoAccion.sinRuta(
                    "La posición actual coincide con el destino");
        }

        // Referencia desde la posición actual (mismo origen del recálculo)
        ReferenciaRuta referencia;
        try {
            referencia = referenciaService.calcular(
                    sesion.perfil(), lat, lng, latDestino, lngDestino);
        } catch (SinCaminoException | MismoNodoException | PuntoFueraDeCoberturaException e) {
            return ResultadoAccion.sinRuta(e.getMessage());
        }

        // Función de costo del recálculo (preset vigente; SEGURA si la acción es 2).
        // La MISMA función evalúa la ruta anterior, para que los costos sean comparables.
        FuncionCosto funcion = construirFuncion(
                presetEfectivo, referencia, sesion, snapshot, usarCostoTiempo);

        // Costo de la ruta anterior desde la posición actual (mismo tramo restante)
        CostoRutaAnterior anterior = evaluarRutaAnterior(
                sesion, grafo, funcion, idxOrigen, snapshot, lat, lng);

        // A* con heurística ShortPaper (o heurística trivial para TIEMPO)
        NodoInfo nDestino = grafo.nodo(idxDestino);
        Heuristica heuristica = usarCostoTiempo
                ? (lt, lg) -> 0.0
                : HeuristicaMedible.crear(nDestino.lat(), nDestino.lng(),
                        sesion.perfil().velocidadMaximaKmh(),
                        referencia, presetProperties.de(presetEfectivo));

        Zona zona = Zona.alrededor(lat, lng, latDestino, lngDestino, margenZonaM);
        ResultadoDijkstra resultado = AStar.calcular(
                grafo, funcion, heuristica, idxOrigen, idxDestino, zona);

        if (!resultado.tieneCamino()) {
            return ResultadoAccion.sinRuta(mensajeSinAlternativa(), anterior);
        }

        RutaDto rutaDto = rutaBuilder.construir(resultado, grafo, sesion.perfil(),
                usarCostoTiempo ? "TIEMPO" : presetEfectivo.name(),
                lat, lng, snapActual[1],
                latDestino, lngDestino, snapDestino[1],
                idxOrigen, idxDestino);

        double costoNuevo = resultado.costoTotal();
        double tiempoNuevoS = tiempoTotalS(resultado.aristas(), grafo);
        return ResultadoAccion.conRuta(resultado.aristas(), rutaDto, anterior,
                costoNuevo, tiempoNuevoS);
    }

    // =========================================================================
    // Paso 5 — costo de la ruta anterior (comparable con el de la ruta nueva)
    // =========================================================================

    /**
     * Costo de la ruta anterior desde la posición actual, evaluado con la MISMA función de
     * costo con la que se calcula la ruta nueva (incluidas las demoras por nodo) y sobre el
     * mismo tramo restante: desde el nodo del grafo más cercano a la posición ({@code idxOrigen})
     * hasta el destino.
     *
     * <ul>
     *   <li>Si {@code idxOrigen} está sobre la ruta restante, se suman las aristas desde ese nodo.
     *   <li>Si no (desvío), se añade el tramo de reconexión de menor costo desde la posición
     *       hasta el final de la arista vigente, y se suma el resto de la ruta anterior.
     *   <li>Si algún tramo restante está bloqueado, devuelve {@link CostoRutaAnterior#bloqueada()}.
     * </ul>
     *
     * <p>El tiempo en segundos se calcula con la misma fórmula (CostoTiempo) para ambas rutas.
     */
    private CostoRutaAnterior evaluarRutaAnterior(SesionNavegacion sesion, GrafoEnMemoria grafo,
                                                  FuncionCosto funcion, int idxOrigen,
                                                  SnapshotEventos snapshot,
                                                  double lat, double lng) {
        List<Arista> aristas = sesion.aristasRuta();
        int desde = sesion.estadoDesvio().indiceArista();
        if (aristas == null || desde < 0 || desde >= aristas.size()) {
            return CostoRutaAnterior.NO_DISPONIBLE;
        }

        // Ruta anterior bloqueada: sin costo comparable (costo_anterior = NULL)
        for (int i = desde; i < aristas.size(); i++) {
            if (snapshot.estaBloqueado(aristas.get(i).tramoId())) {
                return CostoRutaAnterior.BLOQUEADA;
            }
        }

        // ¿El nodo de la posición actual está sobre la ruta restante?
        int nodo = nodoInicioArista(grafo, aristas, desde);
        int posicion = -1;
        for (int i = desde; i <= aristas.size(); i++) {
            if (nodo == idxOrigen) { posicion = i; break; }
            if (i < aristas.size()) nodo = aristas.get(i).destino();
        }

        double costo = 0.0;
        double tiempoS = 0.0;
        int primeraArista;
        if (posicion >= 0) {
            primeraArista = posicion;
        } else {
            // Desvío: reconectar con la ruta anterior al final de la arista vigente
            int nodoReconexion = aristas.get(desde).destino();
            NodoInfo nr = grafo.nodo(nodoReconexion);
            Zona zonaPuente = Zona.alrededor(lat, lng, nr.lat(), nr.lng(), margenZonaM);
            ResultadoDijkstra puente = AStar.calcular(
                    grafo, funcion, (lt, lg) -> 0.0, idxOrigen, nodoReconexion, zonaPuente);
            if (!puente.tieneCamino()) {
                return CostoRutaAnterior.NO_DISPONIBLE;
            }
            costo += puente.costoTotal();
            tiempoS += tiempoTotalS(puente.aristas(), grafo);
            primeraArista = desde + 1;
        }

        for (int i = primeraArista; i < aristas.size(); i++) {
            Arista a = aristas.get(i);
            var ctx = new ContextoNodo(grafo.nodo(a.destino()));
            double c = funcion.costo(a, ctx);
            if (!Double.isFinite(c)) return CostoRutaAnterior.BLOQUEADA;
            costo += c;
            tiempoS += costoTiempo.costo(a, ctx);
        }
        return new CostoRutaAnterior(costo, tiempoS, false);
    }

    /** Nodo (índice del grafo) donde empieza la arista {@code i} de la ruta; -1 si no se puede determinar. */
    private static int nodoInicioArista(GrafoEnMemoria grafo, List<Arista> aristas, int i) {
        if (i > 0) return aristas.get(i - 1).destino();
        double[][] geom = aristas.get(0).geom();
        if (geom == null || geom.length == 0) return -1;
        return grafo.nodoMasCercano(geom[0][0], geom[0][1])[0];
    }

    /** Tiempo total (s) de un conjunto de aristas con la fórmula de {@link CostoTiempo} (incluye demoras por nodo). */
    private double tiempoTotalS(List<Arista> aristas, GrafoEnMemoria grafo) {
        double total = 0.0;
        for (Arista a : aristas) {
            total += costoTiempo.costo(a, new ContextoNodo(grafo.nodo(a.destino())));
        }
        return total;
    }

    /**
     * Costo de la ruta anterior desde la posición actual.
     *
     * @param costo     costo con la función del recálculo; null si no hay valor comparable
     * @param tiempoS   tiempo en segundos con la fórmula de CostoTiempo; null si no hay valor
     * @param bloqueada true si la ruta restante tiene un tramo bloqueado
     */
    private record CostoRutaAnterior(Double costo, Double tiempoS, boolean bloqueada) {
        static final CostoRutaAnterior NO_DISPONIBLE = new CostoRutaAnterior(null, null, false);
        static final CostoRutaAnterior BLOQUEADA = new CostoRutaAnterior(null, null, true);
    }

    private FuncionCosto construirFuncion(Preset preset, ReferenciaRuta referencia,
                                          SesionNavegacion sesion, SnapshotEventos snapshot,
                                          boolean usarCostoTiempo) {
        if (usarCostoTiempo) return costoTiempo;
        return costoPresetFactory.crear(
                presetProperties.de(preset), referencia, sesion.perfil(), snapshot);
    }

    // =========================================================================
    // Paso 6 — registro en BD
    // =========================================================================

    private void registrar(Navegacion navegacion,
                            DecisionConMetadatos decision,
                            EstadoRecalculo estado,
                            DisparadorRecalculo disparador,
                            ResultadoAccion resultado,
                            long tiempoInferenciaMs, long tiempoCalculoMs,
                            long tiempoTotalMs) {
        Recalculo r = new Recalculo();
        r.setNavegacion(navegacion);
        r.setDisparador(disparador);
        r.setAccion((short) decision.accion().codigo());
        r.setConfiguracion(navegacion.getConfiguracion().name());
        r.setEstadoEntrada(construirJsonEstado(estado));
        r.setTiempoInferenciaMs((double) tiempoInferenciaMs);
        r.setTiempoCalculoMs((double) tiempoCalculoMs);
        r.setTiempoTotalServidorMs((double) tiempoTotalMs);
        r.setPolicyVersion(decision.version());
        r.setUsoRespaldo(decision.usoRespaldo());
        r.setMotivoRespaldo(decision.motivoRespaldo() != null
                ? decision.motivoRespaldo().name() : null);
        r.setRutaAnteriorBloqueada(decision.accion() != Accion.MANTENER
                && resultado.rutaAnteriorBloqueada());
        r.setCostoAnterior(resultado.costoAnterior());
        r.setCostoNuevo(resultado.costoNuevo());
        r.setCostoAnteriorTiempoS(resultado.costoAnteriorTiempoS());
        r.setCostoNuevoTiempoS(resultado.costoNuevoTiempoS());
        r.setInnecesario(calcularInnecesario(decision.accion(), estado, resultado));
        r.setExito(resultado.exito());
        r.setMensaje(resultado.mensaje());
        r.setCreadoEn(OffsetDateTime.now(ZoneOffset.UTC));
        recalculoRepo.save(r);
    }

    /**
     * Construye el JSON del estado de entrada: 14 features con nombre + campos crudos.
     * SIN coordenadas (RNF-03/06).
     */
    private com.fasterxml.jackson.databind.JsonNode construirJsonEstado(EstadoRecalculo e) {
        ObjectNode node = objectMapper.createObjectNode();
        double[] v = e.vector();
        List<String> nombres = EstadoRecalculo.NOMBRES_FEATURES;
        for (int i = 0; i < nombres.size(); i++) {
            node.put(nombres.get(i), v[i]);
        }
        // Campos crudos (no en el vector)
        node.put("eventoEnRutaRestante", e.eventoEnRutaRestante());
        node.put("eventoBloquea", e.eventoBloquea());
        if (e.eventoId() != null) node.put("tipoEventoId", e.eventoId());
        return node;
    }

    private Boolean calcularInnecesario(Accion accion, EstadoRecalculo estado,
                                         ResultadoAccion resultado) {
        if (accion == Accion.MANTENER) return null; // no aplica
        // Innecesario si: no había evento en ruta ni desvío...
        boolean sinMotivo = !estado.eventoEnRutaRestante() && !estado.desvio();
        if (sinMotivo) return true;
        // ... o la mejora relativa es ≤ epsilon
        if (resultado.costoAnterior() != null && resultado.costoNuevo() != null
                && resultado.costoAnterior() > 0) {
            double mejora = (resultado.costoAnterior() - resultado.costoNuevo())
                    / resultado.costoAnterior();
            return mejora <= props.epsilonMejora();
        }
        return false;
    }

    // =========================================================================
    // Mensajes al usuario (plantillas fijas en español)
    // =========================================================================

    static String mensajeBloqueoRecalculo() {
        return "Hay un evento en tu ruta. Te calculamos una ruta alternativa.";
    }

    static String mensajeBloqueoPermanece() {
        return "Hay un evento en tu ruta. No encontramos una ruta alternativa mejor.";
    }

    static String mensajeDesvioRecalculo() {
        return "Detectamos que te desviaste de la ruta. Recalculando...";
    }

    static String mensajeSinAlternativa() {
        return "No encontramos una ruta alternativa disponible en este momento.";
    }

    // =========================================================================
    // Tipos internos de resultado
    // =========================================================================

    /**
     * Resultado de la acción ejecutada.
     * Inmutable; construido con los métodos de fábrica estáticos.
     */
    public record ResultadoAccion(
            List<Arista> nuevaRuta,     // null si no hubo recálculo exitoso
            RutaDto rutaDto,            // null si no hubo recálculo exitoso
            Double costoAnterior,       // misma función de costo que costoNuevo; null si bloqueada/no disponible
            Double costoNuevo,
            Double costoAnteriorTiempoS, // misma fórmula de tiempo para ambas rutas
            Double costoNuevoTiempoS,
            boolean rutaAnteriorBloqueada,
            boolean exito,
            String mensaje) {

        static ResultadoAccion mantener(EstadoRecalculo e) {
            return new ResultadoAccion(null, null, null, null, null, null, false, true, null);
        }

        static ResultadoAccion sinRuta(String msg) {
            return new ResultadoAccion(null, null, null, null, null, null, false, false, msg);
        }

        private static ResultadoAccion sinRuta(String msg, CostoRutaAnterior ant) {
            return new ResultadoAccion(null, null, ant.costo(), null, ant.tiempoS(), null,
                    ant.bloqueada(), false, msg);
        }

        private static ResultadoAccion conRuta(List<Arista> aristas, RutaDto dto,
                                               CostoRutaAnterior ant,
                                               double costoNuevo, double tiempoNuevoS) {
            return new ResultadoAccion(aristas, dto, ant.costo(), costoNuevo,
                    ant.tiempoS(), tiempoNuevoS, ant.bloqueada(), true, null);
        }
    }

    /**
     * Respuesta al cliente para una muestra de posición procesada.
     */
    public record ResultadoPosicion(
            List<EventoVial> eventosAledanos,
            boolean huboDisparador,
            DisparadorRecalculo disparador,  // null si no hubo
            Accion accion,                   // null si no hubo disparador
            boolean usoRespaldo,
            String mensaje,
            RutaDto nuevaRuta,               // null si MANTENER o sin alternativa
            boolean exito,
            long tiempoCalculoMs) {

        static ResultadoPosicion soloAledanos(List<EventoVial> aledanos) {
            return new ResultadoPosicion(aledanos, false, null, null,
                    false, null, null, true, 0);
        }

        static ResultadoPosicion conRecalculo(List<EventoVial> aledanos,
                                              DecisionConMetadatos decision,
                                              ResultadoAccion resultado,
                                              long tCalcMs,
                                              DisparadorRecalculo disparador) {
            return new ResultadoPosicion(
                    aledanos, true, disparador,
                    decision.accion(), decision.usoRespaldo(),
                    resultado.mensaje(), resultado.rutaDto(),
                    resultado.exito(), tCalcMs);
        }
    }
}
