package tp20260054.eroutes.usuarios;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class CredencialesInvalidasException extends ResponseStatusException {
    public CredencialesInvalidasException(String msg) {
        super(HttpStatus.UNAUTHORIZED, msg);
    }
}
