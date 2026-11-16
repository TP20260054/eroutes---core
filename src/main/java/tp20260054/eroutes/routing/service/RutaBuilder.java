package tp20260054.eroutes.routing.service;

import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tp20260054.eroutes.mapeado.dto.Coordenada;
import tp20260054.eroutes.routing.algorithm.ResultadoDijkstra;
import tp20260054.eroutes.routing.costo.CostoMulticriterio;
import tp20260054.eroutes.routing.dto.ExtremoRutaDto;
import tp20260054.eroutes.routing.dto.MetricasRutaDto;
import tp20260054.eroutes.routing.dto.PerfilResumenDto;
import tp20260054.eroutes.routing.dto.RutaDto;
import tp20260054.eroutes.routing.dto.TramoRutaDto;
import tp20260054.eroutes.routing.graph.Arista;
import tp20260054.eroutes.routing.graph.GrafoEnMemoria;
import tp20260054.eroutes.vehiculo.PerfilVehiculo;

/**
 * Construye el {@link RutaDto} a partir de un resultado de Dijkstra.
 *
 * <p>Extraído de {@code RuteoService.construirRuta} para reutilizarlo desde otros
 * servicios (p. ej. el cálculo de la ruta de referencia). Comportamiento idéntico
 * al método original.
 */
@Component
@RequiredArgsConstructor
public class RutaBuilder {

    private final CostoMulticriterio costoMulticriterio;

    public RutaDto construir(
            ResultadoDijkstra resultado, GrafoEnMemoria grafo,
            PerfilVehiculo perfil, String criterio,
            double latOrigen, double lngOrigen, int distSnapOrigen,
            double latDestino, double lngDestino, int distSnapDestino,
            int idxOrigen, int idxDestino) {

        List<Arista> aristas = resultado.aristas();

        List<Coordenada> coordenadas = new ArrayList<>();
        for (int i = 0; i < aristas.size(); i++) {
            double[][] geom = aristas.get(i).geom();
            int inicio = (i == 0) ? 0 : 1;
            for (int j = inicio; j < geom.length; j++) {
                coordenadas.add(new Coordenada(geom[j][0], geom[j][1]));
            }
        }

        List<TramoRutaDto> tramos = new ArrayList<>();
        for (Arista a : aristas) {
            double[][] geom = a.geom();
            List<Coordenada> coordsTramo = new ArrayList<>(geom.length);
            for (double[] pt : geom) {
                coordsTramo.add(new Coordenada(pt[0], pt[1]));
            }
            tramos.add(new TramoRutaDto(a.tramoId(), a.nombre(), a.tipoVia(),
                    a.longitudM(), coordsTramo));
        }

        double distanciaTotal = aristas.stream().mapToDouble(Arista::longitudM).sum();

        double tiempoSeg = 0.0;
        for (int i = 0; i < aristas.size(); i++) {
            Arista a = aristas.get(i);
            boolean esUltimo = (i == aristas.size() - 1);
            var destinoInfo = grafo.nodo(a.destino());
            tiempoSeg += costoMulticriterio.tiempoAristaS(
                    a, destinoInfo, perfil.velocidadMaximaKmh(), esUltimo);
        }

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

        double tramosNoValM = aristas.stream()
                .filter(a -> !a.validado())
                .mapToDouble(Arista::longitudM).sum();
        double pctNoVal = distanciaTotal > 0 ? (tramosNoValM / distanciaTotal) * 100.0 : 0.0;

        int crucesRiesgo = 0;
        for (Arista arista : aristas) {
            if (grafo.nodo(arista.destino()).riesgoCruce() >= 0.5) crucesRiesgo++;
        }

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
