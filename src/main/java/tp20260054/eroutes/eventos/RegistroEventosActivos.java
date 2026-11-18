package tp20260054.eroutes.eventos;

import java.util.List;
import tp20260054.eroutes.routing.costo.SnapshotEventos;

/**
 * Índice en memoria de eventos viales activos.
 *
 * <p>Expone dos vistas:
 * <ul>
 *   <li>{@link #instantanea()} — {@link SnapshotEventos} inmutable con los tramos
 *       bloqueados en este instante; se toma una vez por cálculo de ruta para
 *       garantizar consistencia durante todo el recorrido de Dijkstra/A*.
 *   <li>{@link #cercanos(double, double, double)} — lista de eventos cuyo punto
 *       ({@code geom}) está a ≤ {@code radioM} metros del punto consultado (haversine),
 *       usada para construir el vector de estado del DQL.
 * </ul>
 *
 * <p>Un escaneo lineal es aceptable en esta instancia piloto de baja escala.
 * La interfaz permite sustituir la implementación sin cambiar los consumidores.
 */
public interface RegistroEventosActivos {

    /** Snapshot inmutable del conjunto de tramos bloqueados en este momento. */
    SnapshotEventos instantanea();

    /**
     * Eventos activos cuyo punto geográfico está a ≤ {@code radioM} metros del
     * punto (lat, lng).
     */
    List<EventoVial> cercanos(double lat, double lng, double radioM);

    /** Añade un evento recién creado al índice (llamado por el servicio tras persistir). */
    void agregar(EventoVial evento);

    /** Marca un evento como resuelto/expirado y lo retira del índice. */
    void retirar(Long eventoId);
}
