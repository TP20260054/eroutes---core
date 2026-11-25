package tp20260054.eroutes.eventos;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface EventoVialRepository extends JpaRepository<EventoVial, Long> {

    /** Todos los eventos en un estado dado (usado para la carga inicial). */
    List<EventoVial> findByEstado(EstadoEventoVial estado);

    /** Eventos del usuario en el estado dado cuyo vencimiento es posterior a {@code ahora}. */
    long countByAutorUsuarioIdAndEstadoAndExpiraEnAfter(Long autorUsuarioId,
                                                        EstadoEventoVial estado,
                                                        OffsetDateTime ahora);

    /**
     * Pasa a EXPIRADO todos los eventos ACTIVO cuyo {@code expira_en} ya venció.
     * Devuelve el número de filas afectadas.
     */
    @Modifying
    @Query("""
            UPDATE EventoVial e
               SET e.estado = 'EXPIRADO'
             WHERE e.estado = 'ACTIVO'
               AND e.expiraEn <= :ahora
            """)
    int expirarVencidos(OffsetDateTime ahora);
}
