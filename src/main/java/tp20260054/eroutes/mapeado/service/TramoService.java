package tp20260054.eroutes.mapeado.service;

import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.Coordinate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tp20260054.eroutes.mapeado.dto.Coordenada;
import tp20260054.eroutes.mapeado.dto.CoberturaDto;
import tp20260054.eroutes.mapeado.dto.TramoDetalleDto;
import tp20260054.eroutes.mapeado.dto.TramoDto;
import tp20260054.eroutes.mapeado.entity.Tramo;
import tp20260054.eroutes.mapeado.exception.RecursoNoEncontradoException;
import tp20260054.eroutes.mapeado.repository.CoberturaRepository;
import tp20260054.eroutes.mapeado.repository.NodoRepository;
import tp20260054.eroutes.mapeado.repository.TramoRepository;
import org.springframework.beans.factory.annotation.Value;

@Service
@RequiredArgsConstructor
public class TramoService {

	private final TramoRepository tramoRepository;
	private final NodoRepository nodoRepository;
	private final CoberturaRepository coberturaRepository;

	@Value("${eroutes.map.zona-nombre}")
	private String zonaNombre;

	@Transactional(readOnly = true)
	public List<TramoDto> porBbox(double minLat, double minLng, double maxLat, double maxLng,
			int limite, List<String> tiposVia, boolean soloCiclovias, Boolean validado,
			boolean unificarSentidos) {
		return tramoRepository.findByBbox(minLng, minLat, maxLng, maxLat, limite, tiposVia,
				soloCiclovias, validado, unificarSentidos)
				.stream()
				.map(this::toDto)
				.toList();
	}

	@Transactional(readOnly = true)
	public TramoDetalleDto porId(Long id) {
		Tramo tramo = tramoRepository.findById(id)
				.orElseThrow(() -> new RecursoNoEncontradoException("No existe el tramo " + id));
		return new TramoDetalleDto(tramo.getId(), tramo.getOsmWayId(), tramo.getNodoOrigen(),
				tramo.getNodoDestino(), tramo.getTipoVia(), tramo.getLongitudM(),
				tramo.getVelocidadMax(), tramo.isEsCiclovia(), tramo.isValidado(),
				tramo.isSentidoUnico(), coordenadas(tramo), tramo.getNombre());
	}

	@Transactional(readOnly = true)
	public CoberturaDto cobertura() {
		List<Object[]> rows = coberturaRepository.extent();
		Object[] extent = rows.isEmpty() ? null : rows.get(0);
		if (extent == null || extent[0] == null) {
			throw new RecursoNoEncontradoException("No hay datos de cobertura vial disponibles");
		}
		double minLng = ((Number) extent[0]).doubleValue();
		double minLat = ((Number) extent[1]).doubleValue();
		double maxLng = ((Number) extent[2]).doubleValue();
		double maxLat = ((Number) extent[3]).doubleValue();
		return new CoberturaDto(zonaNombre, minLat, minLng, maxLat, maxLng,
				new Coordenada((minLat + maxLat) / 2, (minLng + maxLng) / 2),
				tramoRepository.count(), nodoRepository.count());
	}

	private TramoDto toDto(Tramo tramo) {
		return new TramoDto(tramo.getId(), tramo.getTipoVia(), tramo.getLongitudM(),
				tramo.isEsCiclovia(), tramo.isValidado(), coordenadas(tramo), tramo.isSentidoUnico(),
				tramo.getNombre());
	}

	private List<Coordenada> coordenadas(Tramo tramo) {
		List<Coordenada> coordenadas = Arrays.stream(tramo.getGeom().getCoordinates())
				.map(this::toCoordenada)
				.toList();
		return coordenadas;
	}

	private Coordenada toCoordenada(Coordinate coordinate) {
		return new Coordenada(coordinate.getY(), coordinate.getX());
	}
}
