package tp20260054.eroutes.mapeado.repository;

import java.util.List;
import tp20260054.eroutes.mapeado.entity.Tramo;

public interface TramoRepositoryCustom {

	List<Tramo> findByBbox(double minLng, double minLat, double maxLng, double maxLat,
			int limite, List<String> tiposVia, boolean soloCiclovias, Boolean validado,
			boolean unificarSentidos);
}
