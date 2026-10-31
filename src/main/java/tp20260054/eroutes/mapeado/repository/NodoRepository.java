package tp20260054.eroutes.mapeado.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tp20260054.eroutes.mapeado.entity.Nodo;

public interface NodoRepository extends JpaRepository<Nodo, Long> {
}
