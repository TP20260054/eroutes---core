package tp20260054.eroutes.routing.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tp20260054.eroutes.mapeado.dto.Coordenada;
import tp20260054.eroutes.routing.algorithm.Dijkstra;
import tp20260054.eroutes.routing.algorithm.ResultadoDijkstra;
import tp20260054.eroutes.routing.costo.CostoMulticriterio;
import tp20260054.eroutes.routing.costo.FuncionCosto;
import tp20260054.eroutes.routing.dto.ExtremoRutaDto;
import tp20260054.eroutes.routing.dto.MetricasRutaDto;
import tp20260054.eroutes.routing.dto.PerfilResumenDto;
import tp20260054.eroutes.routing.dto.PesosDto;
import tp20260054.eroutes.routing.dto.RutaDto;
import tp20260054.eroutes.routing.dto.TramoRutaDto;
import tp20260054.eroutes.routing.graph.Arista;
import tp20260054.eroutes.routing.graph.GrafoEnMemoria;
import tp20260054.eroutes.routing.graph.GrafoProvider;
import tp20260054.eroutes.vehiculo.PerfilVehiculo;
import tp20260054.eroutes.vehiculo.PerfilVehiculoProvider;

@Service
@RequiredArgsConstructor
public class RuteoService {

    private final GrafoProvider grafoProvider;
    private final PerfilVehiculoProvider perfilProvider;
    /** Spring inyecta todos los beans FuncionCosto indexados por su nombre de bean. */
    private final Map<String, FuncionCosto> funcionesCosto;
    private final CostoMulticriterio costoMulticriterio;

    @Value("${eroutes.routing.max-distancia-snap-m:200}")
    private int maxSnapM;

    @Value("${eroutes.routing.permitir-pesos-en-solicitud:false}")
    private boolean permitirPesosEnSolicitud;

    // -------------------------------------------------------------------------

    public RutaDto calcular(double latOrigen, double lngOrigen,
                            double latDestino, double lngDestino,
                            String criterioRaw) {
        return calcular(latOrigen, lngOrigen, latDestino, lngDestino, criterioRaw, null);
    }

    public RutaDto calcular(double latOrigen, double lngOrigen,
                            double latDestino, double lngDestino,
                            String criterioRaw, PesosDto pesosDto) {

        String criterio = (criterioRaw == null || criterioRaw.isBlank())
                ? "MULTICRITERIO" : criterioRaw.toUpperCase();

        FuncionCosto funcion = funcionesCosto.get(criterio);
        if (funcion == null) {
            throw new IllegalArgumentException(
                    "Criterio desconocido: '" + criterio
                    + "'. Valores válidos: MULTICRITERIO, DISTANCIA, TIEMPO");
        }

        // Aplicar pesos de la solicitud solo para MULTICRITERIO y si está habilitado
        if ("MULTICRITERIO".equals(criterio) && permitirPesosEnSolicitud && pesosDto != null) {
            funcion = costoMulticriterio.conPesos(pesosDto);
        }

        PerfilVehiculo perfil = perfilProvider.perfil();
        GrafoEnMemoria grafo = grafoProvider.obtenerPorCategoria(perfil.categoriaReglamentaria());
        if (grafo == null) {
            throw new GrafoNoDisponibleException("El grafo de ruteo no está disponible aún");
        }

        // Snap de origen y destino al nodo más cercano
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

        // Dijkstra
        ResultadoDijkstra resultado = Dijkstra.calcular(grafo, funcion, idxOrigen, idxDestino);
        if (!resultado.tieneCamino()) {
            throw new SinCaminoException("No existe camino entre el origen y el destino indicados");
        }

        return construirRuta(resultado, grafo, perfil, criterio,
                latOrigen, lngOrigen, distSnapOrigen,
                latDestino, lngDestino, distSnapDestino,
                idxOrigen, idxDestino);
    }

    // -------------------------------------------------------------------------

    private RutaDto construirRuta(
            ResultadoDijkstra resultado, GrafoEnMemoria grafo,
            PerfilVehiculo perfil, String criterio,
            double latOrigen, double lngOrigen, int distSnapOrigen,
            double latDestino, double lngDestino, int distSnapDestino,
            int idxOrigen, int idxDestino) {

        List<Arista> aristas = resultado.aristas();

        // Polilínea: concatenar geometrías sin duplicar puntos de unión
        List<Coordenada> coordenadas = new ArrayList<>();
        for (int i = 0; i < aristas.size(); i++) {
            double[][] geom = aristas.get(i).geom();
            int inicio = (i == 0) ? 0 : 1;
            for (int j = inicio; j < geom.length; j++) {
                coordenadas.add(new Coordenada(geom[j][0], geom[j][1]));
            }
        }

        // Tramos
        List<TramoRutaDto> tramos = aristas.stream()
                .map(a -> new TramoRutaDto(a.tramoId(), a.nombre(), a.tipoVia(), a.longitudM()))
                .toList();

        // Métricas
        double distanciaTotal = aristas.stream().mapToDouble(Arista::longitudM).sum();

        // Tiempo estimado con velEfectiva del multicriterio
        double tiempoSeg = aristas.stream()
                .mapToDouble(a -> {
                    double vel = costoMulticriterio.velEfectiva(a);
                    double velUsada = Math.min(vel, perfil.velocidadMaximaKmh());
                    return a.longitudM() / (velUsada / 3.6);
                })
                .sum();

        // Exposición promedio ponderada por longitud
        double sumaExpoLong = 0, sumaLong = 0;
        for (Arista a : aristas) {
            double expBase = exposicionBase(a.tipoVia());
            double expo = a.velocidadMax() != null
                    ? Math.min(1.0, expBase * clamp(a.velocidadMax() / 50.0, 0.6, 1.2))
                    : expBase;
            sumaExpoLong += expo * a.longitudM();
            sumaLong += a.longitudM();
        }
        double exposicionPromedio = sumaLong > 0 ? sumaExpoLong / sumaLong : 0.0;

        // Tramos no validados
        double tramosNoValM = aristas.stream()
                .filter(a -> !a.validado())
                .mapToDouble(Arista::longitudM).sum();
        double pctNoVal = distanciaTotal > 0 ? (tramosNoValM / distanciaTotal) * 100.0 : 0.0;

        // Cruces de riesgo
        int crucesRiesgo = 0;
        for (Arista arista : aristas) {
            if (grafo.nodo(arista.destino()).riesgoCruce() >= 0.5) crucesRiesgo++;
        }

        // Extremos
        var nOrigen  = grafo.nodo(idxOrigen);
        var nDestino = grafo.nodo(idxDestino);
        ExtremoRutaDto extramoOrigen = new ExtremoRutaDto(
                new Coordenada(latOrigen, lngOrigen),
                new Coordenada(nOrigen.lat(), nOrigen.lng()),
                distSnapOrigen);
        ExtremoRutaDto extramoDestino = new ExtremoRutaDto(
                new Coordenada(latDestino, lngDestino),
                new Coordenada(nDestino.lat(), nDestino.lng()),
                distSnapDestino);

        MetricasRutaDto metricas = new MetricasRutaDto(
                Math.round(exposicionPromedio * 1000.0) / 1000.0,
                Math.round(tramosNoValM * 10.0) / 10.0,
                Math.round(pctNoVal * 10.0) / 10.0,
                crucesRiesgo,
                Math.round(resultado.costoTotal() * 1000.0) / 1000.0);

        PerfilResumenDto perfilDto = new PerfilResumenDto(
                perfil.modelo(), perfil.tipo(), perfil.velocidadMaximaKmh());

        return new RutaDto(criterio, perfilDto,
                extramoOrigen, extramoDestino,
                Math.round(distanciaTotal * 10.0) / 10.0,
                Math.round(tiempoSeg * 10.0) / 10.0,
                metricas, coordenadas, tramos);
    }

    // -------------------------------------------------------------------------

    private double exposicionBase(String tipoVia) {
        if (tipoVia == null) return 0.5;
        return switch (tipoVia) {
            case "primary",   "primary_link"   -> 1.0;
            case "secondary", "secondary_link" -> 0.8;
            case "tertiary",  "tertiary_link"  -> 0.5;
            case "unclassified"                -> 0.35;
            case "residential"                 -> 0.25;
            case "living_street"               -> 0.1;
            case "service"                     -> 0.2;
            default                            -> 0.5;
        };
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}
