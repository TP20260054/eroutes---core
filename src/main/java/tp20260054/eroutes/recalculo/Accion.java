package tp20260054.eroutes.recalculo;

/**
 * Acción que puede tomar la política de recálculo (OB2 3.5).
 *
 * <p>El código numérico coincide con el SMALLINT almacenado en {@code recalculos.accion}.
 */
public enum Accion {
    MANTENER(0),
    RECALCULAR_VIGENTE(1),
    RECALCULAR_SEGURA(2);

    private final int codigo;

    Accion(int codigo) { this.codigo = codigo; }

    public int codigo() { return codigo; }

    public static Accion deCodigo(int codigo) {
        return switch (codigo) {
            case 0 -> MANTENER;
            case 1 -> RECALCULAR_VIGENTE;
            case 2 -> RECALCULAR_SEGURA;
            default -> throw new IllegalArgumentException("Código de acción inválido: " + codigo);
        };
    }
}
