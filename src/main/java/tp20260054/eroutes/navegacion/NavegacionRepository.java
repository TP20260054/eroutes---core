package tp20260054.eroutes.navegacion;

import java.util.List;
import java.time.OffsetDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface NavegacionRepository extends JpaRepository<Navegacion, Long> {

    /** Historial de navegaciones de un usuario (más recientes primero). */
    List<Navegacion> findByUsuarioIdOrderByCreadoEnDesc(Long usuarioId);

    /**
     * Pone en NULL origen y destino de las navegaciones creadas antes de {@code limite}
     * que aún conserven coordenadas. Devuelve las filas afectadas.
     */
    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE Navegacion n
               SET n.origen = NULL, n.destino = NULL
             WHERE n.creadoEn < :limite
               AND (n.origen IS NOT NULL OR n.destino IS NOT NULL)
            """)
    int purgarCoordenadasAnterioresA(OffsetDateTime limite);
}
