package tp20260054.eroutes.recalculo;

import java.time.Instant;
import java.util.List;

import tp20260054.eroutes.navegacion.ConfiguracionNavegacion;
import tp20260054.eroutes.routing.costo.Preset;
import tp20260054.eroutes.routing.graph.Arista;
import tp20260054.eroutes.vehiculo.PerfilVehiculo;

/**
 * Estado en memoria de una sesión de navegación activa.
 *
 * <p>Una sesión es propiedad de un único usuario: solo el dueño (JWT
 * {@code subject} = {@code usuarioId}) puede acceder a ella. El
 * {@link SesionNavegacionStore} devuelve {@code empty} cuando el
 * {@code usuarioId} no coincide, y la capa de servicio lanza 404.
 *
 * <p>No es thread-safe por sí sola; toda la sincronización se gestiona
 * mediante {@code synchronized} sobre la propia sesión en
 * {@link SesionNavegacionStore}, que es suficiente para las operaciones
 * secuenciales de una sesión individual.
 */
public final class SesionNavegacion {

    // ---- Identidad ----
    private final long navegacionId;
    private final long usuarioId;
    private final PerfilVehiculo perfil;
    private final ConfiguracionNavegacion configuracion;

    // ---- Preset vigente ----
    // Acción 2 (RECALCULAR_SEGURA) NO cambia el preset vigente (D10)
    private final Preset preset;

    // ---- Ruta activa ----
    private List<Arista> aristasRuta;

    // ---- Detector de desvío ----
    private volatile DetectorDesvio.EstadoDesvio estadoDesvio;

    // ---- Tiempo de vida ----
    private volatile Instant ultimoAcceso;

    // ---- Antirrebote (D12) ----
    // Conjunto de disparadores de la última decisión (representado como bitmask
    // con los bits de los enum Disparador). Solo se consulta a la política cuando
    // el conjunto cambia o ha pasado el cooldown.
    private volatile int ultimosDisparadores;   // bitmask
    private volatile Instant ultimaDecision;    // null si nunca se tomó una

    // -------------------------------------------------------------------------

    public SesionNavegacion(long navegacionId, long usuarioId,
                            PerfilVehiculo perfil,
                            ConfiguracionNavegacion configuracion,
                            Preset preset,
                            List<Arista> aristasRuta) {
        this.navegacionId    = navegacionId;
        this.usuarioId       = usuarioId;
        this.perfil          = perfil;
        this.configuracion   = configuracion;
        this.preset          = preset;
        this.aristasRuta     = List.copyOf(aristasRuta);
        this.estadoDesvio    = new DetectorDesvio.EstadoDesvio(this.aristasRuta);
        this.ultimoAcceso    = Instant.now();
        this.ultimosDisparadores = 0;
        this.ultimaDecision  = null;
    }

    // ---- Getters ----

    public long navegacionId()                 { return navegacionId; }
    public long usuarioId()                    { return usuarioId; }
    public PerfilVehiculo perfil()             { return perfil; }
    public ConfiguracionNavegacion configuracion() { return configuracion; }
    public Preset preset()                     { return preset; }
    public List<Arista> aristasRuta()          { return aristasRuta; }
    public DetectorDesvio.EstadoDesvio estadoDesvio() { return estadoDesvio; }
    public Instant ultimoAcceso()              { return ultimoAcceso; }
    public int ultimosDisparadores()           { return ultimosDisparadores; }
    public Instant ultimaDecision()            { return ultimaDecision; }

    // ---- Mutaciones ----

    /** Reemplaza la ruta activa (tras un recálculo exitoso) y reinicia el estado de desvío. */
    public synchronized void reemplazarRuta(List<Arista> nuevasAristas) {
        this.aristasRuta = List.copyOf(nuevasAristas);
        this.estadoDesvio = new DetectorDesvio.EstadoDesvio(this.aristasRuta);
        tocarAcceso();
    }

    public synchronized void tocarAcceso() {
        this.ultimoAcceso = Instant.now();
    }

    public synchronized void registrarDecision(int disparadoresBitmask) {
        this.ultimosDisparadores = disparadoresBitmask;
        this.ultimaDecision = Instant.now();
    }

    // -------------------------------------------------------------------------
}
