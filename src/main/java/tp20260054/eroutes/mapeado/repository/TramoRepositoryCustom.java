package tp20260054.eroutes.mapeado.repository;

import java.util.List;
import java.util.Optional;

import tp20260054.eroutes.mapeado.entity.Tramo;

public interface TramoRepositoryCustom {

	List<Tramo> findByBbox(double minLng, double minLat, double maxLng, double maxLat,
			int limite, List<String> tiposVia, boolean soloCiclovias, Boolean validado,
			boolean unificarSentidos);

	/**
	 * Tramo más cercano al punto (lng, lat) dentro de {@code maxDistanciaM} metros,
	 * usando el operador KNN {@code <->} sobre el índice GIST de {@code geom}.
	 * Devuelve empty si no hay ninguno dentro del radio.
	 */
	Optional<Tramo> findTramoMasCercano(double lng, double lat, double maxDistanciaM);
}
