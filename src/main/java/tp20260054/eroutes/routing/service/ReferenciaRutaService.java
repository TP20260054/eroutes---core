package tp20260054.eroutes.routing.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tp20260054.eroutes.routing.algorithm.Dijkstra;
import tp20260054.eroutes.routing.algorithm.ResultadoDijkstra;
import tp20260054.eroutes.routing.costo.CostoTiempo;
import tp20260054.eroutes.routing.costo.ReferenciaRuta;
import tp20260054.eroutes.routing.graph.GrafoEnMemoria;
import tp20260054.eroutes.routing.graph.GrafoProvider;
import tp20260054.eroutes.vehiculo.PerfilVehiculo;

/**
 * Calcula la {@link ReferenciaRuta} (T_ref, L_ref, c_ref) ejecutando Dijkstra con
 * costo {@code TIEMPO} bajo las mismas restricciones duras que el ruteo normal:
 * grafo por categoría del vehículo, snap al nodo más cercano y máx. distancia
 * de snap, y rechazo de origen==destino.
 *
 * <p>Si la ruta de referencia es degenerada (T_ref o L_ref = 0) lanza
 * {@link MismoNodoException}, el mismo error de dominio que para origen==destino.
 */
@Service
@RequiredArgsConstructor
public class ReferenciaRutaService {

    private final GrafoProvider grafoProvider;
    private final CostoTiempo costoTiempo;
    private final RutaBuilder rutaBuilder;

    @Value("${eroutes.routing.max-distancia-snap-m:200}")
    private int maxSnapM;

    public ReferenciaRuta calcular(PerfilVehiculo perfil,
                                   double latOrigen, double lngOrigen,
                                   double latDestino, double lngDestino) {

        GrafoEnMemoria grafo = grafoProvider.obtenerPorCategoria(perfil.categoriaReglamentaria());
        if (grafo == null) {
            throw new GrafoNoDisponibleException("El grafo de ruteo no está disponible aún");
        }

        int[] snapOrigen  = grafo.nodoMasCercano(latOrigen,  lngOrigen);
        int[] snapDestino = grafo.nodoMasCercano(latDestino, lngDestino);
        int idxOrigen  = snapOrigen[0];
        int idxDestino = snapDestino[0];
        int distSnapOrigen  = snapOrigen[1];
        int distSnapDestino = snapDestino[1];

        if (distSnapOrigen > maxSnapM) {
            throw new PuntoFueraDeCoberturaException(
                    "El origen está a " + distSnapOrigen + " m del nodo más cercano (máximo " + maxSnapM + " m)");
        }
        if (distSnapDestino > maxSnapM) {
            throw new PuntoFueraDeCoberturaException(
                    "El destino está a " + distSnapDestino + " m del nodo más cercano (máximo " + maxSnapM + " m)");
        }
        if (idxOrigen == idxDestino) {
            throw new MismoNodoException("El origen y el destino coinciden en el mismo nodo del grafo");
        }

        ResultadoDijkstra resultado = Dijkstra.calcular(grafo, costoTiempo, idxOrigen, idxDestino);
        if (!resultado.tieneCamino()) {
            throw new SinCaminoException("No existe camino entre el origen y el destino indicados");
        }

        var ruta = rutaBuilder.construir(resultado, grafo, perfil, "TIEMPO",
                latOrigen, lngOrigen, distSnapOrigen,
                latDestino, lngDestino, distSnapDestino,
                idxOrigen, idxDestino);

        double tRefS = ruta.tiempoEstimadoS();
        double lRefM = ruta.distanciaM();
        int cRef = ruta.metricas().crucesRiesgo();

        if (tRefS == 0.0 || lRefM == 0.0) {
            throw new MismoNodoException(
                    "La ruta de referencia es degenerada (T_ref o L_ref = 0)");
        }

        return new ReferenciaRuta(tRefS, lRefM, cRef);
    }
}
