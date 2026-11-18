package tp20260054.eroutes.recalculo;

/**
 * Resultado de {@link DQLPolicyConRespaldo#decidirConMetadatos}: contiene la
 * acción elegida, si se usó respaldo y el motivo.
 */
public record DecisionConMetadatos(
        Accion accion,
        boolean usoRespaldo,
        MotivoRespaldo motivoRespaldo,  // null si no hubo respaldo
        String version,
        long tiempoInferenciaMs) {
}
