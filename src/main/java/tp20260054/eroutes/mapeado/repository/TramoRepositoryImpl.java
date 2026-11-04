package tp20260054.eroutes.mapeado.repository;

import jakarta.persistence.EntityManager;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import tp20260054.eroutes.mapeado.entity.Tramo;

@Repository
@RequiredArgsConstructor
public class TramoRepositoryImpl implements TramoRepositoryCustom {

	private final EntityManager entityManager;

	@Override
	public List<Tramo> findByBbox(double minLng, double minLat, double maxLng, double maxLat,
			int limite, List<String> tiposVia, boolean soloCiclovias, Boolean validado,
			boolean unificarSentidos) {
		StringBuilder sql = new StringBuilder("""
				SELECT t.* FROM tramos t
				WHERE t.geom && ST_MakeEnvelope(:minLng, :minLat, :maxLng, :maxLat, 4326)
				""");
		if (tiposVia != null && !tiposVia.isEmpty()) {
			sql.append(" AND t.tipo_via IN (:tiposVia)");
		}
		if (soloCiclovias) {
			sql.append(" AND t.es_ciclovia = true");
		}
		if (validado != null) {
			sql.append(" AND t.validado = :validado");
		}
		if (unificarSentidos) {
			sql.append("""
					 AND (
						t.sentido_unico = true
						OR t.nodo_origen < t.nodo_destino
						OR NOT EXISTS (
							SELECT 1 FROM tramos inversa
							WHERE inversa.nodo_origen = t.nodo_destino
							  AND inversa.nodo_destino = t.nodo_origen
						)
					)
					""");
		}

		var query = entityManager.createNativeQuery(sql.toString(), Tramo.class)
				.setParameter("minLng", minLng)
				.setParameter("minLat", minLat)
				.setParameter("maxLng", maxLng)
				.setParameter("maxLat", maxLat)
				.setMaxResults(limite);
		if (tiposVia != null && !tiposVia.isEmpty()) {
			query.setParameter("tiposVia", tiposVia);
		}
		if (validado != null) {
			query.setParameter("validado", validado);
		}
		return query.getResultList();
	}
}
