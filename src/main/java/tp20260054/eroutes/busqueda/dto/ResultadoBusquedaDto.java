package tp20260054.eroutes.busqueda.dto;

import tp20260054.eroutes.mapeado.dto.Coordenada;

/**
 * Resultado de una búsqueda de calles o lugares.
 *
 * @param tipo        "CALLE" o "LUGAR"
 * @param id          id del lugar (null para calles)
 * @param nombre      nombre para mostrar
 * @param categoria   categoría (ej. amenity:restaurant); null para calles
 * @param direccion   dirección textual; null si no aplica
 * @param punto       punto representativo del resultado
 * @param distanciaM  distancia en metros a la referencia, o null si no se proporcionó referencia
 */
public record ResultadoBusquedaDto(
        String tipo,
        Long id,
        String nombre,
        String categoria,
        String direccion,
        Coordenada punto,
        Integer distanciaM) {
}
