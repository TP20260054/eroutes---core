package tp20260054.eroutes.mapeado.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tp20260054.eroutes.mapeado.dto.TramoDetalleDto;
import tp20260054.eroutes.mapeado.service.TramoService;

@WebMvcTest(MapaController.class)
class MapaControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private TramoService service;

	@Test
	void bboxValidoDevuelve200() throws Exception {
		org.mockito.Mockito.when(service.porBbox(anyDouble(), anyDouble(), anyDouble(), anyDouble(),
				anyInt(), anyList(), anyBoolean(), isNull(), anyBoolean())).thenReturn(List.of());

		mockMvc.perform(get("/api/v1/map/segments")
						.param("minLat", "-12.15").param("minLng", "-77.07")
						.param("maxLat", "-12.08").param("maxLng", "-77.00"))
				.andExpect(status().isOk())
				.andExpect(content().json("[]"));
	}

	@Test
	void bboxInvertidoDevuelve400() throws Exception {
		mockMvc.perform(get("/api/v1/map/segments")
						.param("minLat", "-12.08").param("minLng", "-77.07")
						.param("maxLat", "-12.15").param("maxLng", "-77.00"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void limiteFueraDeRangoDevuelve400() throws Exception {
		mockMvc.perform(get("/api/v1/map/segments")
						.param("minLat", "-12.15").param("minLng", "-77.07")
						.param("maxLat", "-12.08").param("maxLng", "-77.00")
						.param("limit", "5001"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void tipoMalFormadoDevuelve400() throws Exception {
		mockMvc.perform(get("/api/v1/map/segments")
						.param("minLat", "no-numero").param("minLng", "-77.07")
						.param("maxLat", "-12.08").param("maxLng", "-77.00"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void detalleInexistenteDevuelve404() throws Exception {
		org.mockito.Mockito.when(service.porId(999999L))
				.thenThrow(new tp20260054.eroutes.mapeado.exception.RecursoNoEncontradoException(
						"No existe el tramo 999999"));

		mockMvc.perform(get("/api/v1/map/segments/999999"))
				.andExpect(status().isNotFound());
	}
}
