package tp20260054.eroutes.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;
import tp20260054.eroutes.routing.costo.CostoMulticriterio;
import tp20260054.eroutes.routing.costo.CostoProperties;
import tp20260054.eroutes.routing.graph.Arista;
import tp20260054.eroutes.routing.graph.NodoInfo;

/**
 * Tests unitarios del cálculo de tiempo: factor de velocidad efectiva y demoras por nodo.
 * Sin Spring; construye los objetos directamente.
 */
class CostoTiempoTest {

    private static final double[][] GEOM = {{0.0, 0.0}, {1.0, 1.0}};

    /** Construye un CostoMulticriterio con parámetros de test controlados. */
    private static CostoMulticriterio multicriterio(double factor,
            double demoraSignals, double demoraStop,
            double demorarossing, double demoraSinControl) {

        CostoProperties props = new CostoProperties(
                new CostoProperties.Pesos(1, 1, 1, 0.5, 5),
                new CostoProperties.VelDefecto(50, 50, 40, 30, 30, 20, 20, 30, 10),
                new CostoProperties.Exposicion(1.0, 0.8, 0.5, 0.35, 0.25, 0.1, 0.2, 0.5),
                factor,
                new CostoProperties.DemoraNode(demoraSignals, demoraStop, demorarossing, demoraSinControl));
        return new CostoMulticriterio(props);
    }

    private static Arista arista(String tipoVia, double longM) {
        return new Arista(1, 99L, null, tipoVia, longM, null, false, false, GEOM);
    }

    private static NodoInfo nodo(String control, double riesgo) {
        return new NodoInfo(1L, 0, 0, control, riesgo);
    }

    // ---- Factor de velocidad efectiva ----

    @Test
    void factorVelocidadEfectiva_reduceTiempo() {
        CostoMulticriterio mc1 = multicriterio(1.0, 0, 0, 0, 0); // sin factor
        CostoMulticriterio mc06 = multicriterio(0.6, 0, 0, 0, 0); // factor 0.6

        Arista a = arista("residential", 300.0); // 300 m, vel defecto 30 km/h
        NodoInfo nodoSinControl = nodo(null, 0.0);

        double t1  = mc1.tiempoAristaS(a, nodoSinControl, 45.0, true);
        double t06 = mc06.tiempoAristaS(a, nodoSinControl, 45.0, true);

        // Sin factor: 300 / (30/3.6) = 36 s
        assertThat(t1).isCloseTo(36.0, within(0.01));
        // Con factor 0.6: 300 / (0.6*30/3.6) = 60 s
        assertThat(t06).isCloseTo(60.0, within(0.01));
        // t06 > t1
        assertThat(t06).isGreaterThan(t1);
    }

    @Test
    void factorVelocidadEfectiva_respetaVelMaxVehiculo() {
        CostoMulticriterio mc = multicriterio(0.6, 0, 0, 0, 0);
        // Vía primary (50 km/h defecto), vehículo 45 km/h → min(50,45)=45, factor 0.6 → 27 km/h
        Arista a = arista("primary", 270.0);
        NodoInfo nd = nodo(null, 0.0);
        double t = mc.tiempoAristaS(a, nd, 45.0, true);
        // 270 / (0.6*45/3.6) = 270 / 7.5 = 36 s
        assertThat(t).isCloseTo(36.0, within(0.01));
    }

    // ---- Demoras por nodo ----

    @Test
    void demoraTrafficSignals_sumada() {
        CostoMulticriterio mc = multicriterio(1.0, 20.0, 8.0, 4.0, 3.0);
        Arista a = arista("residential", 300.0); // 36 s rodadura
        NodoInfo semaforo = nodo("traffic_signals", 0.3);

        double tConDemora = mc.tiempoAristaS(a, semaforo, 45.0, false);
        double tSinDemora = mc.tiempoAristaS(a, semaforo, 45.0, true); // es destino final

        assertThat(tConDemora).isCloseTo(36.0 + 20.0, within(0.01));
        assertThat(tSinDemora).isCloseTo(36.0, within(0.01)); // sin demora en destino final
    }

    @Test
    void demoraStop_sumada() {
        CostoMulticriterio mc = multicriterio(1.0, 20.0, 8.0, 4.0, 3.0);
        Arista a = arista("residential", 300.0);
        NodoInfo stop = nodo("stop", 0.6);
        assertThat(mc.tiempoAristaS(a, stop, 45.0, false)).isCloseTo(36.0 + 8.0, within(0.01));
    }

    @Test
    void demoraCrossing_sumada() {
        CostoMulticriterio mc = multicriterio(1.0, 20.0, 8.0, 4.0, 3.0);
        Arista a = arista("residential", 300.0);
        NodoInfo crossing = nodo("crossing", 0.0);
        assertThat(mc.tiempoAristaS(a, crossing, 45.0, false)).isCloseTo(36.0 + 4.0, within(0.01));
    }

    @Test
    void demoraSinControlGradoAlto_sumada() {
        CostoMulticriterio mc = multicriterio(1.0, 20.0, 8.0, 4.0, 3.0);
        Arista a = arista("residential", 300.0);
        // riesgo > 0 sin control → demora sinControl
        NodoInfo cruceSimple = nodo(null, 0.2);
        assertThat(mc.tiempoAristaS(a, cruceSimple, 45.0, false)).isCloseTo(36.0 + 3.0, within(0.01));
    }

    @Test
    void nodoSinControlNiRiesgo_sinDemora() {
        CostoMulticriterio mc = multicriterio(1.0, 20.0, 8.0, 4.0, 3.0);
        Arista a = arista("residential", 300.0);
        NodoInfo extremo = nodo(null, 0.0); // riesgo=0 → sin demora
        assertThat(mc.tiempoAristaS(a, extremo, 45.0, false)).isCloseTo(36.0, within(0.01));
    }
}
