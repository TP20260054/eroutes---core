package tp20260054.eroutes.eventos;

/** El usuario ya tiene el máximo de eventos activos permitido (409). */
public class LimiteEventosActivosException extends RuntimeException {
    public LimiteEventosActivosException(String message) {
        super(message);
    }
}
