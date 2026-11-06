package tp20260054.eroutes.vehiculo;

import java.util.List;

/**
 * Perfil del vehículo con las reglas reglamentarias derivadas.
 *
 * @param modelo                  nombre del modelo
 * @param tipo                    tipo de vehículo (BICIMOTO, etc.)
 * @param velocidadMaximaKmh      velocidad máxima de construcción
 * @param categoriaReglamentaria  "AUTOMOTOR_MENOR_L" (vel > 25) o "MOVILIDAD_PERSONAL" (vel ≤ 25)
 * @param puedeUsarCiclovia       true si vel ≤ 25 km/h
 * @param tiposViaExcluidos       lista de tipos de vía no permitidos para este perfil
 */
public record PerfilVehiculo(
        String modelo,
        String tipo,
        int velocidadMaximaKmh,
        String categoriaReglamentaria,
        boolean puedeUsarCiclovia,
        List<String> tiposViaExcluidos) {
}
