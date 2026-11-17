package tp20260054.eroutes.routing.algorithm;

import tp20260054.eroutes.routing.costo.PesosPreset;
import tp20260054.eroutes.routing.costo.ReferenciaRuta;
import tp20260054.eroutes.routing.graph.GrafoEnMemoria;

/**
 * Heurística Medible:
 * <pre>h(n) = w_T·(d(n,t)/v_max)/T_ref + w_D·d(n,t)/L_ref</pre>
 * con {@code d} haversine y {@code v_max} la velocidad máxima del vehículo (km/h).
 *
 * <p>Es admisible y consistente para {@link tp20260054.eroutes.routing.costo.CostoPreset}:
 * los demás términos del costo (exposición, no-validado, cruces) son ≥ 0, el tiempo
 * real nunca baja de {@code l/v_max} y la distancia real nunca baja de la recta
 * (haversine). Por tanto {@code h(n) ≤ costo real desde n hasta t}.
 */
public final class HeuristicaMedible {

    private HeuristicaMedible() {}

    public static Heuristica crear(double latDestino, double lngDestino,
                                   double velMaxKmh, ReferenciaRuta referencia,
                                   PesosPreset pesos) {
        double vMaxMs = velMaxKmh / 3.6;
        double wT = pesos.tiempo();
        double wD = pesos.distancia();
        double tRef = referencia.tRefS();
        double lRef = referencia.lRefM();
        return (lat, lng) -> {
            double d = GrafoEnMemoria.haversine(lat, lng, latDestino, lngDestino);
            return wT * (d / vMaxMs) / tRef + wD * d / lRef;
        };
    }
}
