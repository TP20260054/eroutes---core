package tp20260054.eroutes.vehiculo;

import java.util.List;

/**
 * Proveedor del perfil de vehículo activo.
 * Más adelante será implementado con el vehículo del usuario autenticado.
 */
public interface PerfilVehiculoProvider {
    PerfilVehiculo perfil();

    /**
     * Devuelve los tipos de vía excluidos para una categoría reglamentaria dada,
     * sin necesidad de construir un perfil completo.
     * Usado por {@code GrafoProvider} para construir grafos por categoría.
     */
    List<String> tiposExcluidosPorCategoria(String categoriaReglamentaria);
}
