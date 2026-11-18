package tp20260054.eroutes.recalculo;

import java.util.List;

import tp20260054.eroutes.routing.graph.Arista;

/**
 * Detecta desvío de la ruta activa.
 *
 * <p>Una muestra de posición se considera desviada si su distancia a la polilínea de
 * la <em>ruta restante</em> (desde {@code indiceProgreso} hacia adelante) supera
 * {@code umbralM}. El disparador {@code REROUTE} se activa solo cuando
 * {@code muestrasConsecutivasRequeridas} muestras desviadas consecutivas se acumulan.
 *
 * <p>El avance es <em>monótono</em>: una vez que se acepta un segmento de la ruta
 * como "más cercano", el índice de progreso nunca retrocede. Esto evita que la
 * posición "salte" hacia atrás a un tramo lejano con la misma calle.
 *
 * <p>Puro y sin dependencias de Spring; testeable directamente.
 */
public final class DetectorDesvio {

    private final double umbralM;
    private final int muestrasRequeridas;

    public DetectorDesvio(double umbralM, int muestrasRequeridas) {
        this.umbralM = umbralM;
        this.muestrasRequeridas = muestrasRequeridas;
    }

    // -------------------------------------------------------------------------

    /**
     * Procesa una muestra de posición y actualiza el estado mutable de la sesión.
     *
     * @param estado  estado mutable de la sesión (se modifica in-place)
     * @param lat     latitud de la muestra
     * @param lng     longitud de la muestra
     * @return {@code true} si se acaba de alcanzar el umbral de desvío
     */
    public boolean procesar(EstadoDesvio estado, double lat, double lng) {
        List<Arista> aristas = estado.aristas();
        if (aristas == null || aristas.isEmpty()) return false;

        double[][] pts = GeoRuta.polilínea(aristas);
        if (pts.length < 2) return false;

        GeoRuta.ProyeccionRuta proyeccion =
                GeoRuta.proyectar(lat, lng, pts, estado.indicePunto());

        // Avance monótono: actualizar el índice de punto si mejoramos
        if (proyeccion.indiceSeg() >= estado.indicePunto()) {
            estado.setIndicePunto(proyeccion.indiceSeg());
        }

        // Mapear índice de punto de polilínea → índice de arista
        estado.setIndiceArista(puntoAArista(aristas, estado.indicePunto()));

        boolean desviado = proyeccion.distanciaM() > umbralM;
        if (desviado) {
            estado.incrementarMuestrasDesviadas();
        } else {
            estado.resetMuestrasDesviadas();
        }

        return estado.muestrasDesviadas() >= muestrasRequeridas;
    }

    /**
     * Reinicia el contador de muestras desviadas (llamado tras un recálculo exitoso
     * o al confirmar que la posición volvió a la ruta).
     */
    public void reiniciar(EstadoDesvio estado) {
        estado.resetMuestrasDesviadas();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Convierte un índice de segmento de polilínea (par de puntos consecutivos)
     * al índice de la arista que lo contiene. Cada arista de {@code k} puntos
     * aporta {@code k-1} segmentos a la polilínea global.
     */
    static int puntoAArista(List<Arista> aristas, int indiceSeg) {
        int acum = 0;
        for (int i = 0; i < aristas.size(); i++) {
            int segmentos = aristas.get(i).geom().length - 1; // segmentos de esta arista
            acum += segmentos;
            if (indiceSeg < acum) return i;
        }
        return aristas.size() - 1;
    }

    // -------------------------------------------------------------------------
    // Estado mutable de la sesión (encapsulado para que DetectorDesvio sea puro)
    // -------------------------------------------------------------------------

    /**
     * Estado de progreso y desvío de una sesión. Creado por {@link SesionNavegacion}
     * y pasado por referencia a {@link DetectorDesvio#procesar}.
     */
    public static final class EstadoDesvio {

        private final List<Arista> aristas;
        /** Índice en la polilínea global (puntos, no aristas). */
        private int indicePunto;
        /** Índice de la arista activa (derivado de indicePunto). */
        private int indiceArista;
        private int muestrasDesviadas;

        public EstadoDesvio(List<Arista> aristas) {
            this.aristas = aristas;
            this.indicePunto = 0;
            this.indiceArista = 0;
            this.muestrasDesviadas = 0;
        }

        public List<Arista> aristas()       { return aristas; }
        public int indicePunto()            { return indicePunto; }
        public int indiceArista()           { return indiceArista; }
        public int muestrasDesviadas()      { return muestrasDesviadas; }

        void setIndicePunto(int v)          { this.indicePunto = v; }
        void setIndiceArista(int v)         { this.indiceArista = v; }
        void incrementarMuestrasDesviadas() { this.muestrasDesviadas++; }
        void resetMuestrasDesviadas()       { this.muestrasDesviadas = 0; }
    }
}
