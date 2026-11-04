package tp20260054.eroutes.mapeado.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import tp20260054.eroutes.mapeado.entity.Tramo;

public interface TramoRepository extends JpaRepository<Tramo, Long>, TramoRepositoryCustom {
}
