package tp20260054.eroutes.usuarios;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface VehiculoRepository extends JpaRepository<Vehiculo, Long> {

    List<Vehiculo> findByUsuarioIdOrderByCreadoEnAsc(Long usuarioId);

    /** Cuenta vehículos del usuario con bloqueo pesimista para evitar race condition. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM Usuario u WHERE u.id = :usuarioId")
    Optional<Usuario> findUsuarioForUpdate(Long usuarioId);

    @Query("SELECT count(v) FROM Vehiculo v WHERE v.usuario.id = :usuarioId")
    int countByUsuarioId(Long usuarioId);

    Optional<Vehiculo> findByIdAndUsuarioId(Long id, Long usuarioId);

    /** Vehículo más reciente del usuario (distinto del dado), para reasignar activo. */
    @Query("""
            SELECT v FROM Vehiculo v
            WHERE v.usuario.id = :usuarioId
              AND v.id <> :excluidoId
            ORDER BY v.creadoEn DESC
            """)
    List<Vehiculo> findMasRecienteExcluyendo(Long usuarioId, Long excluidoId);
}
