package tp20260054.eroutes.navegacion;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NavegacionRepository extends JpaRepository<Navegacion, Long> {

    /** Historial de navegaciones de un usuario (más recientes primero). */
    List<Navegacion> findByUsuarioIdOrderByCreadoEnDesc(Long usuarioId);
}
