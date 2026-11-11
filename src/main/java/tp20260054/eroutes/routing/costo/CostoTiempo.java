package tp20260054.eroutes.routing.costo;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tp20260054.eroutes.routing.graph.Arista;

/** Función de costo basada únicamente en tiempo estimado. Línea base de comparación. */
@Component("TIEMPO")
@RequiredArgsConstructor
public class CostoTiempo implements FuncionCosto {

    private final CostoMulticriterio multicriterio;

    @Override
    public double costo(Arista arista, ContextoNodo destino) {
        // Usa la misma fórmula que el multicriterio (factor + demora), solo tiempo
        // velVehiculo 45 km/h; en Dijkstra no es el nodo final
        return multicriterio.tiempoAristaS(
                arista,
                destino != null ? destino.info() : null,
                45.0,
                false);
    }
}
