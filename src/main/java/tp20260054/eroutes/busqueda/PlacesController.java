package tp20260054.eroutes.busqueda;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tp20260054.eroutes.busqueda.dto.ResultadoBusquedaDto;
import tp20260054.eroutes.busqueda.dto.UbicacionInfoDto;

@RestController
@RequestMapping("/api/v1/places")
@RequiredArgsConstructor
@Validated
@Tag(name = "Búsqueda", description = "Búsqueda de calles y lugares, geocodificación inversa")
public class PlacesController {

    private final BusquedaService service;
    private final BusquedaProperties props;

    @GetMapping("/search")
    @Operation(summary = "Buscar calles y lugares por nombre")
    public List<ResultadoBusquedaDto> search(
            @Parameter(description = "Texto de búsqueda (2-80 caracteres tras normalizar)")
            @RequestParam String q,
            @Parameter(description = "Latitud de referencia (debe ir acompañada de lng)")
            @RequestParam(required = false) Double lat,
            @Parameter(description = "Longitud de referencia (debe ir acompañada de lat)")
            @RequestParam(required = false) Double lng,
            @Parameter(description = "Máximo de resultados (default " + "8" + ", max 20)")
            @RequestParam(required = false) Integer limit) {

        // Validar que lat y lng se dan juntos o ninguno
        if ((lat == null) != (lng == null)) {
            throw new IllegalArgumentException(
                    "Los parámetros lat y lng deben proporcionarse juntos o ninguno de los dos");
        }

        // Validar rango lat/lng
        if (lat != null) {
            validateLatLng(lat, lng);
        }

        // Normalizar la consulta
        String qCompleta = Normalizador.normalizar(q);
        if (qCompleta.length() < 2 || qCompleta.length() > 80) {
            throw new IllegalArgumentException(
                    "El parámetro q debe tener entre 2 y 80 caracteres tras normalizar");
        }

        String qSinTipo = Normalizador.sinTipo(qCompleta);

        // Aplicar límite
        int limiteEfectivo = (limit == null) ? props.limitePorDefecto() : limit;
        if (limiteEfectivo > props.limiteMaximo()) {
            limiteEfectivo = props.limiteMaximo();
        }
        if (limiteEfectivo < 1) {
            limiteEfectivo = 1;
        }

        return service.buscar(qCompleta, qSinTipo, lat, lng, limiteEfectivo);
    }

    @GetMapping("/reverse")
    @Operation(summary = "Geocodificación inversa: calles y lugares cercanos a un punto")
    public UbicacionInfoDto reverse(
            @Parameter(description = "Latitud del punto", required = true)
            @RequestParam double lat,
            @Parameter(description = "Longitud del punto", required = true)
            @RequestParam double lng) {

        validateLatLng(lat, lng);
        return service.geocodificacionInversa(lat, lng);
    }

    private void validateLatLng(double lat, double lng) {
        if (!Double.isFinite(lat) || lat < -90 || lat > 90) {
            throw new IllegalArgumentException(
                    "El parámetro lat debe estar en el rango [-90, 90]");
        }
        if (!Double.isFinite(lng) || lng < -180 || lng > 180) {
            throw new IllegalArgumentException(
                    "El parámetro lng debe estar en el rango [-180, 180]");
        }
    }
}
