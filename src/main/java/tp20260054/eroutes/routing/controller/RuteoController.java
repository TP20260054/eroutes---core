package tp20260054.eroutes.routing.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tp20260054.eroutes.routing.dto.RutaDto;
import tp20260054.eroutes.routing.dto.SolicitudRutaDto;
import tp20260054.eroutes.routing.service.RuteoService;

@RestController
@RequestMapping("/api/v1/routes")
@RequiredArgsConstructor
@Tag(name = "Ruteo", description = "Cálculo de rutas con función de costo multicriterio")
public class RuteoController {

    private final RuteoService service;

    @PostMapping
    @Operation(summary = "Calcular ruta entre dos puntos")
    public RutaDto calcular(@Valid @RequestBody SolicitudRutaDto solicitud) {
        return service.calcular(
                solicitud.origen().lat(),
                solicitud.origen().lng(),
                solicitud.destino().lat(),
                solicitud.destino().lng(),
                solicitud.criterio(),
                solicitud.pesos());
    }
}
