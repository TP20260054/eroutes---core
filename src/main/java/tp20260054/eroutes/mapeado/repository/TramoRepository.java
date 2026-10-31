package tp20260054.eroutes.mapeado.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import tp20260054.eroutes.mapeado.entity.Tramo;

public interface TramoRepository extends JpaRepository<Tramo, Long> {

	@Query(value = """
			SELECT * FROM tramos
			WHERE geom && ST_MakeEnvelope(:minLng, :minLat, :maxLng, :maxLat, 4326)
			LIMIT :limite
			""", nativeQuery = true)
	List<Tramo> findByBbox(@Param("minLng") double minLng, @Param("minLat") double minLat,
			@Param("maxLng") double maxLng, @Param("maxLat") double maxLat,
			@Param("limite") int limite);
}
