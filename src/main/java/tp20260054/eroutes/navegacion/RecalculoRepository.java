package tp20260054.eroutes.navegacion;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RecalculoRepository extends JpaRepository<Recalculo, Long> {

    /** Historial de recálculos de una navegación (orden cronológico). */
    List<Recalculo> findByNavegacionIdOrderByCreadoEnAsc(Long navegacionId);

    /**
     * Cuenta recálculos con acción ≠ 0 (es decir, que tomaron una acción) desde
     * {@code desde} para la navegación dada. Usado para calcular la feature
     * {@code recalculos_recientes}.
     */
    @Query("""
            SELECT count(r) FROM Recalculo r
             WHERE r.navegacion.id = :navegacionId
               AND r.accion <> 0
               AND r.creadoEn >= :desde
            """)
    int contarAccionesDesde(Long navegacionId, OffsetDateTime desde);
}
