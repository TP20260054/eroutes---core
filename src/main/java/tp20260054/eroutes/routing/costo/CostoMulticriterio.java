package tp20260054.eroutes.routing.costo;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tp20260054.eroutes.routing.dto.PesosDto;
import tp20260054.eroutes.routing.graph.Arista;
import tp20260054.eroutes.routing.graph.NodoInfo;

/**
 * Función de costo multicriterio.
 * Combina tiempo, distancia, exposición, penalización por no validado y riesgo de cruce.
 *
 * <p>Todos los parámetros son PROVISIONALES y configurables.
 */
@Component("MULTICRITERIO")
@RequiredArgsConstructor
public class CostoMulticriterio implements FuncionCosto {

    private final CostoProperties props;

    @Override
    public double costo(Arista a, ContextoNodo destino) {
        return calcular(a, destino, props.pesos());
    }

    /**
     * Devuelve una {@link FuncionCosto} que usa los pesos dados en lugar de los configurados.
     * Los campos null del override se sustituyen por el valor por defecto.
     */
    public FuncionCosto conPesos(PesosDto override) {
        CostoProperties.Pesos base = props.pesos();
        CostoProperties.Pesos efectivos = new CostoProperties.Pesos(
                override.tiempo()     != null ? override.tiempo()     : base.tiempo(),
                override.distancia()  != null ? override.distancia()  : base.distancia(),
                override.exposicion() != null ? override.exposicion() : base.exposicion(),
                override.noValidado() != null ? override.noValidado() : base.noValidado(),
                override.cruce()      != null ? override.cruce()      : base.cruce());
        return (arista, ctx) -> calcular(arista, ctx, efectivos);
    }

    // -------------------------------------------------------------------------
    // Tiempo: fórmula compartida con CostoTiempo y RuteoService
    // -------------------------------------------------------------------------

    /**
     * Tiempo de recorrido de la arista en segundos, incluyendo la demora
     * al llegar al nodo destino.
     *
     * <p>Si {@code esDestinoFinal} es true, NO se suma la demora del nodo
     * (el último nodo de la ruta no genera espera).
     *
     * @param a               arista
     * @param destinoInfo     información del nodo destino (puede ser null)
     * @param velVehiculoKmh  velocidad máxima del vehículo en km/h
     * @param esDestinoFinal  true si este es el último nodo de la ruta
     */
    public double tiempoAristaS(Arista a, NodoInfo destinoInfo,
                                double velVehiculoKmh, boolean esDestinoFinal) {
        double factor = props.factorVelocidadEfectiva();
        double velVia = velEfectiva(a);
        double velUsada = factor * Math.min(velVia, velVehiculoKmh);
        double tiempoRodadura = a.longitudM() / (velUsada / 3.6);

        double demora = 0.0;
        if (!esDestinoFinal && destinoInfo != null) {
            demora = demoraNodeS(destinoInfo);
        }
        return tiempoRodadura + demora;
    }

    /** Demora en segundos al llegar a un nodo según su control y grado. */
    public double demoraNodeS(NodoInfo nodo) {
        CostoProperties.DemoraNode d = props.demoraNodo();
        if (nodo == null) return 0.0;
        String ctrl = nodo.control();
        if ("traffic_signals".equals(ctrl)) return d.trafficSignals();
        if ("stop".equals(ctrl))            return d.stop();
        if ("crossing".equals(ctrl))        return d.crossing();
        // Nodo sin control pero con grado >= 3: demora mínima
        if (nodo.riesgoCruce() > 0.0)       return d.sinControl();
        return 0.0;
    }

    // -------------------------------------------------------------------------

    private double calcular(Arista a, ContextoNodo destino, CostoProperties.Pesos w) {
        double dist = a.longitudM();

        // Tiempo usando la fórmula con factor de velocidad y demora de nodo
        // En Dijkstra no sabemos si el nodo destino es el final, se usa false (conservador)
        NodoInfo destinoInfo = (destino != null) ? destino.info() : null;
        double tiempoSeg = tiempoAristaS(a, destinoInfo, 45.0, false);

        // Exposición
        double expBase = exposicionBase(a.tipoVia());
        double expo;
        if (a.velocidadMax() != null) {
            expo = Math.min(1.0, expBase * clamp(a.velocidadMax() / 50.0, 0.6, 1.2));
        } else {
            expo = expBase;
        }

        // Riesgo de cruce del nodo destino
        double riesgoCruce = destinoInfo != null ? destinoInfo.riesgoCruce() : 0.0;

        return w.tiempo()      * (tiempoSeg / 60.0)
             + w.distancia()   * (dist / 100.0)
             + w.exposicion()  * expo * (dist / 100.0)
             + w.noValidado()  * (a.validado() ? 0.0 : dist / 100.0)
             + w.cruce()       * riesgoCruce;
    }

    /** Velocidad libre de la vía en km/h (mínimo = velDefecto.minima). */
    public double velEfectiva(Arista a) {
        CostoProperties.VelDefecto vd = props.velDefecto();
        if (a.velocidadMax() != null) {
            return Math.max(a.velocidadMax(), vd.minima());
        }
        return switch (tipoViaBase(a.tipoVia())) {
            case "primary"       -> vd.primary();
            case "secondary"     -> vd.secondary();
            case "tertiary"      -> vd.tertiary();
            case "unclassified"  -> vd.unclassified();
            case "residential"   -> vd.residential();
            case "living_street" -> vd.livingStreet();
            case "service"       -> vd.service();
            default              -> vd.otros();
        };
    }

    private double exposicionBase(String tipoVia) {
        CostoProperties.Exposicion e = props.exposicion();
        return switch (tipoViaBase(tipoVia)) {
            case "primary"       -> e.primary();
            case "secondary"     -> e.secondary();
            case "tertiary"      -> e.tertiary();
            case "unclassified"  -> e.unclassified();
            case "residential"   -> e.residential();
            case "living_street" -> e.livingStreet();
            case "service"       -> e.service();
            default              -> e.otros();
        };
    }

    private static String tipoViaBase(String tipoVia) {
        if (tipoVia == null) return "";
        return switch (tipoVia) {
            case "primary_link"   -> "primary";
            case "secondary_link" -> "secondary";
            case "tertiary_link"  -> "tertiary";
            default               -> tipoVia;
        };
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}
