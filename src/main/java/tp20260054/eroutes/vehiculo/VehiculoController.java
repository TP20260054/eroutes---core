package tp20260054.eroutes.vehiculo;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/vehiculos")
@RequiredArgsConstructor
@Tag(name = "Vehículo", description = "Perfil del vehículo activo")
public class VehiculoController {

    private final PerfilVehiculoProvider provider;

    @GetMapping("/perfil")
    @Operation(summary = "Obtener el perfil del vehículo activo")
    public PerfilVehiculo perfil() {
        return provider.perfil();
    }
}
