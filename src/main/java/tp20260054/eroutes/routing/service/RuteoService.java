package tp20260054.eroutes.routing.service;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tp20260054.eroutes.mapeado.exception.RecursoNoEncontradoException;
import tp20260054.eroutes.routing.algorithm.Dijkstra;
import tp20260054.eroutes.routing.algorithm.ResultadoDijkstra;
import tp20260054.eroutes.routing.costo.CostoMulticriterio;
import tp20260054.eroutes.routing.costo.FuncionCosto;
import tp20260054.eroutes.routing.dto.PesosDto;
import tp20260054.eroutes.routing.dto.RutaDto;
import tp20260054.eroutes.routing.graph.GrafoEnMemoria;
import tp20260054.eroutes.routing.graph.GrafoProvider;
import tp20260054.eroutes.usuarios.UsuarioRepository;
import tp20260054.eroutes.usuarios.Vehiculo;
import tp20260054.eroutes.usuarios.VehiculoMapper;
import tp20260054.eroutes.usuarios.VehiculoRepository;
import tp20260054.eroutes.vehiculo.PerfilVehiculo;

@Service
@RequiredArgsConstructor
public class RuteoService {

    private final GrafoProvider grafoProvider;
    private final CostoMulticriterio costoMulticriterio;
    private final RutaBuilder rutaBuilder;
    private final Map<String, FuncionCosto> funcionesCosto;
    private final UsuarioRepository usuarioRepository;
    private final VehiculoRepository vehiculoRepository;

    @Value("${eroutes.routing.max-distancia-snap-m:200}")
    private int maxSnapM;

    @Value("${eroutes.routing.permitir-pesos-en-solicitud:false}")
    private boolean permitirPesosEnSolicitud;

    // -------------------------------------------------------------------------

    public RutaDto calcular(Long usuarioId,
                            double latOrigen, double lngOrigen,
                            double latDestino, double lngDestino,
                            String criterioRaw,
                            PesosDto pesosDto,
                            Long vehiculoId) {

        // Resolver el perfil del vehículo
        PerfilVehiculo perfil = resolverPerfil(usuarioId, vehiculoId);

        String criterio = (criterioRaw == null || criterioRaw.isBlank())
                ? "MULTICRITERIO" : criterioRaw.toUpperCase();

        FuncionCosto funcion = funcionesCosto.get(criterio);
        if (funcion == null) {
            throw new IllegalArgumentException(
                    "Criterio desconocido: '" + criterio
                    + "'. Valores válidos: MULTICRITERIO, DISTANCIA, TIEMPO");
        }

        if ("MULTICRITERIO".equals(criterio) && permitirPesosEnSolicitud && pesosDto != null) {
            funcion = costoMulticriterio.conPesos(pesosDto);
        }

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

        ResultadoDijkstra resultado = Dijkstra.calcular(grafo, funcion, idxOrigen, idxDestino);
        if (!resultado.tieneCamino()) {
            throw new SinCaminoException("No existe camino entre el origen y el destino indicados");
        }

        return rutaBuilder.construir(resultado, grafo, perfil, criterio,
                latOrigen, lngOrigen, distSnapOrigen,
                latDestino, lngDestino, distSnapDestino,
                idxOrigen, idxDestino);
    }

    // -------------------------------------------------------------------------

    private PerfilVehiculo resolverPerfil(Long usuarioId, Long vehiculoId) {
        var u = usuarioRepository.findByIdConVehiculoActivo(usuarioId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado"));

        Vehiculo v;
        if (vehiculoId != null) {
            // vehiculoId explícito: debe pertenecer al usuario
            v = vehiculoRepository.findByIdAndUsuarioId(vehiculoId, usuarioId)
                    .orElseThrow(() -> new RecursoNoEncontradoException(
                            "Vehículo no encontrado"));
        } else {
            v = u.getVehiculoActivo();
            if (v == null) {
                throw new PuntoFueraDeCoberturaException(
                        "Registra un vehículo para calcular rutas");
            }
        }
        return VehiculoMapper.toPerfilVehiculo(v);
    }
}
