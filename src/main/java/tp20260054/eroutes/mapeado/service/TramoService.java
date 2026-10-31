package tp20260054.eroutes.mapeado.service;

import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.Coordinate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tp20260054.eroutes.mapeado.dto.Coordenada;
import tp20260054.eroutes.mapeado.dto.TramoDto;
import tp20260054.eroutes.mapeado.entity.Tramo;
import tp20260054.eroutes.mapeado.repository.TramoRepository;

@Service
@RequiredArgsConstructor
public class TramoService {

	private final TramoRepository tramoRepository;

	@Transactional(readOnly = true)
	public List<TramoDto> porBbox(double minLat, double minLng, double maxLat, double maxLng, int limite) {
		return tramoRepository.findByBbox(minLng, minLat, maxLng, maxLat, limite)
				.stream()
				.map(this::toDto)
				.toList();
	}

	private TramoDto toDto(Tramo tramo) {
		List<Coordenada> coordenadas = Arrays.stream(tramo.getGeom().getCoordinates())
				.map(this::toCoordenada)
				.toList();
		return new TramoDto(tramo.getId(), tramo.getTipoVia(), tramo.getLongitudM(),
				tramo.isEsCiclovia(), tramo.isValidado(), coordenadas);
	}

	private Coordenada toCoordenada(Coordinate coordinate) {
		return new Coordenada(coordinate.getY(), coordinate.getX());
	}
}
