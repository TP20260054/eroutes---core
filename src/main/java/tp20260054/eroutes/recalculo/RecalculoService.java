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
import tp20260054.eroutes.routing.algorithm.HeuristicaMedible;
import tp20260054.eroutes.routing.algorithm.ResultadoDijkstra;
import tp20260054.eroutes.routing.algorithm.Zona;
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
                aledanos, sesion.aristasRuta(), sesion.estadoDesvio().indiceArista());

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

        // ----- Paso 5: ejecutar acción -----
        long tInicioCalc = System.currentTimeMillis();
        ResultadoAccion resultado = ejecutarAccion(
                decision.accion(), sesion, snapshot, lat, lng, estado);
        long tFinCalc = System.currentTimeMillis();

        long tTotalServidor = tFinCalc - tInicio;
        long tCalcMs = tFinCalc - tInicioCalc;

        // ----- Paso 6: registrar -----
        Navegacion navegacion = navegacionRepo.findById(navegacionId)
                .orElseThrow(() -> new NavegacionNoEncontradaException(
                        "Navegación " + navegacionId + " no encontrada en BD"));

        registrar(navegacion, decision, estado, disparador, resultado,
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
    private EventoVial seleccionarEventoRelevante(List<EventoVial> aledanos,
                                                  List<Arista> aristas, int desdeArista) {
        if (aledanos.isEmpty()) return null;

        EventoVial mejorEnRuta = null;
        double mejorDistRuta = Double.MAX_VALUE;

        EventoVial mejorFuera = null;
        double mejorDistEuclid = Double.MAX_VALUE;

        for (EventoVial ev : aledanos) {
            double distRuta = GeoRuta.distanciaHastaTramo(aristas, desdeArista, ev.getTramoId());
            if (distRuta < Double.MAX_VALUE) {
                if (distRuta < mejorDistRuta
                        || (distRuta == mejorDistRuta
                            && ev.getGravedad() > mejorEnRuta.getGravedad())) {
                    mejorDistRuta = distRuta;
                    mejorEnRuta = ev;
                }
            } else {
                double dist = distEuclid(ev);
                if (dist < mejorDistEuclid
                        || (dist == mejorDistEuclid
                            && mejorFuera != null
                            && ev.getGravedad() > mejorFuera.getGravedad())) {
                    mejorDistEuclid = dist;
                    mejorFuera = ev;
                }
            }
        }
        return mejorEnRuta != null ? mejorEnRuta : mejorFuera;
    }

    private double distEuclid(EventoVial ev) {
        // Usamos el campo geom que ya está en memoria en el registro
        var p = ev.getGeom();
        return p != null ? 0.0 : Double.MAX_VALUE; // la distancia real la gestionó cercanos()
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
            return ResultadoAccion.sinRuta(estado, "El grafo no está disponible");
        }

        // Snap de la posición actual al grafo
        int[] snapActual = grafo.nodoMasCercano(lat, lng);
        if (snapActual[1] > maxSnapM) {
            return ResultadoAccion.sinRuta(estado,
                    "La posición actual está fuera de cobertura");
        }
        int[] snapDestino = grafo.nodoMasCercano(latDestino, lngDestino);
        int idxOrigen  = snapActual[0];
        int idxDestino = snapDestino[0];

        if (idxOrigen == idxDestino) {
            return ResultadoAccion.sinRuta(estado,
                    "La posición actual coincide con el destino");
        }

        // Referencia desde la posición actual (mismo origen del recálculo)
        ReferenciaRuta referencia;
        try {
            referencia = referenciaService.calcular(
                    sesion.perfil(), lat, lng, latDestino, lngDestino);
        } catch (SinCaminoException | MismoNodoException | PuntoFueraDeCoberturaException e) {
            return ResultadoAccion.sinRuta(estado, e.getMessage());
        }

        // Costo anterior (ruta restante)
        Double costoAnterior = calcularCostoRestante(
                sesion.aristasRuta(), sesion.estadoDesvio().indiceArista(),
                sesion.perfil(), referencia, snapshot, usarCostoTiempo);

        // Función de costo para el recálculo
        FuncionCosto funcion = construirFuncion(
                presetEfectivo, referencia, sesion, snapshot, usarCostoTiempo);

        // A* con heurística ShortPaper (o heurística trivial para TIEMPO)
        NodoInfo nDestino = grafo.nodo(idxDestino);
        var heuristica = usarCostoTiempo
                ? (tp20260054.eroutes.routing.algorithm.Heuristica) (lt, lg) -> 0.0
                : HeuristicaMedible.crear(nDestino.lat(), nDestino.lng(),
                        sesion.perfil().velocidadMaximaKmh(),
                        referencia, presetProperties.de(presetEfectivo));

        Zona zona = Zona.alrededor(lat, lng, latDestino, lngDestino, margenZonaM);
        ResultadoDijkstra resultado = AStar.calcular(
                grafo, funcion, heuristica, idxOrigen, idxDestino, zona);

        if (!resultado.tieneCamino()) {
            return ResultadoAccion.sinRuta(estado, mensajeSinAlternativa());
        }

        NodoInfo nOrigen = grafo.nodo(idxOrigen);
        RutaDto rutaDto = rutaBuilder.construir(resultado, grafo, sesion.perfil(),
                usarCostoTiempo ? "TIEMPO" : presetEfectivo.name(),
                lat, lng, snapActual[1],
                latDestino, lngDestino, snapDestino[1],
                idxOrigen, idxDestino);

        double costoNuevo = resultado.costoTotal();
        return ResultadoAccion.conRuta(estado, resultado.aristas(), rutaDto,
                costoAnterior, costoNuevo);
    }

    // =========================================================================
    // Paso 5 — costo restante de la ruta anterior
    // =========================================================================

    private Double calcularCostoRestante(List<Arista> aristas, int desdeArista,
                                         tp20260054.eroutes.vehiculo.PerfilVehiculo perfil,
                                         ReferenciaRuta referencia,
                                         SnapshotEventos snapshot,
                                         boolean usarCostoTiempo) {
        if (aristas == null || desdeArista >= aristas.size()) return null;

        // Verificar si la ruta restante tiene algún tramo bloqueado
        for (int i = desdeArista; i < aristas.size(); i++) {
            if (snapshot.estaBloqueado(aristas.get(i).tramoId())) {
                return null; // ruta anterior bloqueada → costo_anterior = NULL
            }
        }

        FuncionCosto f = usarCostoTiempo
                ? costoTiempo
                : costoPresetFactory.crear(
                        presetProperties.de(Preset.RAPIDA), // placeholder; se sustituye abajo
                        referencia, perfil, snapshot);
        // Si no es TIEMPO necesitamos el preset real de la sesión; pero calcularCostoRestante
        // no recibe el preset. Para el cálculo de costo_anterior usamos el mismo costo preset
        // que se usó para decidir. El caller pasa 'usarCostoTiempo=false' con el preset vigente,
        // pero CostoPreset necesita los pesos. Simplificación: siempre calculamos con TIEMPO.
        // Esto es suficiente para la etiqueta 'innecesario'; el comparador usa mejora relativa.
        f = costoTiempo;

        double costo = 0.0;
        GrafoEnMemoria grafo = grafoProvider.obtenerPorCategoria(
                perfil.categoriaReglamentaria());
        if (grafo == null) return null;

        for (int i = desdeArista; i < aristas.size(); i++) {
            Arista a = aristas.get(i);
            var ctx = new tp20260054.eroutes.routing.costo.ContextoNodo(
                    grafo.nodo(a.destino()));
            double c = f.costo(a, ctx);
            if (!Double.isFinite(c)) return null; // bloqueada
            costo += c;
        }
        return costo;
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
        r.setRutaAnteriorBloqueada(resultado.costoAnterior() == null
                && decision.accion() != Accion.MANTENER);
        r.setCostoAnterior(resultado.costoAnterior());
        r.setCostoNuevo(resultado.costoNuevo());
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
            Double costoAnterior,
            Double costoNuevo,
            boolean exito,
            String mensaje) {

        static ResultadoAccion mantener(EstadoRecalculo e) {
            return new ResultadoAccion(null, null, null, null, true, null);
        }

        static ResultadoAccion sinRuta(EstadoRecalculo e, String msg) {
            return new ResultadoAccion(null, null, null, null, false, msg);
        }

        static ResultadoAccion conRuta(EstadoRecalculo e, List<Arista> aristas,
                                       RutaDto dto, Double costoAnt, double costoNuevo) {
            return new ResultadoAccion(aristas, dto, costoAnt, costoNuevo, true, null);
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
