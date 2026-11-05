package tp20260054.eroutes.busqueda.dto;

/**
 * Respuesta del endpoint de geocodificación inversa.
 *
 * @param dentroDeCobertura indica si el punto cae dentro de la extensión del grafo
 * @param calle             calle más cercana dentro del radio configurado, o null
 * @param lugar             lugar más cercano dentro del radio configurado, o null
 */
public record UbicacionInfoDto(
        boolean dentroDeCobertura,
        CalleCercanaDto calle,
        LugarCercanoDto lugar) {
}
