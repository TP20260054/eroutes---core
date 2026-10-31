package tp20260054.eroutes.mapeado.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tp20260054.eroutes.mapeado.entity.Tramo;
import tp20260054.eroutes.mapeado.repository.CoberturaRepository;
import tp20260054.eroutes.mapeado.repository.NodoRepository;
import tp20260054.eroutes.mapeado.repository.TramoRepository;

@ExtendWith(MockitoExtension.class)
class TramoServiceTest {

	@Mock
	private TramoRepository tramoRepository;

	@Mock
	private NodoRepository nodoRepository;

	@Mock
	private CoberturaRepository coberturaRepository;

	@InjectMocks
	private TramoService service;

	@Test
	void mapeaGeometriaComoLatitudLongitud() {
		Tramo tramo = new Tramo();
		tramo.setId(1L);
		tramo.setLongitudM(12.5);
		LineString geometry = new GeometryFactory().createLineString(new Coordinate[] {
				new Coordinate(-77.05, -12.10),
				new Coordinate(-77.04, -12.09)
		});
		tramo.setGeom(geometry);
		when(tramoRepository.findByBbox(-77.07, -12.15, -77.00, -12.08, 2000,
				List.of(), false, null, true)).thenReturn(List.of(tramo));

		var result = service.porBbox(-12.15, -77.07, -12.08, -77.00, 2000,
				List.of(), false, null, true);

		assertThat(result).hasSize(1);
		assertThat(result.get(0).coordenadas()).containsExactly(
				new tp20260054.eroutes.mapeado.dto.Coordenada(-12.10, -77.05),
				new tp20260054.eroutes.mapeado.dto.Coordenada(-12.09, -77.04));
	}
}
