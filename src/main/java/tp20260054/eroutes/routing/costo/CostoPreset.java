package tp20260054.eroutes.routing.costo;

import tp20260054.eroutes.routing.graph.Arista;
import tp20260054.eroutes.vehiculo.PerfilVehiculo;

/**
 * Función de costo normalizada por preset. Combina tiempo, distancia, exposición,
 * tramos no validados y cruces de riesgo, cada uno normalizado por la
 * {@link ReferenciaRuta}, con pesos {@code Σw = 1} ({@link PesosPreset}).
 *
 * <p>Los tramos bloqueados en {@link SnapshotEventos} tienen costo
 * {@link Double#POSITIVE_INFINITY}, lo que hace que Dijkstra los omita. Se construye
 * por consulta con {@link CostoPresetFactory} a partir de (pesos, referencia, perfil,
 * SnapshotEventos); las fórmulas de tiempo/exposición se reutilizan de
 * {@link CostoMulticriterio}.
 */
public class CostoPreset implements FuncionCosto {

    private final PesosPreset pesos;
    private final ReferenciaRuta referencia;
    private final PerfilVehiculo perfil;
    private final SnapshotEventos eventos;
    private final CostoMulticriterio costo;

    CostoPreset(PesosPreset pesos, ReferenciaRuta referencia, PerfilVehiculo perfil,
                SnapshotEventos eventos, CostoMulticriterio costo) {
        this.pesos = pesos;
        this.referencia = referencia;
        this.perfil = perfil;
        this.eventos = eventos;
        this.costo = costo;
    }

    public PesosPreset pesos()         { return pesos; }
    public ReferenciaRuta referencia() { return referencia; }

    @Override
    public double costo(Arista arista, ContextoNodo destino) {
        if (eventos != null && eventos.estaBloqueado(arista.tramoId())) {
            return Double.POSITIVE_INFINITY;
        }

        var destinoInfo = destino != null ? destino.info() : null;
        double riesgoCruce = destinoInfo != null ? destinoInfo.riesgoCruce() : 0.0;

        // Cada componente se normaliza por la referencia (la ruta de referencia
        // suma ~1 en tiempo, distancia y cruce). tRefS y lRefM son > 0 por contrato
        // (ReferenciaRutaService lanza MismoNodoException si son 0).
        double tiempoS = costo.tiempoAristaS(arista, destinoInfo, perfil.velocidadMaximaKmh(), false);
        double expo = costo.exposicionArista(arista);

        double tNorm = tiempoS / referencia.tRefS();
        double dNorm = arista.longitudM() / referencia.lRefM();
        double eNorm = expo * arista.longitudM() / referencia.lRefM();
        double nvNorm = arista.validado() ? 0.0 : arista.longitudM() / referencia.lRefM();
        double cNorm = riesgoCruce >= 0.5 ? 1.0 / (referencia.cRef() + 1) : 0.0;

        return pesos.tiempo()     * tNorm
             + pesos.distancia()  * dNorm
             + pesos.exposicion() * eNorm
             + pesos.noValidado() * nvNorm
             + pesos.cruce()      * cNorm;
    }
}
