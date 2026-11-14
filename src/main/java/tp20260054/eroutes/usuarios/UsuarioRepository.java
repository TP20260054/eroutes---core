package tp20260054.eroutes.usuarios;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    @Query("SELECT u FROM Usuario u WHERE lower(u.correo) = lower(:correo)")
    Optional<Usuario> findByCorreoIgnoreCase(String correo);

    @Query("""
            SELECT u FROM Usuario u
            LEFT JOIN FETCH u.vehiculoActivo
            WHERE u.id = :id
            """)
    Optional<Usuario> findByIdConVehiculoActivo(Long id);

    boolean existsByCorreoIgnoreCase(String correo);
}
