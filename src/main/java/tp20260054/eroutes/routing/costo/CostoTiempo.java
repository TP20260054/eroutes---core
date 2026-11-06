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
        double velKmh = multicriterio.velEfectiva(arista);
        return arista.longitudM() / (velKmh / 3.6);
    }
}
