package tp20260054.eroutes.usuarios;

import tp20260054.eroutes.usuarios.dto.VehiculoDto;

/** Convierte Vehiculo → VehiculoDto calculando campos derivados. */
public final class VehiculoMapper {

    private VehiculoMapper() {}

    public static VehiculoDto toDto(Vehiculo v, Long vehiculoActivoId) {
        boolean esMotorizado = v.getVelocidadMaximaKmh() > 25;
        String categoria = esMotorizado ? "AUTOMOTOR_MENOR_L" : "MOVILIDAD_PERSONAL";
        boolean puedeUsarCiclovia = !esMotorizado;
        boolean activo = v.getId() != null && v.getId().equals(vehiculoActivoId);
        return new VehiculoDto(
                v.getId(),
                v.getAlias(),
                v.getMarca(),
                v.getModelo(),
                v.getTipo(),
                v.getVelocidadMaximaKmh(),
                v.getPlaca(),
                categoria,
                puedeUsarCiclovia,
                activo);
    }

    /**
     * Construye un {@link tp20260054.eroutes.vehiculo.PerfilVehiculo} aplicando
     * las reglas reglamentarias del enunciado.
     */
    public static tp20260054.eroutes.vehiculo.PerfilVehiculo toPerfilVehiculo(Vehiculo v) {
        boolean esMotorizado = v.getVelocidadMaximaKmh() > 25;
        String categoria = esMotorizado ? "AUTOMOTOR_MENOR_L" : "MOVILIDAD_PERSONAL";
        boolean puedeUsarCiclovia = !esMotorizado;
        java.util.List<String> excluidos = esMotorizado
                ? java.util.List.of("footway","pedestrian","path","steps","corridor",
                    "bridleway","cycleway","motorway","motorway_link","trunk","trunk_link")
                : java.util.List.of("footway","pedestrian","path","steps","corridor",
                    "bridleway","motorway","motorway_link","trunk","trunk_link");
        String modelo = v.getMarca() != null && !v.getMarca().isBlank()
                ? v.getMarca().trim() + " " + v.getModelo().trim()
                : v.getModelo().trim();
        return new tp20260054.eroutes.vehiculo.PerfilVehiculo(
                modelo, v.getTipo().name(), v.getVelocidadMaximaKmh(),
                categoria, puedeUsarCiclovia, excluidos);
    }
}
