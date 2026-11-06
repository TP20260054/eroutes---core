package tp20260054.eroutes.routing.costo;

import org.springframework.stereotype.Component;
import tp20260054.eroutes.routing.graph.Arista;

/** Función de costo basada únicamente en distancia. Línea base de comparación. */
@Component("DISTANCIA")
public class CostoDistancia implements FuncionCosto {
    @Override
    public double costo(Arista arista, ContextoNodo destino) {
        return arista.longitudM();
    }
}
