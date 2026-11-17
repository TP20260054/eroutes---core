package tp20260054.eroutes.recalculo;

/**
 * Fuente de tiempo inyectable para medir la duración de la inferencia.
 * Permite sustituir el reloj real por uno controlado en tests.
 */
@FunctionalInterface
public interface FuenteTiempo {
    long ahoraMs();

    static FuenteTiempo sistematico() {
        return System::currentTimeMillis;
    }
}
