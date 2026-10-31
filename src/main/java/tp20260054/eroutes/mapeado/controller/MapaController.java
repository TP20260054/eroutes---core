package tp20260054.eroutes.mapeado.controller;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.constraints.Max;
import tp20260054.eroutes.mapeado.dto.TramoDto;
import tp20260054.eroutes.mapeado.service.TramoService;

@RestController
@RequestMapping("/api/v1/map")
@RequiredArgsConstructor
@Validated
public class MapaController {

	private final TramoService service;

	@GetMapping("/segments")
	public List<TramoDto> segments(
			@RequestParam double minLat, @RequestParam double minLng,
			@RequestParam double maxLat, @RequestParam double maxLng,
			@RequestParam(defaultValue = "2000") @Max(5000) int limit) {
		return service.porBbox(minLat, minLng, maxLat, maxLng, limit);
	}
}
