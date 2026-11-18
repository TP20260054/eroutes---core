package tp20260054.eroutes.routing.costo;

import java.util.Set;

/**
 * Snapshot inmutable de los eventos viales activos que afectan al ruteo.
 *
 * <p>Modela los tramos bloqueados (p. ej. {@code BLOQUEO_TOTAL}): un tramo bloqueado
 * hace que {@link CostoPreset} devuelva {@link Double#POSITIVE_INFINITY}, de modo que
 * Dijkstra lo omita. Es un tipo de valor plano (sin dependencia de la entidad
 * {@code EventoVial}); el servicio de recálculo lo construye a partir de los eventos
 * activos.
 */
public record SnapshotEventos(Set<Long> tramosBloqueados) {

    public static final SnapshotEventos VACIO = new SnapshotEventos(Set.of());

    public SnapshotEventos {
        tramosBloqueados = tramosBloqueados == null ? Set.of() : Set.copyOf(tramosBloqueados);
    }

    public boolean estaBloqueado(long tramoId) {
        return tramosBloqueados.contains(tramoId);
    }
}
