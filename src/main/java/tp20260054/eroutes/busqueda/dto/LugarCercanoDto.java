package tp20260054.eroutes.busqueda.dto;

/**
 * Lugar más cercano a un punto.
 *
 * @param nombre      nombre del lugar
 * @param categoria   categoría del lugar (ej. amenity:restaurant)
 * @param distanciaM  distancia en metros al punto consultado
 */
public record LugarCercanoDto(String nombre, String categoria, int distanciaM) {
}
