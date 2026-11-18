package tp20260054.eroutes.recalculo;

import java.util.List;
import tp20260054.eroutes.eventos.TipoEventoVial;
import tp20260054.eroutes.navegacion.DisparadorRecalculo;

/**
 * Estado observado en el momento del recálculo (OB2 3.4).
 *
 * <p>Contiene:
 * <ul>
 *   <li>Los 14 valores normalizados del vector de estado (en el orden exacto
 *       definido por {@link #NOMBRES_FEATURES}).
 *   <li>Campos crudos que NO van al vector pero se persisten en
 *       {@code recalculos.estado_entrada}: {@code eventoEnRutaRestante},
 *       {@code eventoBloquea}, {@code disparador} y {@code eventoId}.
 * </ul>
 *
 * <p>Las constantes de normalización vienen de {@link RecalculoProperties} y se
 * validan contra el archivo del modelo en {@link MlpModel}.
 *
 * <h3>Orden exacto del vector (14 dimensiones)</h3>
 * <pre>
 * [0]  desvio                       — 1 si desvío activo, 0 si no
 * [1]  ev_bloqueo_total             — one-hot del tipo de evento relevante
 * [2]  ev_obra
 * [3]  ev_baches_severos
 * [4]  ev_vehiculo_en_ciclovia
 * [5]  gravedad                     — gravedad del evento relevante [0,1]; 0 si no hay
 * [6]  dist_evento_ruta             — distancia normalizada a lo largo de la ruta; 1 si no hay
 * [7]  velocidad_rel                — velocidad/velMax [0,1]
 * [8]  perfil_bicicleta_electrica   — one-hot de TipoVehiculo
 * [9]  perfil_scooter_electrico
 * [10] perfil_bicimoto
 * [11] perfil_moto_electrica
 * [12] long_restante                — longitud restante normalizada [0,1]
 * [13] recalculos_recientes         — recálculos en ventana normalizados [0,1]
 * </pre>
 */
public record EstadoRecalculo(

        // ---- Disparador y flags crudos (no van al vector) ----
        DisparadorRecalculo disparador,
        boolean eventoEnRutaRestante,
        boolean eventoBloquea,
        Long eventoId,          // null si no hay evento relevante

        // ---- Valores del vector ----
        boolean desvio,

        // One-hot tipo de evento (índices 1–4)
        TipoEventoVial tipoEvento,  // null si no hay evento

        double gravedad,            // [0,1]; 0 si no hay evento
        double distEventoRuta,      // [0,1]; 1 si no hay evento en ruta restante
        double velocidadRel,        // [0,1]

        // One-hot perfil (índices 8–11): derivado de tipo de vehículo
        String tipoVehiculo,        // nombre del enum TipoVehiculo

        double longRestante,        // [0,1]
        double recalculosRecientes  // [0,1]

) {

    // -------------------------------------------------------------------------
    // Nombres de features en orden exacto (contrato con el modelo)
    // -------------------------------------------------------------------------

    public static final List<String> NOMBRES_FEATURES = List.of(
            "desvio",
            "ev_bloqueo_total",
            "ev_obra",
            "ev_baches_severos",
            "ev_vehiculo_en_ciclovia",
            "gravedad",
            "dist_evento_ruta",
            "velocidad_rel",
            "perfil_bicicleta_electrica",
            "perfil_scooter_electrico",
            "perfil_bicimoto",
            "perfil_moto_electrica",
            "long_restante",
            "recalculos_recientes"
    );

    // -------------------------------------------------------------------------
    // Vector de 14 dimensiones, todo en [0, 1]
    // -------------------------------------------------------------------------

    public double[] vector() {
        double[] v = new double[14];

        // [0] desvio
        v[0] = desvio ? 1.0 : 0.0;

        // [1–4] one-hot tipo de evento
        if (tipoEvento != null) {
            v[1] = tipoEvento == TipoEventoVial.BLOQUEO_TOTAL        ? 1.0 : 0.0;
            v[2] = tipoEvento == TipoEventoVial.OBRA                 ? 1.0 : 0.0;
            v[3] = tipoEvento == TipoEventoVial.BACHES_SEVEROS       ? 1.0 : 0.0;
            v[4] = tipoEvento == TipoEventoVial.VEHICULO_EN_CICLOVIA ? 1.0 : 0.0;
        }
        // [1–4] ya son 0 por defecto si tipoEvento == null

        // [5] gravedad
        v[5] = gravedad;

        // [6] dist_evento_ruta (ya normalizada; 1.0 si no hay evento en ruta)
        v[6] = distEventoRuta;

        // [7] velocidad_rel
        v[7] = velocidadRel;

        // [8–11] one-hot perfil vehículo
        if (tipoVehiculo != null) {
            v[8]  = "BICICLETA_ELECTRICA".equals(tipoVehiculo) ? 1.0 : 0.0;
            v[9]  = "SCOOTER_ELECTRICO".equals(tipoVehiculo)   ? 1.0 : 0.0;
            v[10] = "BICIMOTO".equals(tipoVehiculo)            ? 1.0 : 0.0;
            v[11] = "MOTO_ELECTRICA".equals(tipoVehiculo)      ? 1.0 : 0.0;
        }

        // [12] long_restante
        v[12] = longRestante;

        // [13] recalculos_recientes
        v[13] = recalculosRecientes;

        return v;
    }
}
