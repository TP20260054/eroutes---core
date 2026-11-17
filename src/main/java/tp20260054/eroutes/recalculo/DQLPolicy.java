package tp20260054.eroutes.recalculo;

/**
 * Política DQL: inferencia del MLP → argmax de los 3 valores Q.
 *
 * <p>No se registra como bean de Spring directamente; es construida por
 * {@link DQLPolicyConRespaldo}, que encapsula la carga del modelo y el respaldo.
 * Paquete-privada: solo {@link DQLPolicyConRespaldo} la instancia.
 */
final class DQLPolicy implements RecalculationPolicy {

    private final MlpModel modelo;

    DQLPolicy(MlpModel modelo) {
        if (!modelo.estaDisponible()) {
            throw new IllegalArgumentException(
                    "No se puede crear DQLPolicy con un modelo no disponible");
        }
        this.modelo = modelo;
    }

    @Override
    public Accion decide(EstadoRecalculo estado) {
        double[] q = modelo.inferir(estado.vector());
        int mejor = 0;
        for (int i = 1; i < q.length; i++) {
            if (q[i] > q[mejor]) mejor = i;
        }
        return Accion.deCodigo(mejor);
    }

    @Override
    public String version() { return "dql-" + modelo.version(); }
}
