package tp20260054.eroutes.busqueda;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import tp20260054.eroutes.busqueda.dto.CalleCercanaDto;
import tp20260054.eroutes.busqueda.dto.LugarCercanoDto;
import tp20260054.eroutes.busqueda.dto.ResultadoBusquedaDto;
import tp20260054.eroutes.busqueda.dto.UbicacionInfoDto;
import tp20260054.eroutes.mapeado.dto.Coordenada;

@Service
@RequiredArgsConstructor
public class BusquedaService {

    private final JdbcClient jdbcClient;
    private final BusquedaProperties props;

    /**
     * Busca calles y lugares cuyo nombre_norm contenga {@code qSinTipo}.
     *
     * @param qCompleta consulta normalizada completa
     * @param qSinTipo  consulta sin el prefijo de tipo de vía (puede ser igual a qCompleta)
     * @param lat       latitud de referencia (puede ser null)
     * @param lng       longitud de referencia (puede ser null)
     * @param limite    número máximo de resultados
     */
    public List<ResultadoBusquedaDto> buscar(
            String qCompleta, String qSinTipo, Double lat, Double lng, int limite) {

        boolean tieneRef = lat != null && lng != null;
        String likeParam = "%" + qSinTipo + "%";

        /*
         * Usamos una CTE para calcular el punto de referencia UNA sola vez,
         * evitando parámetros nombrados repetidos que confunden a JdbcClient.
         *
         * Si no hay referencia, la CTE devuelve NULL y el cálculo de distancia
         * devuelve NULL también.
         */
        String sql = buildSearchSql(tieneRef);

        var query = jdbcClient.sql(sql)
                .param("likeParam", likeParam)
                .param("qCompleta", qCompleta)
                .param("qCompletaLike", qCompleta + "%")
                .param("limite", limite);

        if (tieneRef) {
            String refWkt = String.format(java.util.Locale.US, "POINT(%f %f)", lng, lat);
            query = query.param("refWkt", refWkt);
        }

        return query.query((rs, rowNum) -> {
            String tipo = rs.getString("tipo");
            long idVal = rs.getLong("id");
            Long idResult = "LUGAR".equals(tipo) ? idVal : null;
            double latPunto = rs.getDouble("lat_punto");
            double lngPunto = rs.getDouble("lng_punto");
            Object distObj = rs.getObject("distancia_m");
            Integer distResult = (tieneRef && distObj != null) ? ((Number) distObj).intValue() : null;

            return new ResultadoBusquedaDto(
                    tipo,
                    idResult,
                    rs.getString("nombre"),
                    rs.getString("categoria"),
                    rs.getString("direccion"),
                    new Coordenada(latPunto, lngPunto),
                    distResult);
        }).list();
    }

    /**
     * Construye la SQL de búsqueda. Usa una CTE {@code ref} para calcular el punto
     * de referencia una sola vez a partir de un único parámetro {@code :refWkt}.
     */
    private String buildSearchSql(boolean tieneRef) {
        // CTE que expone el punto de referencia (o NULL si no hay referencia)
        String refCte = tieneRef
                ? "WITH ref AS (SELECT ST_GeomFromText(:refWkt, 4326) AS pt)"
                : "WITH ref AS (SELECT NULL::geometry AS pt)";

        return refCte + " "
                + "SELECT tipo, id, nombre, nombre_norm, categoria, direccion, lat_punto, lng_punto, distancia_m"
                + " FROM ("

                // ---- Calles ----
                + " SELECT 'CALLE' AS tipo,"
                + "        0       AS id,"
                + "        c.nombre,"
                + "        c.nombre_norm,"
                + "        NULL::VARCHAR AS categoria,"
                + "        NULL::VARCHAR AS direccion,"
                // punto: más cercano a referencia si existe, si no centroide
                + "        CASE WHEN ref.pt IS NOT NULL"
                + "             THEN ST_Y(ST_ClosestPoint(c.geom, ref.pt))"
                + "             ELSE ST_Y(ST_ClosestPoint(c.geom, ST_Centroid(c.geom)))"
                + "        END AS lat_punto,"
                + "        CASE WHEN ref.pt IS NOT NULL"
                + "             THEN ST_X(ST_ClosestPoint(c.geom, ref.pt))"
                + "             ELSE ST_X(ST_ClosestPoint(c.geom, ST_Centroid(c.geom)))"
                + "        END AS lng_punto,"
                // distancia
                + "        CASE WHEN ref.pt IS NOT NULL"
                + "             THEN ROUND(ST_Distance("
                + "                     ST_ClosestPoint(c.geom, ref.pt)::geography,"
                + "                     ref.pt::geography))::int"
                + "             ELSE NULL"
                + "        END AS distancia_m"
                + "   FROM calles c, ref"
                + "  WHERE c.nombre_norm LIKE :likeParam"

                + " UNION ALL"

                // ---- Lugares ----
                + " SELECT 'LUGAR' AS tipo,"
                + "        l.id,"
                + "        l.nombre,"
                + "        l.nombre_norm,"
                + "        l.categoria,"
                + "        l.direccion,"
                + "        ST_Y(l.geom) AS lat_punto,"
                + "        ST_X(l.geom) AS lng_punto,"
                + "        CASE WHEN ref.pt IS NOT NULL"
                + "             THEN ROUND(ST_Distance(l.geom::geography, ref.pt::geography))::int"
                + "             ELSE NULL"
                + "        END AS distancia_m"
                + "   FROM lugares l, ref"
                + "  WHERE l.nombre_norm LIKE :likeParam"

                + " ) sub"
                + " ORDER BY"
                + "   CASE WHEN nombre_norm LIKE :qCompletaLike THEN 0 ELSE 1 END ASC,"
                + "   similarity(nombre_norm, :qCompleta) DESC,"
                + "   distancia_m ASC NULLS LAST,"
                + "   nombre ASC"
                + " LIMIT :limite";
    }

    /**
     * Geocodificación inversa: devuelve la calle y el lugar más cercanos al punto.
     */
    public UbicacionInfoDto geocodificacionInversa(double lat, double lng) {
        String wkt = String.format(java.util.Locale.US, "POINT(%f %f)", lng, lat);
        double radioCalleGrados = props.radioCalleM() / 111_320.0;
        double radioLugarGrados = props.radioLugarM() / 111_320.0;

        // ¿El punto cae dentro del bbox del grafo?
        boolean dentroDeCobertura = Boolean.TRUE.equals(
                jdbcClient.sql(
                                "SELECT ST_Extent(geom) && ST_GeomFromText(:wkt, 4326) FROM tramos")
                        .param("wkt", wkt)
                        .query(Boolean.class)
                        .optional()
                        .orElse(false));

        // Calle más cercana sobre tramos con nombre
        // Prefiltra con el índice espacial (&&) y mide en geography
        CalleCercanaDto calleCercana = jdbcClient.sql(
                        "WITH ref AS (SELECT ST_GeomFromText(:wkt, 4326) AS pt)"
                        + " SELECT nombre,"
                        + "        ROUND(ST_Distance(geom::geography, ref.pt::geography))::int AS distancia_m"
                        + "   FROM tramos, ref"
                        + "  WHERE nombre IS NOT NULL"
                        + "    AND geom && ST_Expand(ref.pt, :radioGrados)"
                        + "  ORDER BY ST_Distance(geom::geography, ref.pt::geography) ASC"
                        + "  LIMIT 1")
                .param("wkt", wkt)
                .param("radioGrados", radioCalleGrados)
                .query((rs, rowNum) -> new CalleCercanaDto(
                        rs.getString("nombre"),
                        rs.getInt("distancia_m")))
                .optional()
                .filter(c -> c.distanciaM() <= props.radioCalleM())
                .orElse(null);

        // Lugar más cercano
        LugarCercanoDto lugarCercano = jdbcClient.sql(
                        "WITH ref AS (SELECT ST_GeomFromText(:wkt, 4326) AS pt)"
                        + " SELECT nombre, categoria,"
                        + "        ROUND(ST_Distance(geom::geography, ref.pt::geography))::int AS distancia_m"
                        + "   FROM lugares, ref"
                        + "  WHERE geom && ST_Expand(ref.pt, :radioGrados)"
                        + "  ORDER BY ST_Distance(geom::geography, ref.pt::geography) ASC"
                        + "  LIMIT 1")
                .param("wkt", wkt)
                .param("radioGrados", radioLugarGrados)
                .query((rs, rowNum) -> new LugarCercanoDto(
                        rs.getString("nombre"),
                        rs.getString("categoria"),
                        rs.getInt("distancia_m")))
                .optional()
                .filter(l -> l.distanciaM() <= props.radioLugarM())
                .orElse(null);

        return new UbicacionInfoDto(dentroDeCobertura, calleCercana, lugarCercano);
    }
}
