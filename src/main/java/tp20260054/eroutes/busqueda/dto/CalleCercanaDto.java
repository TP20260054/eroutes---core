package tp20260054.eroutes.busqueda.dto;

/**
 * Calle más cercana a un punto.
 *
 * @param nombre      nombre de la calle
 * @param distanciaM  distancia en metros al punto consultado
 */
public record CalleCercanaDto(String nombre, int distanciaM) {
}
