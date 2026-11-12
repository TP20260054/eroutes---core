package tp20260054.eroutes.mapeado.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import tp20260054.eroutes.mapeado.entity.Tramo;

public interface CoberturaRepository extends JpaRepository<Tramo, Long> {

	@Query(value = """
			SELECT ST_XMin(ext), ST_YMin(ext), ST_XMax(ext), ST_YMax(ext)
			FROM (SELECT ST_Extent(geom) AS ext FROM tramos) cobertura
			""", nativeQuery = true)
	List<Object[]> extent();
}
