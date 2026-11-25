package tp20260054.eroutes.recalculo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import tp20260054.eroutes.eventos.EventoVial;
import tp20260054.eroutes.eventos.TipoEventoVial;
import tp20260054.eroutes.routing.graph.Arista;
import tp20260054.eroutes.routing.graph.GrafoEnMemoria;

/**
 * Distancia real (haversine) a la geometría del evento y selección del evento relevante.
 * Sin Spring ni base de datos.
 */
class EventoRelevanteTest {

    private static final GeometryFactory GF = new GeometryFactory(new PrecisionModel(), 4326);
    private static final double LAT = -12.0931;
    private static final double LNG = -77.0465;

    private static EventoVial evento(long id, long tramoId, double gravedad, double lat, double lng) {
        EventoVial ev = new EventoVial();
        ev.setId(id);
        ev.setTipo(TipoEventoVial.OBRA);
        ev.setGravedad(gravedad);
        ev.setTramoId(tramoId);
        ev.setGeom(GF.createPoint(new Coordinate(lng, lat)));
        return ev;
    }

    private static Arista aristaRuta(long tramoId) {
        return new Arista(1, tramoId, null, "residential", 100.0, 30, true, false,
                new double[][]{{LAT, LNG}, {LAT, LNG + 0.001}});
    }

    // ---------------------------------------------------------------- distancia

    @Test
    void distanciaAGeometria_punto_coincideConHaversine() {
        var punto = GF.createPoint(new Coordinate(LNG + 0.0007, LAT + 0.0003));

        double d = GeoRuta.distanciaAGeometriaM(LAT, LNG, punto);

        assertThat(d).isEqualTo(GrafoEnMemoria.haversine(LAT, LNG, LAT + 0.0003, LNG + 0.0007));
        assertThat(d).isBetween(80.0, 90.0); // ~84.7 m
    }

    @Test
    void distanciaAGeometria_milGradoDeLatitud_sonUnos111Metros() {
        var punto = GF.createPoint(new Coordinate(LNG, LAT + 0.001));

        assertThat(GeoRuta.distanciaAGeometriaM(LAT, LNG, punto)).isCloseTo(111.19, within(0.5));
    }

    @Test
    void distanciaAGeometria_linea_usaElPuntoMasCercano() {
        var linea = GF.createLineString(new Coordinate[]{
                new Coordinate(LNG, LAT + 0.0010),    // ~111 m
                new Coordinate(LNG, LAT + 0.0002)});  // ~22 m

        assertThat(GeoRuta.distanciaAGeometriaM(LAT, LNG, linea)).isCloseTo(22.24, within(0.5));
    }

    @Test
    void distanciaAGeometria_nulaOVacia_esMaxValue() {
        assertThat(GeoRuta.distanciaAGeometriaM(LAT, LNG, null)).isEqualTo(Double.MAX_VALUE);
        assertThat(GeoRuta.distanciaAGeometriaM(LAT, LNG, GF.createPoint())).isEqualTo(Double.MAX_VALUE);
    }

    // ---------------------------------------------------------------- selección

    @Test
    void fueraDeRuta_eligeElMasCercano_aunqueTengaMenorGravedad() {
        EventoVial lejosGrave = evento(1, 98, 1.0, LAT + 0.0010, LNG);   // ~111 m
        EventoVial cercaLeve = evento(2, 99, 0.2, LAT + 0.0005, LNG);    // ~56 m

        EventoVial elegido = RecalculoService.seleccionarEventoRelevante(
                List.of(lejosGrave, cercaLeve), List.of(aristaRuta(1L)), 0, LAT, LNG);

        assertThat(elegido).isSameAs(cercaLeve);
    }

    @Test
    void fueraDeRuta_elOrdenDeLaListaNoImporta() {
        EventoVial lejos = evento(1, 98, 0.5, LAT, LNG + 0.0010);
        EventoVial cerca = evento(2, 99, 0.5, LAT, LNG + 0.0002);

        assertThat(RecalculoService.seleccionarEventoRelevante(
                List.of(lejos, cerca), List.of(aristaRuta(1L)), 0, LAT, LNG)).isSameAs(cerca);
        assertThat(RecalculoService.seleccionarEventoRelevante(
                List.of(cerca, lejos), List.of(aristaRuta(1L)), 0, LAT, LNG)).isSameAs(cerca);
    }

    @Test
    void fueraDeRuta_aIgualDistancia_desempataPorMayorGravedad() {
        EventoVial leve = evento(1, 98, 0.25, LAT + 0.0005, LNG);
        EventoVial grave = evento(2, 99, 0.75, LAT + 0.0005, LNG);

        EventoVial elegido = RecalculoService.seleccionarEventoRelevante(
                List.of(leve, grave), List.of(aristaRuta(1L)), 0, LAT, LNG);

        assertThat(elegido).isSameAs(grave);
    }

    @Test
    void eventoEnLaRuta_tienePrioridadSobreUnoMasCercanoFueraDeRuta() {
        EventoVial enRuta = evento(1, 1L, 0.5, LAT, LNG + 0.0008);        // tramo de la ruta
        EventoVial fueraMuyCerca = evento(2, 99L, 1.0, LAT + 0.0001, LNG);

        EventoVial elegido = RecalculoService.seleccionarEventoRelevante(
                List.of(fueraMuyCerca, enRuta), List.of(aristaRuta(1L)), 0, LAT, LNG);

        assertThat(elegido).isSameAs(enRuta);
    }

    @Test
    void sinEventos_devuelveNull() {
        assertThat(RecalculoService.seleccionarEventoRelevante(
                List.of(), List.of(aristaRuta(1L)), 0, LAT, LNG)).isNull();
    }
}
