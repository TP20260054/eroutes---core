package tp20260054.eroutes.routing.costo;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tp20260054.eroutes.routing.dto.PesosDto;
import tp20260054.eroutes.routing.graph.Arista;

/**
 * Función de costo multicriterio.
 * Combina tiempo, distancia, exposición, penalización por no validado y riesgo de cruce.
 *
 * <p>Todos los parámetros de la tabla son PROVISIONALES y configurables.
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

    private double calcular(Arista a, ContextoNodo destino, CostoProperties.Pesos w) {
        double dist = a.longitudM();

        // --- Tiempo ---
        double velVia = velEfectiva(a);
        double velVehiculo = 45.0; // km/h del perfil; la función no depende del perfil directamente
        double velUsada = Math.min(velVia, velVehiculo);
        double tiempoSeg = dist / (velUsada / 3.6);

        // --- Exposición ---
        double expBase = exposicionBase(a.tipoVia());
        double expo;
        if (a.velocidadMax() != null) {
            expo = Math.min(1.0, expBase * clamp(a.velocidadMax() / 50.0, 0.6, 1.2));
        } else {
            expo = expBase;
        }

        // --- Riesgo de cruce del nodo destino ---
        double riesgoCruce = destino != null && destino.info() != null
                ? destino.info().riesgoCruce()
                : 0.0;

        return w.tiempo()      * (tiempoSeg / 60.0)
             + w.distancia()   * (dist / 100.0)
             + w.exposicion()  * expo * (dist / 100.0)
             + w.noValidado()  * (a.validado() ? 0.0 : dist / 100.0)
             + w.cruce()       * riesgoCruce;
    }

    /** Velocidad efectiva de la vía en km/h (mínimo = velDefecto.minima). */
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

    /** Normaliza variantes _link al tipo base para la tabla de costos. */
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
