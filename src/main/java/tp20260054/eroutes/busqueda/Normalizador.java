package tp20260054.eroutes.busqueda;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;

/**
 * Normalización de texto para búsqueda de calles y lugares.
 * <p>
 * Algoritmo: NFD → quitar diacríticos → minúsculas (ROOT) →
 * reemplazar todo carácter que no sea [a-z0-9 ] por espacio →
 * colapsar espacios → trim.
 */
public final class Normalizador {

    /** Prefijos de tipo de vía que se eliminan para generar la «forma sin tipo». */
    private static final Set<String> TIPOS_VIA = Set.of(
            "av", "avenida", "jr", "jiron", "ca", "calle",
            "psj", "pasaje", "alameda", "malecon");

    private Normalizador() {
    }

    /**
     * Normaliza una cadena según el algoritmo estándar del proyecto.
     *
     * @param texto cadena de entrada (puede ser null)
     * @return texto normalizado, o cadena vacía si la entrada es null/vacía
     */
    public static String normalizar(String texto) {
        if (texto == null || texto.isBlank()) {
            return "";
        }
        // 1. NFD + quitar marcas diacríticas (categoría Mn)
        String nfd = Normalizer.normalize(texto, Normalizer.Form.NFD);
        String sinTildes = nfd.replaceAll("\\p{Mn}", "");
        // 2. Minúsculas con Locale.ROOT
        String lower = sinTildes.toLowerCase(Locale.ROOT);
        // 3. Reemplazar todo carácter que no sea [a-z0-9 ] por espacio
        String limpio = lower.replaceAll("[^a-z0-9 ]", " ");
        // 4. Colapsar espacios múltiples y trim
        return limpio.replaceAll(" +", " ").trim();
    }

    /**
     * Genera la «forma sin tipo» para la consulta del usuario:
     * si la consulta tiene más de un token y el primero pertenece a los prefijos de vía,
     * devuelve la consulta sin ese primer token. Caso contrario devuelve la misma cadena.
     *
     * <p>Ejemplo: «av javier prado» → «javier prado»; «olivar» → «olivar»
     *
     * @param consultaNormalizada resultado de {@link #normalizar(String)} aplicado a la consulta
     * @return forma sin tipo para usar como filtro LIKE
     */
    public static String sinTipo(String consultaNormalizada) {
        if (consultaNormalizada == null || consultaNormalizada.isBlank()) {
            return consultaNormalizada;
        }
        String[] tokens = consultaNormalizada.split(" ");
        if (tokens.length > 1 && TIPOS_VIA.contains(tokens[0])) {
            // Elimina el primer token
            return consultaNormalizada.substring(tokens[0].length()).trim();
        }
        return consultaNormalizada;
    }
}
