package tp20260054.eroutes.recalculo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * MLP (Perceptrón multicapa) cargado desde el archivo JSON del contrato
 * definido en la sección 7 del documento de tesis.
 *
 * <p>Validaciones al cargar:
 * <ul>
 *   <li>Lista de features idéntica a {@link EstadoRecalculo#NOMBRES_FEATURES}
 *   <li>Dimensiones encadenadas: salida de capa k = entrada de capa k+1
 *   <li>Exactamente 3 salidas (MANTENER, RECALCULAR_VIGENTE, RECALCULAR_SEGURA)
 *   <li>Todos los pesos y biases son finitos
 *   <li>Constantes de normalización iguales a las de {@link RecalculoProperties}
 * </ul>
 *
 * <p>Si cualquier validación falla, o si el archivo no existe, el modelo queda
 * "no disponible" ({@link #estaDisponible()} = false) con el motivo registrado
 * en {@link #motivoNoDisponible()}. El arranque <strong>no falla</strong>.
 *
 * <p>No usa ONNX ni DJL; toda la aritmética es en {@code double[]}.
 */
public final class MlpModel {

    // ---- Capas ----
    private final double[][][] pesos;  // pesos[capa][neurona_salida][neurona_entrada]
    private final double[][] biases;   // biases[capa][neurona_salida]
    private final boolean[] esRelu;    // true = ReLU, false = lineal

    // ---- Meta ----
    private final String version;

    // ---- Estado de disponibilidad ----
    private final boolean disponible;
    private final String motivoNoDisponible;

    // ---- Constructor privado (vía #cargar) ----
    private MlpModel(double[][][] pesos, double[][] biases, boolean[] esRelu,
                     String version) {
        this.pesos = pesos;
        this.biases = biases;
        this.esRelu = esRelu;
        this.version = version;
        this.disponible = true;
        this.motivoNoDisponible = null;
    }

    private MlpModel(String motivo) {
        this.pesos = null;
        this.biases = null;
        this.esRelu = null;
        this.version = "N/A";
        this.disponible = false;
        this.motivoNoDisponible = motivo;
    }

    // -------------------------------------------------------------------------
    // Fábrica
    // -------------------------------------------------------------------------

    /**
     * Carga el modelo desde la ruta dada validando contra las constantes de
     * {@link RecalculoProperties}.
     *
     * @param ruta             ruta al archivo JSON; {@code null}/vacío → no disponible
     * @param props            propiedades de recálculo para validar normalización
     * @return instancia con {@link #estaDisponible()} = true o con motivo de fallo
     */
    public static MlpModel cargar(String ruta, RecalculoProperties props) {
        if (ruta == null || ruta.isBlank()) {
            return new MlpModel("MODELO_NO_DISPONIBLE: ruta no configurada");
        }
        Path path = Path.of(ruta);
        if (!Files.exists(path)) {
            return new MlpModel("MODELO_NO_DISPONIBLE: archivo no encontrado en " + ruta);
        }
        try {
            byte[] bytes = Files.readAllBytes(path);
            return parsear(bytes, props);
        } catch (IOException e) {
            return new MlpModel("MODELO_NO_DISPONIBLE: error al leer el archivo – " + e.getMessage());
        }
    }

    /** Variante que acepta el contenido JSON directamente (para tests). */
    public static MlpModel cargarDesdeBytes(byte[] jsonBytes, RecalculoProperties props) {
        return parsear(jsonBytes, props);
    }

    // -------------------------------------------------------------------------

    private static MlpModel parsear(byte[] bytes, RecalculoProperties props) {
        try {
            ObjectMapper om = new ObjectMapper();
            JsonNode root = om.readTree(bytes);

            // ---- features ----
            JsonNode featNode = root.get("features");
            if (featNode == null || !featNode.isArray()) {
                return new MlpModel("MODELO_INVALIDO: falta el campo 'features'");
            }
            List<String> features = new ArrayList<>();
            featNode.forEach(n -> features.add(n.asText()));
            if (!features.equals(EstadoRecalculo.NOMBRES_FEATURES)) {
                return new MlpModel(
                        "MODELO_INVALIDO: lista de features no coincide con NOMBRES_FEATURES");
            }

            // ---- normalizacion ----
            JsonNode norm = root.get("normalizacion");
            if (norm == null) {
                return new MlpModel("MODELO_INVALIDO: falta el campo 'normalizacion'");
            }
            String errorNorm = validarNormalizacion(norm, props);
            if (errorNorm != null) return new MlpModel(errorNorm);

            // ---- capas ----
            JsonNode capasNode = root.get("capas");
            if (capasNode == null || !capasNode.isArray() || capasNode.size() == 0) {
                return new MlpModel("MODELO_INVALIDO: falta o está vacío el campo 'capas'");
            }

            int nCapas = capasNode.size();
            double[][][] pesos  = new double[nCapas][][];
            double[][]   biases = new double[nCapas][];
            boolean[]    esRelu = new boolean[nCapas];
            int entradaEsperada = EstadoRecalculo.NOMBRES_FEATURES.size(); // 14

            for (int c = 0; c < nCapas; c++) {
                JsonNode capa = capasNode.get(c);

                JsonNode wNode = capa.get("w");
                JsonNode bNode = capa.get("b");
                String activacion = capa.has("activacion") ? capa.get("activacion").asText() : "lineal";

                if (wNode == null || !wNode.isArray() || bNode == null || !bNode.isArray()) {
                    return new MlpModel("MODELO_INVALIDO: capa " + c + " sin w o b");
                }

                int salidas  = wNode.size();
                int entradas = salidas > 0 ? wNode.get(0).size() : 0;

                if (entradas != entradaEsperada) {
                    return new MlpModel(
                            "MODELO_INVALIDO: capa " + c + " espera " + entradaEsperada
                            + " entradas pero tiene " + entradas);
                }
                if (bNode.size() != salidas) {
                    return new MlpModel(
                            "MODELO_INVALIDO: capa " + c + " – tamaño de b no coincide con w");
                }

                pesos[c]  = new double[salidas][entradas];
                biases[c] = new double[salidas];
                esRelu[c] = "relu".equalsIgnoreCase(activacion);

                for (int i = 0; i < salidas; i++) {
                    biases[c][i] = bNode.get(i).asDouble();
                    if (!Double.isFinite(biases[c][i])) {
                        return new MlpModel("MODELO_INVALIDO: bias no finito en capa " + c);
                    }
                    for (int j = 0; j < entradas; j++) {
                        pesos[c][i][j] = wNode.get(i).get(j).asDouble();
                        if (!Double.isFinite(pesos[c][i][j])) {
                            return new MlpModel("MODELO_INVALIDO: peso no finito en capa " + c);
                        }
                    }
                }
                entradaEsperada = salidas; // la siguiente capa espera esta cantidad
            }

            // ---- 3 salidas ----
            if (pesos[nCapas - 1].length != 3) {
                return new MlpModel(
                        "MODELO_INVALIDO: la última capa debe tener 3 salidas, tiene "
                        + pesos[nCapas - 1].length);
            }

            String version = root.has("version") ? root.get("version").asText() : "desconocida";
            return new MlpModel(pesos, biases, esRelu, version);

        } catch (Exception e) {
            return new MlpModel("MODELO_INVALIDO: error al parsear – " + e.getMessage());
        }
    }

    private static String validarNormalizacion(JsonNode norm, RecalculoProperties props) {
        double distMax     = norm.path("dist_evento_max_m").asDouble(Double.NaN);
        double longMax     = norm.path("long_restante_max_m").asDouble(Double.NaN);
        double maxNorm     = norm.path("max_recalculos_norm").asDouble(Double.NaN);
        double ventana     = norm.path("ventana_recalculos_s").asDouble(Double.NaN);

        if (!eq(distMax, props.distEventoMaxM())) {
            return "MODELO_INVALIDO: dist_evento_max_m=" + distMax
                    + " ≠ configurado " + props.distEventoMaxM();
        }
        if (!eq(longMax, props.longRestanteMaxM())) {
            return "MODELO_INVALIDO: long_restante_max_m=" + longMax
                    + " ≠ configurado " + props.longRestanteMaxM();
        }
        if (!eq(maxNorm, props.maxRecalculosNorm())) {
            return "MODELO_INVALIDO: max_recalculos_norm=" + maxNorm
                    + " ≠ configurado " + props.maxRecalculosNorm();
        }
        if (!eq(ventana, props.ventanaRecalculosS())) {
            return "MODELO_INVALIDO: ventana_recalculos_s=" + ventana
                    + " ≠ configurado " + props.ventanaRecalculosS();
        }
        return null;
    }

    private static boolean eq(double a, double b) {
        return Double.isFinite(a) && Math.abs(a - b) < 1e-9;
    }

    // -------------------------------------------------------------------------
    // Pasada hacia adelante
    // -------------------------------------------------------------------------

    /**
     * Calcula los valores Q para las 3 acciones dados los 14 valores del vector.
     *
     * @throws IllegalStateException si el modelo no está disponible
     */
    public double[] inferir(double[] entrada) {
        if (!disponible) {
            throw new IllegalStateException("El modelo no está disponible: " + motivoNoDisponible);
        }
        double[] activacion = entrada.clone();
        for (int c = 0; c < pesos.length; c++) {
            int salidas  = pesos[c].length;
            int entradas = pesos[c][0].length;
            double[] siguiente = new double[salidas];
            for (int i = 0; i < salidas; i++) {
                double suma = biases[c][i];
                for (int j = 0; j < entradas; j++) {
                    suma += pesos[c][i][j] * activacion[j];
                }
                siguiente[i] = esRelu[c] ? Math.max(0.0, suma) : suma;
            }
            activacion = siguiente;
        }
        return activacion; // [Q(MANTENER), Q(RECALCULAR_VIGENTE), Q(RECALCULAR_SEGURA)]
    }

    // -------------------------------------------------------------------------
    // API pública
    // -------------------------------------------------------------------------

    public boolean estaDisponible()       { return disponible; }
    public String motivoNoDisponible()    { return motivoNoDisponible; }
    public String version()               { return version; }
}
