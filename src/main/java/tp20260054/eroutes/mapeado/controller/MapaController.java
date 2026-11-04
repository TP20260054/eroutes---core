package tp20260054.eroutes.mapeado.controller;

import java.util.List;
import lombok.RequiredArgsConstructor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tp20260054.eroutes.mapeado.dto.CoberturaDto;
import tp20260054.eroutes.mapeado.dto.TramoDetalleDto;
import tp20260054.eroutes.mapeado.dto.TramoDto;
import tp20260054.eroutes.mapeado.service.TramoService;
import tp20260054.eroutes.mapeado.validation.BboxValidator;

@RestController
@RequestMapping("/api/v1/map")
@RequiredArgsConstructor
@Validated
@Tag(name = "Mapeado", description = "Consultas del grafo vial")
public class MapaController {

	private final TramoService service;

	@GetMapping("/segments")
	@Operation(summary = "Consultar tramos por bounding box")
	public List<TramoDto> segments(
			@Parameter(description = "Latitud mínima") @RequestParam double minLat,
			@Parameter(description = "Longitud mínima") @RequestParam double minLng,
			@Parameter(description = "Latitud máxima") @RequestParam double maxLat,
			@Parameter(description = "Longitud máxima") @RequestParam double maxLng,
			@Parameter(description = "Máximo de tramos a devolver")
			@RequestParam(defaultValue = "2000") @Min(1) @Max(5000) int limit,
			@Parameter(description = "Tipos de vía separados por comas")
			@RequestParam(required = false) String tiposVia,
			@Parameter(description = "Limitar a ciclovías")
			@RequestParam(defaultValue = "false") boolean soloCiclovias,
			@Parameter(description = "Filtrar por estado de validación")
			@RequestParam(required = false) Boolean validado,
			@Parameter(description = "Omitir la fila inversa de sentidos duplicados")
			@RequestParam(defaultValue = "true") boolean unificarSentidos) {
		BboxValidator.validate(minLat, minLng, maxLat, maxLng);
		List<String> tipos = tiposVia == null || tiposVia.isBlank()
				? List.of()
				: List.of(tiposVia.split(",")).stream().map(String::trim).filter(s -> !s.isBlank()).toList();
		return service.porBbox(minLat, minLng, maxLat, maxLng, limit, tipos,
				soloCiclovias, validado, unificarSentidos);
	}

	@GetMapping("/segments/{id}")
	@Operation(summary = "Obtener el detalle de un tramo")
	public TramoDetalleDto segment(@Parameter(description = "Identificador del tramo") @PathVariable Long id) {
		return service.porId(id);
	}

	@GetMapping("/coverage")
	@Operation(summary = "Obtener la cobertura disponible del grafo")
	public CoberturaDto coverage() {
		return service.cobertura();
	}
}
